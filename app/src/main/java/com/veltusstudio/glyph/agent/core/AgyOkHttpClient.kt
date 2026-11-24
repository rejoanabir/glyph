package com.veltusstudio.glyph.agent.core

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * High-performance OkHttpClient singleton for Antigravity Wire/gRPC streaming.
 * Uses HTTP/2 prior knowledge without TLS for low-latency local IPC on Android.
 */
object AgyOkHttpClient {

    val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 256
        }
        val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read timeout for trajectory streams
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val csrfManager = AgyCsrfManager.instance
                val token = csrfManager.token

                val requestBuilder = chain.request().newBuilder()
                    .header("User-Agent", "Glyph-Agent-Android")
                    .header("X-Grpc-Web", "1")

                if (token.isNotBlank()) {
                    requestBuilder.header(csrfManager.currentFramedHeader, token)
                    requestBuilder.header("x-codeium-csrf-token", token)
                    requestBuilder.header("Authorization", "Bearer $token")
                }

                val request = requestBuilder.build()
                val response = chain.proceed(request)

                // Detect CSRF rejection (HTTP 401/403 or gRPC 16 UNAUTHENTICATED)
                if (response.code == 401 || response.code == 403 || response.header("grpc-status") == "16") {
                    csrfManager.notifyCsrfExpired(chain.request().url.encodedPath, token)
                }

                response
            }
            .build()
    }
}

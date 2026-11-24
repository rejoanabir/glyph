package com.veltusstudio.glyph.agent.core

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

/**
 * Dedicated OkHttpClient providers:
 * 1. bridgeClient: HTTP/1.1 for Go IDE bridge (:1236) REST calls.
 * 2. grpcClient: HTTP/2 Prior Knowledge for AGY Hub (:1237) Wire gRPC streaming.
 */
object AgyOkHttpClient {

    /**
     * HTTP/1.1 client for Go IDE Bridge supervisor (:1236).
     */
    val bridgeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val csrfManager = AgyCsrfManager.instance
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Glyph-Agent-Android")
                    .header(csrfManager.currentFramedHeader, "true")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    /**
     * HTTP/2 Prior Knowledge client for AGY Hub RPC (:1237).
     */
    val grpcClient: OkHttpClient by lazy {
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
                var csrf = csrfManager.hubCsrfToken
                if (csrf.isBlank()) {
                    csrf = csrfManager.refreshFromBridge()
                }

                val framedHeader = csrfManager.currentFramedHeader
                val requestBuilder = chain.request().newBuilder()
                    .header("User-Agent", "Glyph-Agent-Android")
                    .header("X-Grpc-Web", "1")
                    .header(framedHeader, csrf.ifBlank { "true" })
                    .header("x-codeium-csrf-token", csrf)
                    .header("Authorization", "Bearer $csrf")

                val request = requestBuilder.build()
                val response = chain.proceed(request)

                // Detect CSRF rejection (HTTP 401/403 or gRPC 16 UNAUTHENTICATED)
                if (response.code == 401 || response.code == 403 || response.header("grpc-status") == "16") {
                    csrfManager.notifyCsrfExpired(chain.request().url.encodedPath, csrf)
                }

                response
            }
            .build()
    }
}

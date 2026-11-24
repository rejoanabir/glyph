package com.veltusstudio.glyph.agent.core

import android.util.Log
import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcException
import com.squareup.wire.GrpcStatus
import com.squareup.wire.GrpcStreamingCall
import exa.language_server_pb.GrpcLanguageServerServiceClient
import exa.language_server_pb.LanguageServerServiceClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException

/**
 * Shared typed entrypoint for Antigravity LanguageServerService gRPC APIs.
 * Connects to port 1237 using HTTP/2 Prior Knowledge OkHttpClient.
 */
object AgyLanguageServiceHolder {
    const val DEFAULT_HUB_URL = "http://127.0.0.1:1237"

    val service: LanguageServerServiceClient by lazy {
        GrpcLanguageServerServiceClient(
            GrpcClient.Builder()
                .client(AgyOkHttpClient.grpcClient)
                .baseUrl(DEFAULT_HUB_URL)
                .build()
        )
    }
}

/**
 * Domain error model for AGY Language Server gRPC calls.
 */
sealed class AgyRpcError(override val message: String, override val cause: Throwable? = null) : Exception(message, cause) {
    data class DaemonOffline(
        override val message: String = "Antigravity Hub is offline or unreachable.",
        override val cause: Throwable? = null
    ) : AgyRpcError(message, cause)

    data class Unauthenticated(
        override val message: String = "Session unauthenticated or CSRF validation failed."
    ) : AgyRpcError(message)

    data class ServerError(
        val status: GrpcStatus,
        override val message: String
    ) : AgyRpcError(message)

    data class Unknown(
        override val message: String,
        override val cause: Throwable? = null
    ) : AgyRpcError(message, cause)
}

suspend fun <Req : Any, Resp : Any> GrpcCall<Req, Resp>.executeSafely(
    request: Req
): Result<Resp> {
    val tokenAtStart = AgyCsrfManager.instance.hubCsrfToken
    return try {
        val res = execute(request)
        Result.success(res)
    } catch (e: CancellationException) {
        throw e
    } catch (e: GrpcException) {
        Log.e("AgyRpc", "GrpcException: status=${e.grpcStatus}, msg=${e.grpcMessage}", e)
        val isCsrfError = e.grpcStatus == GrpcStatus.UNAUTHENTICATED || e.grpcMessage?.contains("CSRF", ignoreCase = true) == true
        if (isCsrfError) {
            AgyCsrfManager.instance.notifyCsrfExpired("executeSafely", usedToken = tokenAtStart)
        }
        val err = when {
            isCsrfError || e.grpcStatus == GrpcStatus.UNAUTHENTICATED -> AgyRpcError.Unauthenticated(e.grpcMessage ?: "CSRF authentication failed")
            e.grpcStatus == GrpcStatus.UNAVAILABLE -> AgyRpcError.DaemonOffline("AGY Hub is offline or unreachable", e)
            else -> AgyRpcError.ServerError(e.grpcStatus, e.grpcMessage ?: e.message ?: "gRPC error")
        }
        Result.failure(err)
    } catch (e: IOException) {
        Log.e("AgyRpc", "IOException: ${e.message}", e)
        Result.failure(AgyRpcError.DaemonOffline("Connection failed: ${e.message}", e))
    } catch (e: Throwable) {
        Log.e("AgyRpc", "Throwable: ${e.message}", e)
        Result.failure(AgyRpcError.Unknown(e.message ?: "Unexpected error", e))
    }
}

fun <Req : Any, Resp : Any> GrpcStreamingCall<Req, Resp>.asFlowSafely(
    request: Req
): Flow<Resp> = callbackFlow {
    val tokenAtStart = AgyCsrfManager.instance.hubCsrfToken
    val (requestChannel, responseChannel) = executeIn(this)
    try {
        requestChannel.send(request)
        requestChannel.close()

        for (message in responseChannel) {
            send(message)
        }
        channel.close()
    } catch (e: CancellationException) {
        throw e
    } catch (e: GrpcException) {
        Log.e("AgyRpcStream", "Stream GrpcException: status=${e.grpcStatus}, msg=${e.grpcMessage}", e)
        val isCsrfError = e.grpcStatus == GrpcStatus.UNAUTHENTICATED || e.grpcMessage?.contains("CSRF", ignoreCase = true) == true
        if (isCsrfError) {
            AgyCsrfManager.instance.notifyCsrfExpired("asFlowSafely", usedToken = tokenAtStart)
        }
        val err = when (e.grpcStatus) {
            GrpcStatus.UNAUTHENTICATED -> AgyRpcError.Unauthenticated(e.grpcMessage ?: "Unauthenticated")
            GrpcStatus.UNAVAILABLE -> AgyRpcError.DaemonOffline("AGY Hub offline", e)
            else -> AgyRpcError.ServerError(e.grpcStatus, e.grpcMessage ?: e.message ?: "Stream error")
        }
        channel.close(err)
    } catch (e: IOException) {
        channel.close(AgyRpcError.DaemonOffline("Stream connection broken: ${e.message}", e))
    } catch (e: Throwable) {
        channel.close(AgyRpcError.Unknown(e.message ?: "Stream error", e))
    } finally {
        responseChannel.cancel()
        requestChannel.close()
    }
    awaitClose {
        cancel()
    }
}.flowOn(Dispatchers.IO)

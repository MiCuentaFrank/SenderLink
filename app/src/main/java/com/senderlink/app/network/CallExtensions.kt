package com.senderlink.app.network

import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Call<T>.await(): T {
    return suspendCancellableCoroutine { cont ->
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    cont.resume(body)
                } else {
                    cont.resumeWithException(
                        RuntimeException("HTTP ${response.code()} ${response.message()}")
                    )
                }
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                if (cont.isCancelled) return
                cont.resumeWithException(t)
            }
        })

        cont.invokeOnCancellation { try { cancel() } catch (_: Throwable) {} }
    }
}

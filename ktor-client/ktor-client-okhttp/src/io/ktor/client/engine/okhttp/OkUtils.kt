package io.ktor.client.engine.okhttp

import kotlinx.atomicfu.*
import kotlinx.coroutines.experimental.*
import okhttp3.*
import java.io.*

internal suspend fun OkHttpClient.execute(request: Request): Response = suspendCancellableCoroutine {
    val call = newCall(request)
    val callback = object : Callback {
        val pending = atomic(true)

        fun switchUp() = pending.compareAndSet(false, true)

        override fun onFailure(call: Call, cause: IOException) {
            if (switchUp()) {
                it.resumeWithException(cause)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            if (switchUp()) {
                it.resume(response)
            }
        }
    }

    call.enqueue(callback)

    it.invokeOnCancellation { _ ->
        if (callback.switchUp()) {
            call.cancel()
        }
    }
}

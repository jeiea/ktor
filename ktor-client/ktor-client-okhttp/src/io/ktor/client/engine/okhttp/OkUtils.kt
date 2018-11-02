package io.ktor.client.engine.okhttp

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import okhttp3.*
import java.io.*
import kotlin.coroutines.*

internal suspend fun OkHttpClient.execute(request: Request): Response = suspendCancellableCoroutine {
    val call = newCall(request)
    val callback = object : Callback {
        val resumed = atomic(false)

        fun switchOn() = resumed.compareAndSet(false, true)

        override fun onFailure(call: Call, cause: IOException) {
            if (switchOn()) {
                it.resumeWithException(cause)
            }
        }

        override fun onResponse(call: Call, response: Response) {
            if (switchOn()) {
                it.resume(response)
            }
        }
    }

    call.enqueue(callback)

    it.invokeOnCancellation { ex ->
        if (callback.switchOn()) {
            call.cancel()
        }
    }
}

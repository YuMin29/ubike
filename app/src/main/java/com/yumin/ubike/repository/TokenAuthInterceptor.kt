package com.yumin.ubike.repository

import android.util.Log
import com.yumin.ubike.SessionManager
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class TokenAuthInterceptor(
    private val sessionManager: SessionManager,
) : Interceptor {
    val TAG = "[TokenAuthInerecptor]"

    override fun intercept(chain: Interceptor.Chain): Response {
//        Log.d(TAG, "[Intercept] enter")
        val request = chain.request()

        val accessToken = sessionManager.fetchAuthToken()

        var response = Response.Builder().message("init").protocol(Protocol.HTTP_1_1)
            .body("init".toResponseBody(null)).request(request).code(999).build()

        try {
            response = chain.proceed(newRequestWithAccessToken(accessToken, request))
        } catch (exception: Exception) {
            Log.d(TAG, "[Intercept] got exception => ${exception.printStackTrace()}")
        }

        return response
    }

    private fun newRequestWithAccessToken(accessToken: String?, request: Request): Request =
        request.newBuilder()
            .header("authorization", "$accessToken")
            .build()
}
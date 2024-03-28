package com.yumin.ubike.repository

import android.content.Context
import android.util.Log
import com.yumin.ubike.NetworkChecker
import com.yumin.ubike.SessionManager
import dagger.Lazy
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.net.HttpURLConnection
import javax.inject.Inject

class TokenAuthInterceptor (private val sessionManager: SessionManager, private val repository: Lazy<UbikeRepository>, private val context: Context,) : Interceptor {
    val TAG = "[TokenAuthInerecptor]"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var accessToken = sessionManager.fetchAuthToken()
        var response = chain.proceed(newRequestWithAccessToken(accessToken,request))

        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            Log.d(TAG,"HTTP_UNAUTHORIZED")
            // update token

            runBlocking() {
                val deferred = async {
                    getToken()
                }
                val newToken = deferred.await()
                val token = sessionManager.fetchAuthToken()
                newToken?.let {
                    if (token != it){
                        sessionManager.saveAuthToken(String.format("Bearer %s", newToken))
                        accessToken = it
                    }
                }
                response.close()
                response = chain.proceed(newRequestWithAccessToken(accessToken, request))
            }
        }
        return response
    }

    private fun newRequestWithAccessToken(accessToken: String?, request: Request): Request =
        request.newBuilder()
            .header("authorization", "$accessToken")
            .build()

    private suspend fun getToken() : String? {
        try {
            if (NetworkChecker.checkConnectivity(context)) {
                val response = repository.get().getToken()
                if (response.isSuccessful) {
                    response.body()?.let { result ->
                        var bodyContent = result.string()
                        var jsonObject = JSONObject(bodyContent)
                        Log.d(TAG, "[getToken] = "+jsonObject.get("access_token"))
                        val token = jsonObject.get("access_token").toString()
                        return token
                    }
                } else {
                    Log.d(TAG, "[getToken] response error = "+response.message())
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "[getToken] response throwable = "+t.message)
        }
        return null
    }
}
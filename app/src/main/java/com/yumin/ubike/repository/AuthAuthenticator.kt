package com.yumin.ubike.repository

import android.content.Context
import android.util.Log
import com.yumin.ubike.NetworkChecker
import com.yumin.ubike.SessionManager
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject

class AuthAuthenticator(
    private val sessionManager: SessionManager,
    private val repository: Lazy<UbikeRepository>,
    private val context: Context
) : Authenticator {
    val TAG = "[AuthAuthenticator]"

    override fun authenticate(route: Route?, response: Response): Request? {
        return runBlocking {
            val newAccessToken = getToken()

            newAccessToken?.let {
                response.request.newBuilder()
                    .header("authorization", it)
                    .build()
            }
        }
    }

    private suspend fun getToken(): String? {
        Log.d(TAG, "[getToken] enter")
        try {
            if (NetworkChecker.checkConnectivity(context)) {
                val response = repository.get().getToken()
                if (response.isSuccessful) {
                    response.body()?.let { result ->
                        var bodyContent = result.string()
                        var jsonObject = JSONObject(bodyContent)
                        Log.d(TAG, "[getToken] = " + jsonObject.get("access_token"))
                        val rawToken = jsonObject.get("access_token").toString()
                        sessionManager.saveAuthToken(String.format("Bearer %s", rawToken))
                        return String.format("Bearer %s", rawToken)
                    }
                } else {
                    Log.d(TAG, "[getToken] response error = " + response.body())
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "[getToken] response throwable = " + t.message)
        }
        return null
    }
}
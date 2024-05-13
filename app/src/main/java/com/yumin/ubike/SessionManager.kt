package com.yumin.ubike

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject

class SessionManager(context: Context) {
    private val TAG = "[SessionManager]"
    private var sharedPreferences: SharedPreferences =
        context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE)

    companion object {
        private const val USER_TOKEN = "user_token"
        private const val EXPIRE_IN = "expire_in"
    }

    /**
     * Function to save auth token
     */
    fun saveAuthToken(token: String) {
        sharedPreferences.edit().apply {
            putString(USER_TOKEN, token)
            apply()
        }
    }

    /**
     * Function to fetch auth token
     */
    fun fetchAuthToken(): String? {
        return sharedPreferences.getString(USER_TOKEN, null)
    }
}
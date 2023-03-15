package com.yumin.ubike

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class FavoriteManager(val context: Context) {
    private var sharedPreferences: SharedPreferences =
        context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE)

    public fun storeFavoriteUid(){

    }

    public fun getFavoriteUid(){

    }
}
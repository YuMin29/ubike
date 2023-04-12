package com.yumin.ubike

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SessionManager(context: Context) {
    private var sharedPreferences: SharedPreferences =
        context.getSharedPreferences(context.getString(R.string.app_name), Context.MODE_PRIVATE)
    private var favoriteList = ArrayList<String>()

    init {
        Log.d(TAG,"[init]")
        favoriteList = fetchFavoriteList()
    }

    companion object{
        private const val TAG = "[SessionManager]"
        private const val USER_TOKEN = "user_token"
        private const val FAVORITE_LIST = "favorite_list"
    }

    /**
     * Function to save auth token
     */
    fun saveAuthToken(token: String) {
        val editor = sharedPreferences.edit()
        editor.putString(USER_TOKEN, token)
        editor.apply()
    }

    /**
     * Function to fetch auth token
     */
    fun fetchAuthToken(): String? {
        return sharedPreferences.getString(USER_TOKEN, null)
    }

    fun addToFavoriteList(stationUid: String) {
        Log.d(TAG, "[addToFavoriteList] stationUid = $stationUid")
        if (!favoriteList.contains(stationUid))
            favoriteList.add(stationUid)
        Log.d(TAG, "[addToFavoriteList] favoriteList = ${favoriteList?.size}")
        saveFavoriteList()
    }

    fun removeFromFavoriteList(stationUid: String){
        Log.d(TAG, "[removeFromFavoriteList] stationUid = $stationUid")
        if (favoriteList.contains(stationUid)) {
            favoriteList.remove(stationUid)
            Log.d(TAG, "[removeFromFavoriteList] favoriteList = ${favoriteList?.size}")
        }
        saveFavoriteList()
    }

    private fun saveFavoriteList(){
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(favoriteList)
        Log.d(TAG, "[saveFavoriteList] json = $json")
        editor.putString(FAVORITE_LIST,json)
        editor.apply()
    }

    fun fetchFavoriteList(): ArrayList<String> {
        val data = sharedPreferences.getString(FAVORITE_LIST,null)
        val gson = Gson()
        val type = object : TypeToken<ArrayList<String>>(){}.type

        if (data != null) {
            favoriteList = gson.fromJson(data,type)
            Log.d(TAG, "[fetchFavoriteList] favoriteList = ${favoriteList?.size}")
        }
        if (favoriteList == null)
            favoriteList = ArrayList<String>()

        return favoriteList
    }
}
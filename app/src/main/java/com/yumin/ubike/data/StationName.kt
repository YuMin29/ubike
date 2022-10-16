package com.yumin.ubike.data


import com.google.gson.annotations.SerializedName

data class StationName(
    @SerializedName("En")
    val en: String,
    @SerializedName("Zh_tw")
    val zhTw: String
)
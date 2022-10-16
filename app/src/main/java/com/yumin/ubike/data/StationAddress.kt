package com.yumin.ubike.data


import com.google.gson.annotations.SerializedName

data class StationAddress(
    @SerializedName("En")
    val en: String,
    @SerializedName("Zh_tw")
    val zhTw: String
)
package com.yumin.ubike

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.LayoutStationItemBinding
import java.text.SimpleDateFormat
import java.util.*

class StationListAdapter(
    private var stationList: MutableList<StationInfoItem>,
    private var availabilityList: MutableList<AvailabilityInfoItem>,
    private val sessionManager: SessionManager
) : RecyclerView.Adapter<BaseViewHolder>() {

    private val TAG = "[StationListAdapter]"
    private lateinit var context: Context

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val itemLayoutBinding = LayoutStationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        context = parent.context
        return ItemViewHolder(itemLayoutBinding)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.onBind(position)

        holder.itemView.apply {
            val availabilityInfoItem = availabilityList.find { it.StationUID == stationList[position].stationUID }
            availabilityInfoItem?.let {
                setOnClickListener {
                    onItemClickListener?.let {
                        it(holder.itemView, stationList[position], availabilityInfoItem)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return stationList.size
    }

    fun updateStationList(value: MutableList<StationInfoItem>) {
        Log.d(TAG, "[updateStationList] value size = " + value.size)
        stationList = value
        notifyDataSetChanged()
    }

    fun updateAvailabilityList(value: MutableList<AvailabilityInfoItem>) {
        Log.d(TAG, "[updateAvailabilityList] value size = " + value.size)
        availabilityList = value
        notifyDataSetChanged()
    }

    private var availableInfoString: (rent: Int, returnN: Int) -> String? = { rentN, returnN ->
        rentN.toString() + "可借 | " + returnN.toString() + "可還"
    }

    private var onItemClickListener: ((View, StationInfoItem, AvailabilityInfoItem) -> Unit)? = null
    private var onShareClick: ((Intent) -> Unit)? = null
    private var onNavigationClick: ((Intent) -> Unit)? = null
    private var onFavoriteClick: ((String, Boolean) -> Unit)? = null

    fun setOnItemClickListener(listener: (View, StationInfoItem, AvailabilityInfoItem) -> Unit) {
        onItemClickListener = listener
    }

    fun setOnShareClickListener(listener: (Intent) -> Unit) {
        onShareClick = listener
    }

    fun setOnNavigationClickListener(listener: (Intent) -> Unit) {
        onNavigationClick = listener
    }

    fun setOnFavoriteClickListener(listener: (String, Boolean) -> Unit) {
        onFavoriteClick = listener
    }

    private val convertTime: (time: String) -> Calendar = { time ->
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+08:00")
        val calendar = Calendar.getInstance().apply {
            setTime(simpleDateFormat.parse(time))
        }
        calendar
    }

    inner class ItemViewHolder(private val binding: LayoutStationItemBinding) : BaseViewHolder(binding.root) {
        override fun onBind(position: Int) {
            val stationInfoItem = stationList[position]
            val stationNameSplit = stationInfoItem.stationName.zhTw.split("_")
            binding.apply {
                stationName.text = stationNameSplit[1]
                stationAddress.text = stationInfoItem.stationAddress.zhTw
                type.text = stationNameSplit[0]
                stationDistance.text = getStationDistance(
                    LatLng(
                        stationInfoItem.stationPosition.positionLat,
                        stationInfoItem.stationPosition.positionLon
                    )
                )

                availabilityList.find { it.StationUID == stationInfoItem.stationUID }?.let { availabilityInfoItem ->
                    availableStatus.text = availableInfoString(availabilityInfoItem.AvailableRentBikes, availabilityInfoItem.AvailableReturnBikes)

                    var currentTimeDate = Calendar.getInstance().time
                    var diff = (currentTimeDate.time - convertTime(availabilityInfoItem.UpdateTime).time.time) / 1000
                    latestUpdateTime.text = diff.toInt().toString() + context.getString(R.string.updated_string)
                }

                share.setOnClickListener {
                    val sendIntent: Intent = Intent().apply {
                        val availableInfoString = availabilityList.find { it.StationUID == stationInfoItem.stationUID }?.let { availableInfoString(it.AvailableRentBikes, it.AvailableReturnBikes) }

                        val mapUri = "https://www.google.com/maps/dir/?api=1&destination=" + stationInfoItem.stationPosition.positionLat + "," + stationInfoItem.stationPosition.positionLon
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, stationNameSplit[1] + "有" + availableInfoString + "，地點在$mapUri")
                        type = "text/plain"
                    }
                    onShareClick?.let {
                        it(sendIntent)
                    }
                }

                navigate.setOnClickListener {
                    val gmmIntentUri =
                        Uri.parse("google.navigation:q=" + stationInfoItem.stationPosition.positionLat + "," + stationInfoItem.stationPosition.positionLon + "&mode=w")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    onNavigationClick?.let {
                        it(mapIntent)
                    }
                }

                var isFavorite = if (sessionManager.fetchFavoriteList().contains(stationInfoItem.stationUID)) {
                    // change icon
                    binding.star.setImageResource(R.drawable.ic_baseline_favorite_24)
                    true
                } else {
                    binding.star.setImageResource(R.drawable.ic_baseline_favorite_border_24)
                    false
                }

                star.setOnClickListener {
                    // add to favorite list
                    // remove from favorite list
                    if (isFavorite) {
                        isFavorite = false
                        binding.star.setImageResource(R.drawable.ic_baseline_favorite_border_24)
                        sessionManager.removeFromFavoriteList(stationInfoItem.stationUID)
                    } else {
                        isFavorite = true
                        binding.star.setImageResource(R.drawable.ic_baseline_favorite_24)
                        sessionManager.addToFavoriteList(stationInfoItem.stationUID)
                    }
                    onFavoriteClick?.let {
                        it(stationInfoItem.stationUID, isFavorite)
                    }
                }
            }
        }
    }

    fun getStationDistance(stationLatLng: LatLng): String {
        var stationLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = stationLatLng.latitude
            longitude = stationLatLng.longitude
        }
        val distance = stationLocation.distanceTo(MapFragment.currentLocation)

        return if (distance > 1000) {
            "%.2f".format(distance / 1000).toString() + "公里"
        } else
            distance.toInt().toString() + "公尺"
    }

}
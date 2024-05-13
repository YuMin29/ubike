package com.yumin.ubike

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.data.UbikeStationWithFavorite
import com.yumin.ubike.databinding.LayoutStationItemBinding
import java.text.SimpleDateFormat
import java.util.Calendar

class StationListAdapter(private val clickListener: FavoriteItemClickListener) :
    ListAdapter<UbikeStationWithFavorite, StationListAdapter.ItemViewHolder>(DiffCallback) {

    companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<UbikeStationWithFavorite>() {
            override fun areItemsTheSame(
                oldItem: UbikeStationWithFavorite,
                newItem: UbikeStationWithFavorite
            ): Boolean {
                return oldItem.item.srcUpdateTime == newItem.item.srcUpdateTime
            }

            override fun areContentsTheSame(
                oldItem: UbikeStationWithFavorite,
                newItem: UbikeStationWithFavorite
            ): Boolean {
                return oldItem == newItem
            }
        }
    }

    private lateinit var context: Context

    private var availableInfoString: (rent: Int, returnN: Int) -> String? = { rentN, returnN ->
        rentN.toString() + "可借 | " + returnN.toString() + "可還"
    }
    private val convertTime: (time: String) -> Calendar = { time ->
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+08:00")
        val calendar = Calendar.getInstance().apply {
            setTime(simpleDateFormat.parse(time))
        }
        calendar
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        context = parent.context
        val itemViewHolder = ItemViewHolder(
            LayoutStationItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
        itemViewHolder.itemView.setOnClickListener {
            clickListener.onClick(FavoriteItemClickEvent.ItemClick(getItem(itemViewHolder.adapterPosition)))
        }
        return itemViewHolder
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ItemViewHolder(private var binding: LayoutStationItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: UbikeStationWithFavorite) {
            val stationNameSplit = item.item.stationName.zhTw.split("_")
            binding.apply {
                stationName.text = item.item.stationName.zhTw
                stationName.text = stationNameSplit[1]
                stationAddress.text = item.item.stationAddress.zhTw
                type.text = stationNameSplit[0]
                type.apply {
                    text = stationNameSplit[0]

                    background = if (item.item.serviceType == 1)
                        context.getDrawable(R.drawable.ubike_type_bg)
                    else
                        context.getDrawable(R.drawable.ubike_type2_bg)
                }
                stationDistance.text = getStationDistance(
                    LatLng(
                        item.item.stationPosition.positionLat,
                        item.item.stationPosition.positionLon
                    )
                )

                availableStatus.text = availableInfoString(
                    item.item.availableRentBikes,
                    item.item.availableReturnBikes
                )

                var currentTimeDate = Calendar.getInstance().time
                var diff =
                    (currentTimeDate.time - convertTime(item.item.updateTime).time.time) / 1000
                latestUpdateTime.text =
                    diff.toInt().toString() + context.getString(R.string.updated_string)

                share.setOnClickListener {
                    val sendIntent: Intent = Intent().apply {
                        val availableInfoString = availableInfoString(
                            item.item.availableRentBikes,
                            item.item.availableReturnBikes
                        )

                        val mapUri =
                            "https://www.google.com/maps/dir/?api=1&destination=" + item.item.stationPosition.positionLat + "," + item.item.stationPosition.positionLon
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            stationNameSplit[1] + "有" + availableInfoString + "，地點在$mapUri"
                        )
                        type = "text/plain"
                    }
                    clickListener.onClick(FavoriteItemClickEvent.ShareClick(sendIntent))
                }

                navigate.setOnClickListener {
                    val gmmIntentUri =
                        Uri.parse("google.navigation:q=" + item.item.stationPosition.positionLat + "," + item.item.stationPosition.positionLon + "&mode=w")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    clickListener.onClick(FavoriteItemClickEvent.NavigationClick(mapIntent))
                }


                var isFavorite = if (item.isFavorite) {
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
                    } else {
                        isFavorite = true
                        binding.star.setImageResource(R.drawable.ic_baseline_favorite_24)
                    }
                    clickListener.onClick(
                        FavoriteItemClickEvent.FavoriteClick(
                            isFavorite,
                            item.item.stationUID
                        )
                    )
                }
            }
        }
    }
}
package com.yumin.ubike

import android.location.Location
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.LayoutStationItemBinding

class StationListAdapter(
    private val clickListener: OnItemClickListener,
//    private var list: Pair<StationInfo, AvailabilityInfo>
    private var stationList: MutableList<StationInfoItem>,
    private var availabilityList: MutableList<AvailabilityInfoItem>
) : RecyclerView.Adapter<BaseViewHolder>() {
    private val TAG = "[StationListAdapter]"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val itemLayoutBinding =
            LayoutStationItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(itemLayoutBinding, clickListener)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.onBind(position)
    }

    override fun getItemCount(): Int {
//        return list.first.size
        return stationList.size
    }

    fun updateStationList(value: MutableList<StationInfoItem>){
        Log.d(TAG,"[updateStationList] value size = "+value.size)
        stationList = value
        notifyDataSetChanged()
    }

    fun updateAvailabilityList(value: MutableList<AvailabilityInfoItem>){
        Log.d(TAG,"[updateAvailabilityList] value size = "+value.size)
        availabilityList = value
        notifyDataSetChanged()
    }

//    public fun addItems(data: Pair<StationInfo, AvailabilityInfo>) {
//        list = data
//        notifyDataSetChanged()
//    }

    inner class ItemViewHolder(
        private val binding: LayoutStationItemBinding,
        private val listener: OnItemClickListener
    ) : BaseViewHolder(binding.root), View.OnClickListener {
        override fun onBind(position: Int) {
//            val stationInfoItem = list.first[position]
            val stationInfoItem = stationList[position]
//            val availabilityInfoItem = list.second[position]
            val stationNameSplit = stationInfoItem.stationName.zhTw.split("_")
            binding.stationName.text = stationNameSplit[1]
            binding.stationAddress.text = stationInfoItem.stationAddress.zhTw
            binding.type.text = stationNameSplit[0]
            binding.stationDistance.text = getStationDistance(
                LatLng(
                    stationInfoItem.stationPosition.positionLat,
                    stationInfoItem.stationPosition.positionLon
                )
            )

            binding.availableStatus.text = getAvailableInfo(stationInfoItem.stationUID)
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            listener.onItemClick(v, stationList[position],availabilityList[adapterPosition])
        }
    }

    public fun getStationDistance(stationLatLng: LatLng): String {
        var stationLocation = Location("")
        stationLocation.latitude = stationLatLng.latitude
        stationLocation.longitude = stationLatLng.longitude
        val distance = stationLocation.distanceTo(StationListFragment.getLocation())

        return if (distance > 1000) {
            "%.2f".format(distance / 1000).toString() + "公里"
        } else
            distance.toInt().toString() + "公尺"
    }

    fun getAvailableInfo(uId: String): String {
//        list.second.forEach { availabilityInfoItem ->
//            if (availabilityInfoItem.StationUID == uId) {
//                return availabilityInfoItem.AvailableRentBikes.toString() + "可借 | " +
//                        availabilityInfoItem.AvailableReturnBikes.toString() + "可還"
//            }
//        }
        availabilityList.forEach { availabilityInfoItem ->
            if (availabilityInfoItem.StationUID == uId) {
                return availabilityInfoItem.AvailableRentBikes.toString() + "可借 | " +
                        availabilityInfoItem.AvailableReturnBikes.toString() + "可還"
            }
        }
        return ""
    }

    interface OnItemClickListener {
        fun onItemClick(
            view: View,
            item: StationInfoItem,
            availabilityInfoItem: AvailabilityInfoItem
        )
    }
}
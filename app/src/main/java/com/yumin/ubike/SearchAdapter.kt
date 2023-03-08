package com.yumin.ubike

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.CardViewItemLayoutBinding

class SearchAdapter(
    private var stationList: MutableList<StationInfoItem>,
    private var availabilityList: MutableList<AvailabilityInfoItem>
    ) : RecyclerView.Adapter<BaseViewHolder>() {
    val TAG = "[SearchAdapter]"

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        val itemLayoutBinding =
            CardViewItemLayoutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(itemLayoutBinding)
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.onBind(position)
    }

    override fun getItemCount(): Int {
        Log.d(TAG,"[getItemCount] stationList.size = "+stationList.size+" ,availabilityList.size = "+availabilityList.size)
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

    inner class ItemViewHolder(
        private val binding: CardViewItemLayoutBinding
    ) : BaseViewHolder(binding.root) {
        override fun onBind(position: Int) {
            val stationInfoItem = stationList[position]
//            val availabilityInfoItem = list.second[position]
            val stationNameSplit = stationInfoItem.stationName.zhTw.split("_")
            binding.stationName.text = stationNameSplit[1]
            binding.stationAddress.text = stationList[position].stationAddress.zhTw
            binding.type.text = stationNameSplit[0]
        }
    }
}
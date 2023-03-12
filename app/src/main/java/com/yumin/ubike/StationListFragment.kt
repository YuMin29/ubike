package com.yumin.ubike

import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentStationListBinding
import com.yumin.ubike.repository.RemoteRepository
import java.util.*
import kotlin.Comparator

class StationListFragment : Fragment(),StationListAdapter.OnItemClickListener{
    private val TAG: String = "[StationListFragment]"
    private lateinit var fragmentStationListBinding: FragmentStationListBinding
    private lateinit var stationListAdapter: StationListAdapter
    private val viewModel: MapViewModel by activityViewModels{
        val activity = requireNotNull(this.activity)
        val repository = RemoteRepository(SessionManager(activity))
        MyViewModelFactory(repository)
    }
    private lateinit var remoteRepository: RemoteRepository
    private lateinit var currentLatLng: LatLng
    private var initialDistance: Int = 2000

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentStationListBinding = FragmentStationListBinding.inflate(inflater)

        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, true)
            it.window.statusBarColor = it.getColor(R.color.pink)
        }

        val bundle = arguments
        if (bundle != null) {
            currentLatLng = LatLng(bundle.getDouble("latitude"), bundle.getDouble("longitude"))
//            initialDistance = bundle.getInt("distance")
            myCurrentLocation = bundle.get("location") as Location
            Log.d(
                TAG,
                "[extras] longitude = ${bundle.getDouble("longitude")} ,latitude = ${
                    bundle.getDouble("latitude")
                }" +
                        ",distance = $initialDistance"
            )
        } else {
            // means don't have any lating data
        }
        initView()
        observeViewModel()
        getCurrentStationInfo()

        fragmentStationListBinding.imageButton.setOnClickListener{
            // popup
            activity?.let {
                it.supportFragmentManager.popBackStack()
            }
        }

        return fragmentStationListBinding.root
    }

    private fun getCurrentStationInfo() {
        viewModel.getStationInfoNearBy(
            currentLatLng.latitude, currentLatLng.longitude, initialDistance, 0
        )
        viewModel.getAvailabilityNearBy(
            currentLatLng.latitude, currentLatLng.longitude, initialDistance, 0, false
        )
    }

    private fun initView() {
        stationListAdapter = StationListAdapter(
            this,
//            Pair(StationInfo(), AvailabilityInfo())
        mutableListOf(), mutableListOf()
        )
        fragmentStationListBinding.stationListView.adapter = stationListAdapter
    }

    /**
     * observe the data from view model
     * station info and available info by distance
     * distance => 滑到頁面底部再加大距離
     * 一剛開始加載此頁面以map 切換的經緯度+距離為主
     */
    private fun observeViewModel() {
        viewModel.stationWholeInfo.observe(viewLifecycleOwner, androidx.lifecycle.Observer { it1 ->
            Log.d(TAG, "[observeViewModel] first SIZE = " + it1.first?.size)
            Log.d(TAG, "[observeViewModel] second SIZE = " + it1.second?.size)

            if (it1.first?.size == it1.second?.size) {
                // sort list
                sortListByDistance(it1.first as ArrayList<StationInfoItem>)
//                stationListAdapter.addItems(it1 as Pair<StationInfo, AvailabilityInfo>)
                stationListAdapter.updateStationList(it1.first!!.toMutableList())
                stationListAdapter.updateAvailabilityList(it1.second!!.toMutableList())
            }
        })
    }


    private fun sortListByDistance(stationList: ArrayList<StationInfoItem>): ArrayList<StationInfoItem> {
        val comparator = Comparator<StationInfoItem?> { item1, item2 ->
            var distance1 = 0f
            if (item1 != null) {
                val location1 = Location("")
                location1.latitude = item1.stationPosition.positionLat
                location1.longitude = item1.stationPosition.positionLon
                distance1 = myCurrentLocation.distanceTo(location1)
            }

            var distance2 = 0f
            if (item2 != null) {
                val location2 = Location("")
                location2.latitude = item2.stationPosition.positionLat
                location2.longitude = item2.stationPosition.positionLon
                distance2 = myCurrentLocation.distanceTo(location2)
            }

//            Log.d(TAG, "distance1 : $distance1, distance2 : $distance2")
//            Log.d(TAG, "o1 : ${item1?.stationName}, o2 : ${item2?.stationName}")
            distance1.compareTo(distance2)
        }
        Collections.sort(stationList, comparator)
        return stationList
    }

    override fun onItemClick(
        view: View,
        item: StationInfoItem,
        availabilityInfoItem: AvailabilityInfoItem
    ) {
        if (item != null) {
            Log.d(TAG,"[onItemClick] ITEM = "+item.stationName)
            parentFragmentManager.popBackStack()
            viewModel.setSelectStationUid(item.stationUID)
        }
    }

    companion object{
        lateinit var myCurrentLocation:Location
        public fun getLocation(): Location {
            return myCurrentLocation
        }
    }
}
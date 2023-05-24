package com.yumin.ubike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentStationListBinding
import com.yumin.ubike.repository.RemoteRepository
import java.util.*
import kotlin.Comparator

class StationListFragment : Fragment(),StationListAdapter.OnClickListener{
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
    private var type: Int = 0
    private lateinit var receiver: BroadcastReceiver
    lateinit var sessionManager: SessionManager
    lateinit var currentLocation:Location

    companion object{
        private const val TAG: String = "[StationListFragment]"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentStationListBinding = FragmentStationListBinding.inflate(inflater)

        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, true)
            it.window.statusBarColor = it.getColor(R.color.pink)
            sessionManager = SessionManager(it)
        }

        val bundle = arguments
        if (bundle != null) {
            currentLatLng = LatLng(bundle.getDouble("latitude"), bundle.getDouble("longitude"))
            currentLocation = Location("")
            currentLocation.latitude = currentLatLng.latitude
            currentLocation.longitude = currentLatLng.longitude
            Log.d(TAG, "[onCreateView] currentLatLng = $currentLatLng")
            type = bundle.getInt("type")

            Log.d(
                TAG,
                "[extras] longitude = ${bundle.getDouble("longitude")} ,latitude = ${
                    bundle.getDouble("latitude")
                }" +
                        ",distance = $initialDistance"
            )
        } else {
            // means don't have any lating data
            Log.d(TAG, "[onCreateView] don't have any lating data")
        }
        getCurrentStationInfo()
        observeViewModel()

        receiver = object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                getCurrentStationInfo()
            }
        }

        context?.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_TICK))
        return fragmentStationListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView()
    }

    private fun getCurrentStationInfo() {
        viewModel.getStationInfoNearBy(
            currentLatLng.latitude, currentLatLng.longitude, initialDistance, type
        )
        viewModel.getAvailabilityNearBy(
            currentLatLng.latitude, currentLatLng.longitude, initialDistance, type, false
        )
    }

    private fun initView() {
        stationListAdapter = StationListAdapter(
            this,
            mutableListOf(), mutableListOf(), sessionManager
        )
        fragmentStationListBinding.stationListView.adapter = stationListAdapter

        fragmentStationListBinding.imageButton.setOnClickListener{
            // popup
            activity?.let {
                findNavController().popBackStack()
            }
        }
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
                distance1 = currentLocation.distanceTo(location1)
            }

            var distance2 = 0f
            if (item2 != null) {
                val location2 = Location("")
                location2.latitude = item2.stationPosition.positionLat
                location2.longitude = item2.stationPosition.positionLon
                distance2 = currentLocation.distanceTo(location2)
            }

//            Log.d(TAG, "distance1 : $distance1, distance2 : $distance2")
//            Log.d(TAG, "o1 : ${item1?.stationName}, o2 : ${item2?.stationName}")
            distance1.compareTo(distance2)
        }
        Collections.sort(stationList, comparator)
        return stationList
    }

    override fun onDestroyView() {
        super.onDestroyView()
        context?.unregisterReceiver(receiver)
    }

    override fun onItemClick(
        view: View,
        item: StationInfoItem,
        availabilityInfoItem: AvailabilityInfoItem
    ) {
        if (item != null) {
            Log.d(TAG,"[onItemClick] ITEM = "+item.stationName)
            findNavController().popBackStack()
            viewModel.setSelectStationUid(item.stationUID)
        }
    }

    override fun onShareClick(intent: Intent) {
        intent.putExtra(Intent.EXTRA_SUBJECT,getString(R.string.share_subject))
        val shareIntent = Intent.createChooser(intent, null)
        startActivity(shareIntent)
    }

    override fun onNavigationClick(intent: Intent) {
        startActivity(intent)
    }

    override fun onFavoriteClick(uId: String, add: Boolean) {

    }
}
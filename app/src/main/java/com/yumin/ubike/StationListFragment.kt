package com.yumin.ubike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
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
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentStationListBinding
import com.yumin.ubike.repository.RemoteRepository
import java.util.*

class StationListFragment : Fragment(){
    private val TAG = "[StationListFragment]"
    private lateinit var fragmentStationListBinding: FragmentStationListBinding
    private lateinit var stationListAdapter: StationListAdapter
    private val viewModel: MapViewModel by activityViewModels{
        val repository = RemoteRepository(SessionManager(requireActivity()))
        MyViewModelFactory(repository,requireActivity().application)
    }
    private lateinit var currentLatLng: LatLng
    private var initialDistance: Int = 1000
    private var type: Int = 0
    private lateinit var broadcastReceiver: BroadcastReceiver
    lateinit var sessionManager: SessionManager
    lateinit var currentLocation:Location

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentStationListBinding = FragmentStationListBinding.inflate(inflater)
        return fragmentStationListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.pink)
        sessionManager = SessionManager(requireContext())

        arguments?.let { bundle ->
            currentLatLng = LatLng(bundle.getDouble(MapFragment.KEY_LATITUDE), bundle.getDouble(MapFragment.KEY_LONGITUDE))
            currentLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = currentLatLng.latitude
                longitude = currentLatLng.longitude
            }
            Log.d(TAG, "[onCreateView] currentLatLng = $currentLatLng")
            type = bundle.getInt(MapFragment.KEY_UBIKE_TYPE)
            Log.d(TAG, "[extras] longitude = ${bundle.getDouble("longitude")} ,latitude = ${bundle.getDouble("latitude")}" + ",distance = $initialDistance")
        }

        getCurrentStationInfo()

        observeViewModel()

        setupBroadcastReceiver()

        initView()
    }

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                getCurrentStationInfo()
            }
        }

        requireContext().registerReceiver(broadcastReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
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
        stationListAdapter = StationListAdapter(mutableListOf(), mutableListOf(), sessionManager).apply {
            setOnItemClickListener { view, stationInfoItem, availabilityInfoItem ->
                stationInfoItem?.let {
                    Log.d(TAG,"[onItemClick] ITEM = "+it.stationName)
                    findNavController().popBackStack()
                    viewModel.setSelectStationUid(it.stationUID)
                }
            }

            setOnShareClickListener { intent ->
                intent.putExtra(Intent.EXTRA_SUBJECT,getString(R.string.share_subject))
                val shareIntent = Intent.createChooser(intent, null)
                startActivity(shareIntent)
            }

            setOnNavigationClickListener { intent ->
                startActivity(intent)
            }
        }
        fragmentStationListBinding.stationListView.adapter = stationListAdapter

        fragmentStationListBinding.imageButton.setOnClickListener{
            findNavController().popBackStack()
        }
    }

    /**
     * observe the data from view model
     * station info and available info by distance
     * distance => 滑到頁面底部再加大距離
     * 一剛開始加載此頁面以map 切換的經緯度+距離為主
     */
    private fun observeViewModel() {
        viewModel.stationInfo.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            Log.d(TAG,"[observeViewModel] stationInfo SIZE = "+it.size)
            SortUtils.sortListByDistance(it as ArrayList<StationInfoItem>)
            stationListAdapter.updateStationList(it.toMutableList())
        })

        viewModel.availabilityInfo.observe(viewLifecycleOwner, androidx.lifecycle.Observer {
            Log.d(TAG,"[observeViewModel] availabilityInfo SIZE = "+it.size)
            stationListAdapter.updateAvailabilityList(it.toMutableList())
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
    }
}
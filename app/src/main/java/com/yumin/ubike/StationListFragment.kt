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
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentStationListBinding
import com.yumin.ubike.repository.UbikeRepository
import java.util.*
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class StationListFragment : Fragment() {
    private val TAG = "[StationListFragment]"
    private lateinit var fragmentStationListBinding: FragmentStationListBinding
    private lateinit var stationListAdapter: StationListAdapter
    @Inject lateinit var sessionManager: SessionManager
    private val viewModel: MapViewModel by activityViewModels()
    private lateinit var currentLatLng: LatLng
    private var stationRange: Int = MapFragment.stationRange
    private var type: Int = 0
    private lateinit var broadcastReceiver: BroadcastReceiver
    lateinit var currentLocation: Location

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentStationListBinding = FragmentStationListBinding.inflate(inflater)
        return fragmentStationListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.primary_700)
        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.apply {
            isAppearanceLightStatusBars = false
        }

        arguments?.let { bundle ->
            currentLatLng = LatLng(bundle.getDouble(MapFragment.KEY_LATITUDE), bundle.getDouble(MapFragment.KEY_LONGITUDE))
            currentLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = currentLatLng.latitude
                longitude = currentLatLng.longitude
            }
            Log.d(TAG, "[onCreateView] currentLatLng = $currentLatLng")
            type = bundle.getInt(MapFragment.KEY_UBIKE_TYPE)
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
            currentLatLng.latitude, currentLatLng.longitude, stationRange, type
        )
        viewModel.getAvailabilityNearBy(
            currentLatLng.latitude, currentLatLng.longitude, stationRange, type, false
        )
    }

    private fun initView() {
        stationListAdapter = StationListAdapter(mutableListOf(), mutableListOf(),sessionManager).apply {
            setOnItemClickListener { view, stationInfoItem, availabilityInfoItem ->
                stationInfoItem?.let {
                    Log.d(TAG, "[onItemClick] ITEM = " + it.stationName)
                    viewModel.setSelectStationUid(it.stationUID)
                    findNavController().popBackStack()

                }
            }

            setOnShareClickListener { intent ->
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                val shareIntent = Intent.createChooser(intent, null)
                startActivity(shareIntent)
            }

            setOnNavigationClickListener { intent ->
                startActivity(intent)
            }
        }
        fragmentStationListBinding.stationListView.adapter = stationListAdapter

        fragmentStationListBinding.imageButton.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun observeViewModel() {
        viewModel.stationInfo.observe(viewLifecycleOwner, Observer { response ->
            when (response) {
                is Resource.Success -> {
                    response.data?.let { data ->
                        Log.d(TAG, "[observeViewModel] stationInfo SIZE = " + data.size)
                        SortUtils.sortListByDistance(data as ArrayList<StationInfoItem>)
                        stationListAdapter.updateStationList(data.toMutableList())
                    }
                }

                is Resource.Loading -> {

                }

                is Resource.Error -> {
                    response.message?.let { message ->
                        Log.e(TAG,"observe stationInfo, an error happened: $message")
                    }
                }
            }
        })

        viewModel.availabilityInfo.observe(viewLifecycleOwner, Observer { response ->
            when (response) {
                is Resource.Success -> {
                    response.data?.let { data ->
                        Log.d(TAG, "[observeViewModel] availabilityInfo SIZE = " + data.size)
                        stationListAdapter.updateAvailabilityList(data.toMutableList())
                    }
                }

                is Resource.Loading -> {

                }

                is Resource.Error -> {
                    response.message?.let { message ->
                        Log.e(TAG,"observe availabilityInfo, an error happened: $message")
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
    }
}
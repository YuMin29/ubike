package com.yumin.ubike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentFavoriteBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class FavoriteFragment : Fragment() {
    private val TAG = "[FavoriteFragment]"
    private lateinit var fragmentFavoriteBinding: FragmentFavoriteBinding
    private lateinit var stationListAdapter: StationListAdapter
    @Inject lateinit var sessionManager: SessionManager
    private val mapViewModel: MapViewModel by activityViewModels()
    private lateinit var favoriteStationList: ArrayList<String>
    private lateinit var broadcastReceiver: BroadcastReceiver
    private var allStationInfo = listOf<StationInfo>()
    private var allAvailabilityInfo = listOf<AvailabilityInfo>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentFavoriteBinding = FragmentFavoriteBinding.inflate(inflater)
        return fragmentFavoriteBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapViewModel.getAllCityAvailabilityInfo()
        mapViewModel.getAllCityStationInfo()

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.primary_700)

        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.apply {
            isAppearanceLightStatusBars = false
        }

        favoriteStationList = sessionManager.fetchFavoriteList()
        Log.d(TAG, "favoriteList = $favoriteStationList")

        setupBroadcastReceiver()
        setupRecyclerView()

        fragmentFavoriteBinding.imageButton.setOnClickListener {
            findNavController().popBackStack()
        }

        observeViewModel()
    }

    private fun setupRecyclerView() {
        stationListAdapter = StationListAdapter(mutableListOf(), mutableListOf(),sessionManager).apply {
            setOnItemClickListener { view, stationInfoItem, availabilityInfoItem ->
                stationInfoItem?.let {
                    Log.d(
                        TAG, "[onItemClick] stationName = " + it.stationName + " ,uid = " + it.stationUID +
                                "availabilityInfoItem uid = " + availabilityInfoItem.StationUID +
                                ", rent = " + availabilityInfoItem.AvailableRentBikes +
                                ", return = " + availabilityInfoItem.AvailableReturnBikes
                    )
                    findNavController().popBackStack()
                    mapViewModel.setSelectSearchStationUid(it, availabilityInfoItem)
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

            setOnFavoriteClickListener { s, b ->
                // update list
                favoriteStationList = sessionManager.fetchFavoriteList()
                Log.d(TAG, "[onFavoriteClick] favoriteList = $favoriteStationList")
                getFavoriteStationInfo()
            }
        }

        fragmentFavoriteBinding.favoriteRecyclerView.adapter = stationListAdapter
    }

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                mapViewModel.getAllCityAvailabilityInfo()
                mapViewModel.getAllCityStationInfo()
            }
        }
        requireContext().registerReceiver(broadcastReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    private fun observeViewModel() {
        mapViewModel.allCityStationInfo.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is Resource.Success -> {
                        response.data?.let { data ->
                            allStationInfo = data
                            Log.d(TAG, "[observeViewModel] getAllStationInfo SIZE = " + allStationInfo.size)
                            hideProgressBar()
                            getFavoriteStationInfo()
                        }
                    }

                    is Resource.Loading -> {
                        showProgressBar()
                    }

                    is Resource.Error -> {
                        response.message?.let { message ->
                            Log.e(TAG,"observe allCityStationInfo, an error happened: $message")
                            hideProgressBar()
                        }
                    }
                }
            }
        })

        mapViewModel.allCityAvailabilityInfo.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is Resource.Success -> {
                        response.data?.let { data ->
                            allAvailabilityInfo = data
                            Log.d(TAG, "[observeViewModel] gwtAllAvailabilityInfo SIZE = " + allAvailabilityInfo.size)
                            hideProgressBar()
                            getFavoriteStationInfo()
                        }
                    }

                    is Resource.Loading -> {
                        showProgressBar()
                    }

                    is Resource.Error -> {
                        response.message?.let { message ->
                            Log.e(TAG,"observe allCityAvailabilityInfo, an error happened: $message")
                            hideProgressBar()
                        }
                    }
                }
            }
        })
    }

    private fun hideProgressBar() {
        fragmentFavoriteBinding.progressBar.visibility = View.INVISIBLE
    }

    private fun showProgressBar() {
        fragmentFavoriteBinding.progressBar.visibility = View.VISIBLE
    }

    private fun getFavoriteStationInfo() {
        var stationInfoList = mutableListOf<StationInfoItem>()
        var availabilityInfoList = mutableListOf<AvailabilityInfoItem>()

//        allStationInfo.forEach {
//            stationInfoList.addAll(it)
//        }

        for (stationInfo in allStationInfo) {
            stationInfoList.addAll(stationInfo)
        }

        stationInfoList = stationInfoList.filter { stationInfoItem ->
            favoriteStationList.contains(stationInfoItem.stationUID)
        }.toMutableList()

//        allAvailabilityInfo.forEach {
//            availabilityInfoList.addAll(it)
//        }

        for (availabilityInfo in allAvailabilityInfo) {
            availabilityInfoList.addAll(availabilityInfo)
        }

        availabilityInfoList = availabilityInfoList.filter { availabilityInfo ->
            favoriteStationList.contains(availabilityInfo.StationUID)
        }.toMutableList()

        stationListAdapter.updateStationList(stationInfoList)
        stationListAdapter.updateAvailabilityList(availabilityInfoList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
    }
}
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
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentFavoriteBinding
import com.yumin.ubike.repository.RemoteRepository

class FavoriteFragment : Fragment() {
    private val TAG = "[FavoriteFragment]"
    private lateinit var fragmentFavoriteBinding: FragmentFavoriteBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var stationListAdapter: StationListAdapter
    private val mapViewModel: MapViewModel by activityViewModels {
        val repository = RemoteRepository(SessionManager(requireActivity()))
        MyViewModelFactory(repository, requireActivity().application)
    }
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
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.pink)

        sessionManager = SessionManager(requireContext())

        // get favorite station info
        favoriteStationList = sessionManager.fetchFavoriteList()
        Log.d(TAG, "favoriteList = $favoriteStationList")

        setupBroadcastReceiver()

        setupRecyclerView()

        fragmentFavoriteBinding.imageButton.setOnClickListener {
            findNavController().popBackStack()
        }

        // view model get all station info
        // find station info and show on the recycler view
        observeViewModel()
    }

    private fun setupRecyclerView() {
        stationListAdapter = StationListAdapter(mutableListOf(), mutableListOf(), sessionManager).apply {
            setOnItemClickListener { view, stationInfoItem, availabilityInfoItem ->
                stationInfoItem?.let {
                    Log.d(TAG, "[onItemClick] stationName = " + it.stationName + " ,uid = " + it.stationUID +
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
            event.getContentIfNotHandled()?.let {
                allStationInfo = it
                Log.d(TAG, "[observeViewModel] getAllStationInfo SIZE = " + allStationInfo.size)
                hideProgressBar()
                getFavoriteStationInfo()
            }
        })

        mapViewModel.allCityAvailabilityInfo.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let {
                allAvailabilityInfo = it
                Log.d(TAG, "[observeViewModel] gwtAllAvailabilityInfo SIZE = " + allAvailabilityInfo.size)
                hideProgressBar()
                getFavoriteStationInfo()
            }
        })
    }

    private fun hideProgressBar() {
        fragmentFavoriteBinding.progressBar.visibility = View.INVISIBLE
    }

    private fun getFavoriteStationInfo() {
        // if favorite list not empty ， find the station info & update adapter data
        var stationInfoList = mutableListOf<StationInfoItem>()
        var availabilityInfoList = mutableListOf<AvailabilityInfoItem>()

        allStationInfo.forEach {
            stationInfoList.addAll(it)
        }

        stationInfoList = stationInfoList.filter { stationInfoItem ->
            favoriteStationList.contains(stationInfoItem.stationUID)
        }.toMutableList()

        allAvailabilityInfo.forEach {
            availabilityInfoList.addAll(it)
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
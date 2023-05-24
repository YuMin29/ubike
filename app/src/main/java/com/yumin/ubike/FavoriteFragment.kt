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

class FavoriteFragment:Fragment(),StationListAdapter.OnClickListener {
    private val TAG = "[FavoriteFragment]"
    private lateinit var favoriteBinding:FragmentFavoriteBinding
    private lateinit var sessionManager : SessionManager
    private lateinit var stationListAdapter: StationListAdapter
    private val mapViewModel: MapViewModel by activityViewModels{
        val repository = RemoteRepository(SessionManager(requireActivity()))
        MyViewModelFactory(repository,requireActivity().application)
    }
    private lateinit var favoriteList : ArrayList<String>
    private lateinit var stationInfoList : MutableList<StationInfoItem>
    private lateinit var availabilityInfoList : MutableList<AvailabilityInfoItem>
    private lateinit var pairInfo : Pair<List<StationInfo>?,List<AvailabilityInfo>?>
    private lateinit var receiver: BroadcastReceiver

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        favoriteBinding = FragmentFavoriteBinding.inflate(inflater)

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.secondary_orange)
        sessionManager = SessionManager(requireContext())

        // get favorite station info
        favoriteList = sessionManager.fetchFavoriteList()
        Log.d(TAG,"favoriteList = "+favoriteList.toString())

        receiver = object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                mapViewModel.getAllCityAvailabilityInfo()
                mapViewModel.getAllCityStationInfo()
            }
        }
        requireContext().registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_TICK))
        // view model get all station info
        // find station info and show on the recycler view
        observeViewModel()

        return favoriteBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // listview
        stationListAdapter = StationListAdapter(this,mutableListOf(), mutableListOf(), sessionManager)
        favoriteBinding.favoriteRecyclerView.adapter = stationListAdapter
        favoriteBinding.imageButton.setOnClickListener{
            // popup
            findNavController().popBackStack()
        }
    }

    private fun observeViewModel(){
        mapViewModel.allInfo.observe(viewLifecycleOwner, Observer {
            if (favoriteList.isEmpty()) {
                favoriteBinding.progressBar3.visibility = View.INVISIBLE
                return@Observer
            }

            it.getContentIfNotHandled()?.apply {
                pairInfo = this
                getFavoriteStationInfo(pairInfo)
                favoriteBinding.progressBar3.visibility = View.INVISIBLE
            }
        })
    }

    private fun getFavoriteStationInfo(source : Pair<List<StationInfo>?,List<AvailabilityInfo>?>){
        // if favorite list not empty ， find the station info & update adapter data
        stationInfoList = mutableListOf<StationInfoItem>()
        availabilityInfoList = mutableListOf<AvailabilityInfoItem>()

        favoriteList.forEach {
            source.first?.apply {
                for (list in this) {
                    for (item in list) {
                        if (item.stationUID == it)
                            stationInfoList.add(item)
                    }
                }
            }

            source.second?.apply {
                for (list in this) {
                    for (item in list) {
                        if (item.StationUID == it)
                            availabilityInfoList.add(item)
                    }
                }
            }
        }
        // update
        stationListAdapter.updateStationList(stationInfoList)
        stationListAdapter.updateAvailabilityList(availabilityInfoList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(receiver)
    }

    override fun onItemClick(
        view: View,
        item: StationInfoItem,
        availabilityInfoItem: AvailabilityInfoItem
    ) {
        if (item != null) {
            Log.d(TAG,"[onItemClick] stationName = "+item.stationName+" ,uid = "+item.stationUID)
            Log.d(TAG,"[onItemClick] availabilityInfoItem uid = "+availabilityInfoItem.StationUID);
            Log.d(TAG,"[onItemClick] availabilityInfoItem rent = "+availabilityInfoItem.AvailableRentBikes+", return = "+availabilityInfoItem.AvailableReturnBikes)
            findNavController().popBackStack()
            mapViewModel.setSelectSearchStationUid(item,availabilityInfoItem)
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
        // update list
        favoriteList = sessionManager.fetchFavoriteList()
        Log.d(TAG,"[onFavoriteClick] favoriteList = "+favoriteList.toString())
        getFavoriteStationInfo(pairInfo)
    }
}
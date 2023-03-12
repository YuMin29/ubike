package com.yumin.ubike

import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentSearchBinding
import com.yumin.ubike.repository.RemoteRepository
import java.util.*
import kotlin.Comparator

class SearchFragment: Fragment(),StationListAdapter.OnItemClickListener{
    private val TAG = "[SearchFragment]"
    lateinit var fragmentSearchBinding: FragmentSearchBinding
    lateinit var stationList:MutableList<StationInfoItem>
    lateinit var availabilityInfoList:MutableList<AvailabilityInfoItem>

    private val mapViewModel: MapViewModel by activityViewModels{
        val activity = requireNotNull(this.activity)
        val repository = RemoteRepository(SessionManager(activity))
        MyViewModelFactory(repository)
    }
    private lateinit var stationListAdapter: StationListAdapter

    //搜尋站點名稱 並顯示在recycler list中....
    // 先把全台的站點都拿到 => ok
    // 一分鐘更新一次站點可借還資訊
    // 點選站點 跳轉回去地圖模式? 怎麼告訴地圖模式站點資訊在哪裡? => ok
    // 1. 只新增該站點資訊到目前地圖觀察的對象中
    // 2. 再依照目前位置去更新觀察的對象?

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentSearchBinding = FragmentSearchBinding.inflate(inflater)
        activity?.let { WindowCompat.setDecorFitsSystemWindows(it.window, true) }

        activity?.let {
//            WindowCompat.setDecorFitsSystemWindows(it.window, true)
//            it.window.statusBarColor = it.getColor(R.color.pink)
        }

        fragmentSearchBinding.imageButton.setOnClickListener{
            // popup
            activity?.let {
                it.supportFragmentManager.popBackStack()
            }
        }

        mapViewModel.getAllCityStationInfo()
        mapViewModel.getAllCityAvailabilityInfo()

        stationListAdapter = StationListAdapter(this,mutableListOf(), mutableListOf())

        fragmentSearchBinding.recyclerView.adapter = stationListAdapter
        fragmentSearchBinding.searchView2.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText)
                return true
            }
        })

        fragmentSearchBinding.searchView2.setOnQueryTextFocusChangeListener(object : View.OnFocusChangeListener{
            override fun onFocusChange(v: View?, hasFocus: Boolean) {
                if (hasFocus) {
                    val inputMethodManager = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showSoftInput(v,0)
                }
            }
        })
        fragmentSearchBinding.searchView2.requestFocus()

        observeViewModel()

        return fragmentSearchBinding.root
    }

    private fun filterList(queryString:String?){
        queryString?.let {
            Log.d(TAG, "[filterList] queryString = $it")

            if (it.isEmpty()) {
                fragmentSearchBinding.noSearchResult.visibility = View.INVISIBLE
                return
            }

            val queryStationList = mutableListOf<StationInfoItem>()
            val queryAvailabilityList = mutableListOf<AvailabilityInfoItem>()
            stationList.forEach { stationItem ->
                if (stationItem.stationName.zhTw.contains(queryString)) {
                    queryStationList.add(stationItem)

                    availabilityInfoList.forEach{ availabilityItem ->
                        if (availabilityItem.StationUID == stationItem.stationUID)
                            queryAvailabilityList.add(availabilityItem)
                    }
                }
            }

            if (queryStationList.isEmpty()) {
                fragmentSearchBinding.noSearchResult.visibility = View.VISIBLE
                Toast.makeText(context,"Do query data! Please try again.",Toast.LENGTH_SHORT).show()
            } else {
                fragmentSearchBinding.noSearchResult.visibility = View.INVISIBLE
                sortListByDistance(queryStationList as ArrayList<StationInfoItem>)
                stationListAdapter.updateStationList(queryStationList)
                stationListAdapter.updateAvailabilityList(queryAvailabilityList)
            }
        }
    }

    private fun sortListByDistance(stationList: ArrayList<StationInfoItem>): ArrayList<StationInfoItem> {
        val comparator = Comparator<StationInfoItem?> { item1, item2 ->
            var distance1 = 0f
            if (item1 != null) {
                val location1 = Location("")
                location1.latitude = item1.stationPosition.positionLat
                location1.longitude = item1.stationPosition.positionLon
                distance1 = StationListFragment.currentLocation.distanceTo(location1)
            }

            var distance2 = 0f
            if (item2 != null) {
                val location2 = Location("")
                location2.latitude = item2.stationPosition.positionLat
                location2.longitude = item2.stationPosition.positionLon
                distance2 = StationListFragment.currentLocation.distanceTo(location2)
            }

//            Log.d(TAG, "distance1 : $distance1, distance2 : $distance2")
//            Log.d(TAG, "o1 : ${item1?.stationName}, o2 : ${item2?.stationName}")
            distance1.compareTo(distance2)
        }
        Collections.sort(stationList, comparator)
        return stationList
    }

    private fun observeViewModel(){
        mapViewModel.allInfo.observe(viewLifecycleOwner, Observer {
            // update search adpater data

            it.first?.let {
                Log.d(TAG,"[allCityStationInfo] list.size = "+it)

                stationList = mutableListOf()

                for (items in it) {
                    Log.d(TAG,"[allCityStationInfo] items.size = "+items.size)
                    stationList.addAll(items)
                }
            }

            it.second?.let {
                Log.d(TAG,"[allCityAvailabilityInfo] list.size = "+it)

                availabilityInfoList = mutableListOf()

                for (items in it) {
                    Log.d(TAG,"[allCityAvailabilityInfo] items.size = "+items.size)
                    availabilityInfoList.addAll(items)
                }
            }
        })
    }

    override fun onItemClick(
        view: View,
        stationInfoItem: StationInfoItem,
        availabilityInfoItem: AvailabilityInfoItem
    ) {
        if (stationInfoItem != null) {
            Log.d(TAG,"[onItemClick] ITEM = "+stationInfoItem.stationName)
            parentFragmentManager.popBackStack()
            mapViewModel.setSelectSearchStationUid(stationInfoItem,availabilityInfoItem)
        }
    }
}
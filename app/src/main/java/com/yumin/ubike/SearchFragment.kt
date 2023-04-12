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

class SearchFragment : Fragment(), StationListAdapter.OnClickListener {
    lateinit var fragmentSearchBinding: FragmentSearchBinding
    var stationList: MutableList<StationInfoItem> = mutableListOf()
    var availabilityInfoList: MutableList<AvailabilityInfoItem> = mutableListOf()
    lateinit var sessionManager: SessionManager
    lateinit var receiver: BroadcastReceiver
    var queryStringEvent: Event<String>? = null

    private val mapViewModel: MapViewModel by activityViewModels {
        val activity = requireNotNull(this.activity)
        val repository = RemoteRepository(SessionManager(activity))
        MyViewModelFactory(repository)
    }
    private lateinit var stationListAdapter: StationListAdapter

    // 資料來不及拿到就等query時顯示progress bar處理
    // 再由這頁每分鐘更新一次
    companion object {
        private const val TAG = "[SearchFragment]"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mapViewModel.getAllCityStationInfo()
        mapViewModel.getAllCityAvailabilityInfo()

        fragmentSearchBinding = FragmentSearchBinding.inflate(inflater)

        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, true)
            sessionManager = SessionManager(it)
        }

        observeViewModel()

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                mapViewModel.getAllCityStationInfo()
                mapViewModel.getAllCityAvailabilityInfo()
            }
        }

        context?.registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_TICK))

        return fragmentSearchBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentSearchBinding.imageButton.setOnClickListener {
            activity?.let {
                it.supportFragmentManager.popBackStack()
            }
        }

        stationListAdapter =
            StationListAdapter(this, mutableListOf(), mutableListOf(), sessionManager)

        fragmentSearchBinding.recyclerView.adapter = stationListAdapter
        fragmentSearchBinding.searchView2.setOnQueryTextListener(object :
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(queryString: String?): Boolean {
                queryString?.let {
                    if (stationList.isNotEmpty() && availabilityInfoList.isNotEmpty()) {
                        fragmentSearchBinding.progressBar.visibility = View.VISIBLE
                        filterList(queryString)
                        fragmentSearchBinding.progressBar.visibility = View.INVISIBLE
                    } else {
                        fragmentSearchBinding.progressBar.visibility = View.VISIBLE
                        queryStringEvent = Event(queryString)
                    }
                }
                return true
            }
        })

        // show keyboard
        fragmentSearchBinding.searchView2.setOnQueryTextFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                val inputMethodManager =
                    context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(view, 0)
            }
        }
        fragmentSearchBinding.searchView2.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        context?.unregisterReceiver(receiver)
    }

    private fun filterList(queryString: String?) {
        queryString?.let {
            Log.d(TAG, "[filterList] queryString = $it")

            if (it.isEmpty()) {
                fragmentSearchBinding.noSearchResult.visibility = View.INVISIBLE
                return
            }

            val queryStationList = mutableListOf<StationInfoItem>()
            val queryAvailabilityList = mutableListOf<AvailabilityInfoItem>()
            stationList?.forEach { stationItem ->
                if (stationItem.stationName.zhTw.contains(queryString)) {
                    queryStationList.add(stationItem)

                    availabilityInfoList.forEach { availabilityItem ->
                        if (availabilityItem.StationUID == stationItem.stationUID)
                            queryAvailabilityList.add(availabilityItem)
                    }
                }
            }

            if (queryStationList.isEmpty()) {
                fragmentSearchBinding.noSearchResult.visibility = View.VISIBLE
                Toast.makeText(context, "Do query data! Please try again.", Toast.LENGTH_SHORT)
                    .show()
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
                distance1 = MapFragment.getLocation().distanceTo(location1)
            }

            var distance2 = 0f
            if (item2 != null) {
                val location2 = Location("")
                location2.latitude = item2.stationPosition.positionLat
                location2.longitude = item2.stationPosition.positionLon
                distance2 = MapFragment.getLocation().distanceTo(location2)
            }

//            Log.d(TAG, "distance1 : $distance1, distance2 : $distance2")
//            Log.d(TAG, "o1 : ${item1?.stationName}, o2 : ${item2?.stationName}")
            distance1.compareTo(distance2)
        }
        Collections.sort(stationList, comparator)
        return stationList
    }

    private fun observeViewModel() {
        mapViewModel.allInfo.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.apply {

                var filterString = false
                queryStringEvent?.getContentIfNotHandled()?.apply {
                    // 還沒處理query string
                    filterString = true
                }

                // update search adpater data
                it.peekContent().first?.let {
                    Log.d(TAG, "[allCityStationInfo] list.size = " + it)

                    stationList = mutableListOf()

                    for (items in it) {
                        Log.d(TAG, "[allCityStationInfo] items.size = " + items.size)
                        stationList.addAll(items)
                    }
                }

                it.peekContent().second?.let {
                    Log.d(TAG, "[allCityAvailabilityInfo] list.size = " + it)

                    availabilityInfoList = mutableListOf()

                    for (items in it) {
                        Log.d(TAG, "[allCityAvailabilityInfo] items.size = " + items.size)
                        availabilityInfoList.addAll(items)
                    }
                }

                if (filterString) {
                    filterList(queryStringEvent?.peekContent())
                    fragmentSearchBinding.progressBar.visibility = View.INVISIBLE
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
            Log.d(
                TAG,
                "[onItemClick] stationName = " + stationInfoItem.stationName + " ,uid = " + stationInfoItem.stationUID
            )
            Log.d(
                TAG,
                "[onItemClick] availabilityInfoItem uid = " + availabilityInfoItem.StationUID
            );
            Log.d(
                TAG,
                "[onItemClick] availabilityInfoItem rent = " + availabilityInfoItem.AvailableRentBikes + ", return = " + availabilityInfoItem.AvailableReturnBikes
            )
            parentFragmentManager.popBackStack()
            mapViewModel.setSelectSearchStationUid(stationInfoItem, availabilityInfoItem)
        }
    }

    override fun onShareClick(intent: Intent) {
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
        val shareIntent = Intent.createChooser(intent, null)
        startActivity(shareIntent)
    }

    override fun onNavigationClick(intent: Intent) {
        startActivity(intent)
    }

    override fun onFavoriteClick(uId: String, add: Boolean) {
        TODO("Not yet implemented")
    }
}
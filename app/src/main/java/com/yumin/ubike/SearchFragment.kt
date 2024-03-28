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
import android.view.inputmethod.InputMethodManager
import android.widget.SearchView
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment() {
    private val TAG = "[SearchFragment]"
    private lateinit var fragmentSearchBinding: FragmentSearchBinding
    private var allStationList = mutableListOf<StationInfoItem>()
    private var allAvailabilityInfoList = mutableListOf<AvailabilityInfoItem>()
    lateinit var broadcastReceiver: BroadcastReceiver
    var queryStringEvent: Event<String>? = null
    @Inject lateinit var sessionManager: SessionManager
    private val mapViewModel: MapViewModel by activityViewModels()
    private lateinit var stationListAdapter: StationListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentSearchBinding = FragmentSearchBinding.inflate(inflater)
        return fragmentSearchBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapViewModel.getAllCityStationInfo()
        mapViewModel.getAllCityAvailabilityInfo()

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)

        observeViewModel()

        setupReceiver()

        setupRecyclerView()

        fragmentSearchBinding.imageButton.setOnClickListener {
            findNavController().popBackStack()
        }

        fragmentSearchBinding.searchView.apply {
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(queryString: String?): Boolean {
                    queryString?.let {
                        queryFromStationList(queryString)
                    }
                    return true
                }
            })

            setOnQueryTextFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    val inputMethodManager =
                        requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showSoftInput(view, 0)
                }
            }
            requestFocus()
        }
    }

    private fun setupRecyclerView() {
        stationListAdapter = StationListAdapter(mutableListOf(), mutableListOf(),sessionManager)

        stationListAdapter.apply {
            setOnItemClickListener { view, stationInfoItem, availabilityInfoItem ->
                stationInfoItem?.let {
                    Log.d(
                        TAG, "[onItemClick] stationName = " + stationInfoItem.stationName +
                                " ,uid = " + stationInfoItem.stationUID +
                                " ,availabilityInfoItem uid = " + availabilityInfoItem.StationUID +
                                " ,availabilityInfoItem rent = " + availabilityInfoItem.AvailableRentBikes +
                                " ,return = " + availabilityInfoItem.AvailableReturnBikes
                    )
                    findNavController().popBackStack()
                    mapViewModel.setSelectSearchStationUid(stationInfoItem, availabilityInfoItem)
                }
            }

            setOnShareClickListener { intent ->
                intent.apply {
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                }
                val shareIntent = Intent.createChooser(intent, null)
                startActivity(shareIntent)
            }

            setOnNavigationClickListener { intent ->
                startActivity(intent)
            }

        }
        fragmentSearchBinding.recyclerView.apply {
            adapter = stationListAdapter
        }
    }

    private fun setupReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                mapViewModel.getAllCityStationInfo()
                mapViewModel.getAllCityAvailabilityInfo()
            }
        }
        requireContext().registerReceiver(broadcastReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    private fun queryFromStationList(queryString: String) {
        Log.d(TAG, "[filterList] queryString = $queryString")

        if (allStationList.isEmpty() || allAvailabilityInfoList.isEmpty()) {
            // Not get the station list from server yet...
            queryStringEvent = Event(queryString)
            return
        }

        var queryStationList = mutableListOf<StationInfoItem>()
        var queryAvailabilityList = mutableListOf<AvailabilityInfoItem>()

        if (queryString.isEmpty()) {
            Log.d(TAG, "[filterList] queryString isEmpty")
            stationListAdapter.updateStationList(queryStationList)
            stationListAdapter.updateAvailabilityList(queryAvailabilityList)
            showNoResultView()
            return
        }

        queryStationList = allStationList.filter {
            it.stationName.zhTw.contains(queryString)
        }.toMutableList()

        queryAvailabilityList = allAvailabilityInfoList.filter { availabilityInfoItem ->
            queryStationList.any {
                it.stationUID == availabilityInfoItem.StationUID
            }
        }.toMutableList()


        if (queryStationList.isEmpty())
            showNoResultView()
        else
            hideNoResultView()


        hideProgressBar()
        SortUtils.sortListByDistance(queryStationList as ArrayList<StationInfoItem>)
        stationListAdapter.updateStationList(queryStationList)
        stationListAdapter.updateAvailabilityList(queryAvailabilityList)
    }

    private fun observeViewModel() {
        mapViewModel.allCityAvailabilityInfo.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is Resource.Success -> {
                        response.data?.let { data ->
                            Log.d(TAG, "[allCityAvailabilityInfo] list.size = " + data.size)
                            allAvailabilityInfoList.clear()
                            // collect all station available info from each city
                            for (availabilityInfo in data) {
                                allAvailabilityInfoList.addAll(availabilityInfo)
                            }

//                            data.forEach {
//                                allAvailabilityInfoList.addAll(it)
//                            }
                            // check query string event
                            queryStringEvent?.getContentIfNotHandled()?.let { queryString ->
                                queryFromStationList(queryString)
                            }
                            hideProgressBar()
                        }
                    }

                    is Resource.Loading -> {
                        Log.d(TAG, "allCityAvailabilityInfo LOADING")
                        showProgressBar()
                    }

                    is Resource.Error -> {
                        response.message?.let { message ->
                            Log.e(TAG,"observe allCityAvailabilityInfo, an error happened: $message")
                        }
                        hideProgressBar()
                    }
                }
            }
        })

        mapViewModel.allCityStationInfo.observe(viewLifecycleOwner, Observer { event ->
            event.getContentIfNotHandled()?.let { response ->
                when (response) {
                    is Resource.Success -> {
                        response.data?.let { data ->
                            Log.d(TAG, "[allCityStationInfo] list.size =  " + data.size)
                            allStationList.clear()
                            // collect all station info from each city
                            for (stationInfo in data) {
                                allStationList.addAll(stationInfo)
                            }
//                            data.forEach {
//                                allStationList.addAll(it)
//                            }
                            // check query string event
                            queryStringEvent?.getContentIfNotHandled()?.let { queryString ->
                                queryFromStationList(queryString)
                            }
                            hideProgressBar()
                        }
                    }

                    is Resource.Loading -> {
                        Log.d(TAG, "allCityStationInfo LOADING")
                        showProgressBar()
                    }

                    is Resource.Error -> {
                        response.message?.let { message ->
                            Log.e(TAG,"observe allCityStationInfo, an error happened: $message")
                        }
                        hideProgressBar()
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
    }

    private fun hideProgressBar() {
        fragmentSearchBinding.progressBar.visibility = View.INVISIBLE
    }

    private fun showProgressBar() {
        fragmentSearchBinding.progressBar.visibility = View.VISIBLE
    }

    private fun showNoResultView() {
        fragmentSearchBinding.noSearchResult.visibility = View.VISIBLE
    }

    private fun hideNoResultView() {
        fragmentSearchBinding.noSearchResult.visibility = View.INVISIBLE
    }
}
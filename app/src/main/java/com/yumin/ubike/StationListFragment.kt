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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.model.LatLng
import com.yumin.ubike.databinding.FragmentStationListBinding
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class StationListFragment : Fragment(), FavoriteItemClickListener {
    private val TAG = "[StationListFragment]"
    private lateinit var fragmentStationListBinding: FragmentStationListBinding
    private val viewModel: MapViewModel by activityViewModels()
    private lateinit var currentLatLng: LatLng
    private var stationRange: Int = MapFragment.stationRange
    private var type: Int = 0
    private lateinit var broadcastReceiver: BroadcastReceiver
    lateinit var currentLocation: Location
    private lateinit var stationListAdapter: StationListAdapter

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
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.primary_700)
        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.apply {
            isAppearanceLightStatusBars = false
        }

        arguments?.let { bundle ->
            currentLatLng = LatLng(
                bundle.getDouble(MapFragment.KEY_LATITUDE),
                bundle.getDouble(MapFragment.KEY_LONGITUDE)
            )
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
        viewModel.getNearbyStation(
            currentLatLng.latitude,
            currentLatLng.longitude,
            stationRange,
            type
        )
    }

    private fun initView() {
        stationListAdapter = StationListAdapter(this)
        fragmentStationListBinding.stationListView.adapter = stationListAdapter

        fragmentStationListBinding.imageButton.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.nearbyStation.collect{
                    Log.d(TAG, "[nearbyStationDataWithFavorite] DATA SIZE = " + it.size)
                    SortUtils.sortFavoriteListByDistance(it)
                    stationListAdapter.submitList(it)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
    }

    override fun onClick(favoriteItemClickEvent: FavoriteItemClickEvent) {
        Log.d(TAG, "[onClick] event = $favoriteItemClickEvent")
        when (favoriteItemClickEvent) {
            is FavoriteItemClickEvent.ItemClick -> {
                viewModel.setSelectStationUid(favoriteItemClickEvent.ubikeStationWithFavorite.item.stationUID)
                findNavController().popBackStack()
            }

            is FavoriteItemClickEvent.NavigationClick -> {
                startActivity(favoriteItemClickEvent.intent)
            }

            is FavoriteItemClickEvent.ShareClick -> {
                val intent = favoriteItemClickEvent.intent
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                val shareIntent = Intent.createChooser(intent, null)
                startActivity(shareIntent)
            }

            is FavoriteItemClickEvent.FavoriteClick -> {
                // through room to edit favorite table
                if (favoriteItemClickEvent.isFavorite)
                    viewModel.addToFavoriteList(favoriteItemClickEvent.stationUid)
                else
                    viewModel.removeFromFavoriteList(favoriteItemClickEvent.stationUid)
            }
        }
    }
}
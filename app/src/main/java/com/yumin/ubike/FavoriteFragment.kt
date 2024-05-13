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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.yumin.ubike.databinding.FragmentFavoriteBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoriteFragment : Fragment(), FavoriteItemClickListener {
    private val TAG = "[FavoriteFragment]"
    private lateinit var fragmentFavoriteBinding: FragmentFavoriteBinding
    private val mapViewModel: MapViewModel by activityViewModels()
    private lateinit var broadcastReceiver: BroadcastReceiver
    private lateinit var stationListAdapter: StationListAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentFavoriteBinding = FragmentFavoriteBinding.inflate(inflater)
        return fragmentFavoriteBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "[onViewCreated]")
        showProgressBar()
//        mapViewModel.getFavoriteStation()

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, true)
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.primary_700)

        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.apply {
            isAppearanceLightStatusBars = false
        }

        setupBroadcastReceiver()
        setupRecyclerView()

        fragmentFavoriteBinding.imageButton.setOnClickListener {
            findNavController().popBackStack()
        }

        observeViewModel()
    }

    private fun setupRecyclerView() {
        stationListAdapter = StationListAdapter(this)
        fragmentFavoriteBinding.favoriteRecyclerView.adapter = stationListAdapter
    }

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                mapViewModel.refreshAllCityStation()
            }
        }
        requireContext().registerReceiver(broadcastReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mapViewModel.favoriteStation.collect { result ->
                    hideProgressBar()
                    Log.d(TAG, "favoriteStationData SIZE = " + result.size)
                    SortUtils.sortFavoriteListByDistance(result)
                    stationListAdapter.submitList(result)
                }
            }
        }
    }

    private fun hideProgressBar() {
        fragmentFavoriteBinding.progressBar.visibility = View.INVISIBLE
    }

    private fun showProgressBar() {
        fragmentFavoriteBinding.progressBar.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
    }

    override fun onClick(favoriteClick: FavoriteItemClickEvent) {
        when (favoriteClick) {
            is FavoriteItemClickEvent.FavoriteClick -> {
                if (favoriteClick.isFavorite)
                    mapViewModel.addToFavoriteList(favoriteClick.stationUid)
                else
                    mapViewModel.removeFromFavoriteList(favoriteClick.stationUid)
            }

            is FavoriteItemClickEvent.ItemClick -> {
                mapViewModel.setSearchStationUid(favoriteClick.ubikeStationWithFavorite)
                findNavController().popBackStack()
            }

            is FavoriteItemClickEvent.NavigationClick -> {
                startActivity(favoriteClick.intent)
            }

            is FavoriteItemClickEvent.ShareClick -> {
                val intent = favoriteClick.intent
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                val shareIntent = Intent.createChooser(intent, null)
                startActivity(shareIntent)
            }
        }
    }
}
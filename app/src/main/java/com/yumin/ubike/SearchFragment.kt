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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.yumin.ubike.databinding.FragmentSearchBinding
import com.yumin.ubike.room.Database
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SearchFragment : Fragment(), FavoriteItemClickListener {
    private val TAG = "[SearchFragment]"
    private lateinit var fragmentSearchBinding: FragmentSearchBinding
    lateinit var broadcastReceiver: BroadcastReceiver
    @Inject
    lateinit var database: Database
    private val mapViewModel: MapViewModel by activityViewModels()
    private lateinit var stationListAdapter: StationListAdapter


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentSearchBinding = FragmentSearchBinding.inflate(inflater)
        return fragmentSearchBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
                    Log.d(TAG,"[onQueryTextSubmit] queryString = $query")
                    showProgressBar()
                    mapViewModel.queryStation(query)
                    return true
                }

                override fun onQueryTextChange(queryString: String?): Boolean {
                    Log.d(TAG,"[onQueryTextChange] queryString = $queryString")
                    if (queryString.isNullOrBlank()) {
                        mapViewModel.queryStation(queryString)
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
        stationListAdapter = StationListAdapter(this)
        stationListAdapter.submitList(emptyList())
        fragmentSearchBinding.recyclerView.apply {
            adapter = stationListAdapter
        }
    }

    private fun setupReceiver() {
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
                mapViewModel.searchStation.collect {
                    Log.d(TAG,"testSearchStation, SIZE = "+it.size)
                    hideProgressBar()
                    if (it.isEmpty()) {
                        stationListAdapter.submitList(emptyList())
                        showNoResultView()
                    } else {
                        SortUtils.sortFavoriteListByDistance(it)
                        stationListAdapter.submitList(it)
                        hideNoResultView()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mapViewModel.queryStationName.collect {
                    fragmentSearchBinding.searchView.setQuery(it,false)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireContext().unregisterReceiver(broadcastReceiver)
    }

    private fun hideProgressBar() {
        Log.d(TAG,"[hideProgressBar]")
        fragmentSearchBinding.progressBar.visibility = View.INVISIBLE
    }

    private fun showProgressBar() {
        Log.d(TAG,"[showProgressBar]")
        fragmentSearchBinding.progressBar.visibility = View.VISIBLE
    }

    private fun showNoResultView() {
        fragmentSearchBinding.noSearchResult.visibility = View.VISIBLE
    }

    private fun hideNoResultView() {
        fragmentSearchBinding.noSearchResult.visibility = View.INVISIBLE
    }

    override fun onClick(favoriteClick: FavoriteItemClickEvent) {
        when (favoriteClick) {
            is FavoriteItemClickEvent.ItemClick -> {
                mapViewModel.setSearchStationUid(favoriteClick.ubikeStationWithFavorite)
                findNavController().popBackStack()
            }

            is FavoriteItemClickEvent.FavoriteClick -> {
                // through room to edit favorite table
                if (favoriteClick.isFavorite)
                    mapViewModel.addToFavoriteList(favoriteClick.stationUid)
                else
                    mapViewModel.removeFromFavoriteList(favoriteClick.stationUid)
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
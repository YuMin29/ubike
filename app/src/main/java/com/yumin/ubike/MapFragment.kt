package com.yumin.ubike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.maps.android.SphericalUtil
import com.google.maps.android.clustering.ClusterManager
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentMapBinding
import com.yumin.ubike.repository.RemoteRepository
import java.util.*
import kotlin.collections.HashMap


/**
 * MapFragment responsible for show map
 */
class MapFragment : Fragment(), LocationListener, StationClusterRenderer.Callback, OnMapReadyCallback {
    private val TAG: String = "[MapFragment]"
    private lateinit var fragmentMapBinding: FragmentMapBinding
    private lateinit var locationManager: LocationManager
    private lateinit var mapView: View
    private lateinit var myGoogleMap: GoogleMap
    private val mapViewModel: MapViewModel by activityViewModels{
        val activity = requireNotNull(this.activity)
        val repository = RemoteRepository(SessionManager(activity))
        MyViewModelFactory(repository)
    }
    private var isDrawCurrentPosition: Boolean = false
    private lateinit var currentLocationWhenStart: LatLng
    private var availableList: ArrayList<AvailabilityInfoItem> = ArrayList()
    private var ubikeType: Int = 0 // 0 -> all
    private lateinit var clusterManager: ClusterManager<StationClusterItem>
    private var clusterItemMap: HashMap<String, StationClusterItem> = HashMap()
    private lateinit var currentLatLng: LatLng
    private var zoomDistance: Double = 0.0
    private lateinit var latestRefreshTime: Date
    private var isRefreshed = false

    private var isMoveToSelectedStation = false
    private var selectStationUid = ""
    private lateinit var stationClusterRenderer: StationClusterRenderer
    private var markerMap : HashMap<String,Marker> = HashMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG,"[onCreateView]")

        fragmentMapBinding = FragmentMapBinding.inflate(inflater)

        activity?.let {
            WindowCompat.setDecorFitsSystemWindows(it.window, false)
            it.window.statusBarColor = it.getColor(R.color.transparent)
        }

        locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Get current location by network
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.0f, this)

        Log.d(TAG, "mapViewModel = $mapViewModel")

        setupMap()

        setupUI()

        context?.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                refreshAvailabilityData()
                if (!isRefreshed)
                    isRefreshed = true

                latestRefreshTime = Calendar.getInstance().time
            }
        }, IntentFilter(Intent.ACTION_TIME_TICK))

        return fragmentMapBinding.root
    }

    private fun setupMap() {
        (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)
        mapView = (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).requireView()
    }

    private fun refreshAvailabilityData() {
        Log.d(
            TAG, "[refreshAvailabilityData] lat : ${currentLatLng.latitude}, " +
                    "lon : ${currentLatLng.longitude}, distance : ${zoomDistance.toInt()}"
        )
        mapViewModel.getAvailabilityNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            zoomDistance.toInt(),
            ubikeType,
            true
        )
    }

    private fun setupUI() {
        fragmentMapBinding.ubikeAll.setOnClickListener {
            ubikeType = 0
            clearMap()
            getCurrentStationInfo()
        }

        fragmentMapBinding.ubike10.setOnClickListener {
            ubikeType = 1
            clearMap()
            getCurrentStationInfo()
        }

        fragmentMapBinding.ubike20.setOnClickListener {
            ubikeType = 2
            clearMap()
            getCurrentStationInfo()
        }

        fragmentMapBinding.favoriteStationInfo.setOnClickListener {}

        fragmentMapBinding.stationInfoListView.setOnClickListener {
            val bundle = Bundle()
            bundle.putDouble("longitude",currentLatLng.longitude)
            bundle.putDouble("latitude", currentLatLng.latitude)
            bundle.putInt("distance", zoomDistance.toInt())
            bundle.putParcelable("location", myGoogleMap.myLocation)
            (activity as MainActivity).replaceStationListFragment(bundle)
        }

        fragmentMapBinding.searchView.setOnClickListener{
            (activity as MainActivity).replaceSearchFragment()
        }
    }

    private fun clearMap() {
        clusterManager.clearItems()
        clusterItemMap.clear()
        markerMap.clear()
//        googleMap.clear()
    }

    private fun getCurrentStationInfo() {
        mapViewModel.getStationInfoNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            zoomDistance.toInt(),
            ubikeType
        )
        mapViewModel.getAvailabilityNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            zoomDistance.toInt(),
            ubikeType,
            false
        )
    }

    private fun getZoomDistance(): Double {
        val visibleRegion = myGoogleMap.projection.visibleRegion
        val distance: Double = SphericalUtil.computeDistanceBetween(
            visibleRegion.farLeft, myGoogleMap.cameraPosition.target
        )
        Log.d(TAG, "[getZoomDistance] DISTANCE = $distance");
        return distance
    }

    private fun observeViewModelData() {
        mapViewModel.selectStationUid.observe(viewLifecycleOwner, Observer {
            Log.d(TAG, "[observeViewModelData] selectStationUid = $it")
            selectStationUid = it
            isMoveToSelectedStation = true
        })

        mapViewModel.stationWholeInfo.observe(viewLifecycleOwner, Observer { stationWholeInfo ->
            Log.d(
                TAG,
                "[observeViewModelData] stationWholeInfo first  : " + stationWholeInfo.first?.size
            )
            Log.d(
                TAG,
                "[observeViewModelData] stationWholeInfo second : " + stationWholeInfo.second?.size
            )
            if (stationWholeInfo.first?.size == stationWholeInfo.second?.size) {
                stationWholeInfo.second?.let { availableValue -> availableList = availableValue }
                stationWholeInfo.first?.let { stationValue -> addClusterItems(stationValue) }
            }
        })

        mapViewModel.refreshAvailability.observe(
            viewLifecycleOwner,
            Observer { refreshAvailability ->
                Log.d(
                    TAG,
                    "[observeViewModelData] refreshAvailability size : " + refreshAvailability.size
                )
                availableList = refreshAvailability
                // TODO : need to update cluster icon
                refreshClusterItems(refreshAvailability)
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // release here
        Log.d(TAG,"[onDestroyView]")
        clearMap()
        myGoogleMap.clear()
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "LOCATION = " + location.latitude + "," + location.longitude)

        if (!isDrawCurrentPosition) {
            isDrawCurrentPosition = true
            currentLocationWhenStart = LatLng(location.latitude, location.longitude)
            // move to current position
            myGoogleMap?.moveCamera(CameraUpdateFactory.newLatLng(currentLocationWhenStart))
        }
    }

    private fun showBottomSheetDialog(stationInfoItem: StationInfoItem,availabilityInfoItem: AvailabilityInfoItem) {
        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_NoWiredStrapInNavigationBar)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_dialog_layout, null)
        val stationNameSplitList = stationInfoItem.stationName.zhTw.split("_")
        val stationId = stationInfoItem.stationUID

        // 顯示最後更新時間
        var currentTimeDate = Calendar.getInstance().time
        var diff = currentTimeDate.time - latestRefreshTime.time
        val diffSeconds = (diff / 1000).toInt()
        Log.d(
            TAG,
            "[showBottomSheetDialog] latestRefreshTime time = ${latestRefreshTime.toString()}"
        )
        Log.d(TAG, "[showBottomSheetDialog] currentTimeDate = ${currentTimeDate.toString()}")

        val updateTime = view.findViewById<TextView>(R.id.updateTime)
        updateTime.text = diffSeconds.toString() + "秒前更新"

        // 顯示站點距離
        val stationLatLng = LatLng(
            stationInfoItem.stationPosition.positionLat,
            stationInfoItem.stationPosition.positionLon
        )
        val distance = getStationDistance(stationLatLng)
        val showDistance = view.findViewById<TextView>(R.id.distance)
        showDistance.text = "距離" + distance
        Log.d(TAG, "[showBottomSheetDialog] show distance = " + getStationDistance(stationLatLng))

        val stationName = view.findViewById<TextView>(R.id.station_name)
        stationName.text = stationNameSplitList[1]

        val stationAddress = view.findViewById<TextView>(R.id.station_address)
        stationAddress.text = stationInfoItem.stationAddress.zhTw

        val type = view.findViewById<TextView>(R.id.type)
        type.text = stationNameSplitList[0]

        val availableRent = view.findViewById<TextView>(R.id.available_rent)
        availableRent.text = availabilityInfoItem.AvailableRentBikes.toString() + "可借"

        val availableReturn = view.findViewById<TextView>(R.id.available_return)
        availableReturn.text = availabilityInfoItem.AvailableReturnBikes.toString() + "可還"

        dialog.setCancelable(true)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun getStationDistance(stationLatLng: LatLng): String {
        var stationLocation = Location("station")
        stationLocation.latitude = stationLatLng.latitude
        stationLocation.longitude = stationLatLng.longitude
        val distance = stationLocation.distanceTo(myGoogleMap.myLocation)

        return if (distance > 1000) {
            "%.2f".format(distance / 1000).toString() + "公里"
        } else
            distance.toInt().toString() + "公尺"
    }

    private fun getRateIcon(availableRent: Int, availableReturn: Int): Int {
        if (availableRent == 0)
            return R.drawable.ic_ubike_icon_0

        val availableRate =
            ((availableRent.toFloat() / (availableRent.toFloat() + availableReturn.toFloat())) * 100).toInt()

        return when {
            availableRate == 0 -> R.drawable.ic_ubike_icon_0
            availableRate <= 10 -> R.drawable.ic_ubike_icon_10
            availableRate <= 20 -> R.drawable.ic_ubike_icon_20
            availableRate <= 30 -> R.drawable.ic_ubike_icon_30
            availableRate <= 40 -> R.drawable.ic_ubike_icon_40
            availableRate <= 50 -> R.drawable.ic_ubike_icon_50
            availableRate <= 60 -> R.drawable.ic_ubike_icon_60
            availableRate <= 70 -> R.drawable.ic_ubike_icon_70
            availableRate <= 80 -> R.drawable.ic_ubike_icon_80
            availableRate <= 90 -> R.drawable.ic_ubike_icon_90
            else -> R.drawable.ic_ubike_icon_100
        }
    }

    private fun findAvailableInfoItem(stationId: String): AvailabilityInfoItem? {
        for (item in availableList){
            if (item.StationUID == stationId)
                return item
        }
        return null
    }

    private fun setUpClusterManager() {
        clusterManager = ClusterManager(context, myGoogleMap)
        stationClusterRenderer = StationClusterRenderer(context, myGoogleMap, clusterManager,this)
        clusterManager.renderer = stationClusterRenderer
        myGoogleMap.setOnMarkerClickListener(clusterManager)
        myGoogleMap.setOnInfoWindowClickListener(clusterManager)
        clusterManager.setOnClusterClickListener {
            Toast.makeText(context, "[setOnClusterClickListener]", Toast.LENGTH_SHORT).show()
            true
        }
        clusterManager.setOnClusterInfoWindowClickListener {
            Toast.makeText(context, "[setOnClusterInfoWindowClickListener]", Toast.LENGTH_SHORT)
                .show()
        }

        clusterManager.setOnClusterItemClickListener {
            Toast.makeText(context, "[setOnClusterItemClickListener]", Toast.LENGTH_SHORT).show()
            showBottomSheetDialog(it.stationInfoItem,it.availabilityInfoItem)
            true
        }

        clusterManager.setOnClusterItemInfoWindowLongClickListener {
            Toast.makeText(
                context,
                "[setOnClusterItemInfoWindowLongClickListener]",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshClusterItems(availabilityInfo: AvailabilityInfo) {
        var refreshTimes = 0
        availabilityInfo.forEach { item ->
            if (clusterItemMap.containsKey(item.StationUID)) {
                // need to update icon
                val updateItem = clusterItemMap.get(item.StationUID)
                updateItem?.availabilityInfoItem = item
                val iconId = getRateIcon(
                    item.AvailableRentBikes,
                    item.AvailableReturnBikes
                )
                updateItem?.imageId = iconId
                val success = clusterManager.updateItem(updateItem)
                Log.d(TAG, "[refreshClusterItems] success = $success")
                refreshTimes++
            }
            clusterManager.cluster()
            Log.d(TAG, "[refreshClusterItems] refreshTimes = $refreshTimes")
        }
    }

    private fun addClusterItems(stationInfo: StationInfo) {
        var addTimes = 0
        var updateTimes = 0
        stationInfo.forEach { stationInfoItem ->
            if (clusterItemMap.containsKey(stationInfoItem.stationUID)) {
                // need to update icon
                val updateItem = clusterItemMap[stationInfoItem.stationUID]
                val availabilityInfoItem = updateItem?.let { findAvailableInfoItem(it.stationUid) }
                val iconId = availabilityInfoItem?.let {
                    getRateIcon(
                        it.AvailableRentBikes,
                        it.AvailableReturnBikes
                    )
                }
                if (iconId != null) {
                    updateItem?.imageId = iconId
                }
                clusterManager.updateItem(updateItem)
                updateTimes++
            } else {
                val availabilityInfoItem = findAvailableInfoItem(stationInfoItem.stationUID)

                availabilityInfoItem?.let {
                    val iconId = getRateIcon(availabilityInfoItem.AvailableRentBikes, availabilityInfoItem.AvailableReturnBikes)

                    val myItem = StationClusterItem(
                        LatLng(stationInfoItem.stationPosition.positionLat,
                               stationInfoItem.stationPosition.positionLon),
                        "Title ${stationInfoItem.stationName}",
                        "Snippet ${stationInfoItem.stationUID}",
                        null,
                        iconId,
                        stationInfoItem,
                        stationInfoItem.stationUID,
                        availabilityInfoItem
                    )
                    clusterManager.addItem(myItem)
                    clusterItemMap.put(stationInfoItem.stationUID, myItem)
                    addTimes++
                }
            }
        }

        availableList.forEach { availabilityInfoItem ->
            Log.d(
                TAG,
                "[addClusterItems] [update] availability item update time : ${availabilityInfoItem.UpdateTime}"
            )
        }

        Log.d(TAG, "[addClusterItems] [update] availability SIZE : ${availableList.size}")
        Log.d(TAG, "[addClusterItems] addTimes : $addTimes, updateTimes : $updateTimes")

        clusterManager.cluster()
        clusterManager.onCameraIdle()
    }

    override fun clusterItemRendered(clusterItem: StationClusterItem, marker: Marker) {
        if (!markerMap.containsKey(clusterItem.stationUid))
            markerMap.put(clusterItem.stationUid,marker)


        if (isMoveToSelectedStation && markerMap.containsKey(selectStationUid)) {
            isMoveToSelectedStation = false
            // move to select station
            myGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.position, 17f),
                object : GoogleMap.CancelableCallback{
                override fun onCancel() {

                }

                override fun onFinish() {
                    showBottomSheetDialog(clusterItem.stationInfoItem,clusterItem.availabilityInfoItem)
                }
            })
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "[onMapReady]")
        this.myGoogleMap = googleMap

        setUpClusterManager()
        observeViewModelData()

        this.myGoogleMap.isMyLocationEnabled = true

        this.myGoogleMap.setOnCameraMoveStartedListener {
            Log.d(TAG, "[setOnCameraMoveStartedListener]")
        }

        this.myGoogleMap.setOnCameraMoveCanceledListener {
            Log.d(TAG, "[setOnCameraMoveCanceledListener]")
        }

        this.myGoogleMap.setOnCameraIdleListener {
            Log.d(TAG, "[setOnCameraIdleListener]")
            val animation = AnimationUtils.loadAnimation(context, R.anim.position_animation)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {
                    fragmentMapBinding.anchorPoint.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animation?) {
                    fragmentMapBinding.anchorPoint.visibility = View.INVISIBLE
                }

                override fun onAnimationRepeat(animation: Animation?) {
                }
            })
            fragmentMapBinding.anchorPoint.startAnimation(animation)

            zoomDistance = getZoomDistance()
            if (zoomDistance > 15000) {
                Log.d(
                    TAG,
                    "[setOnCameraIdleListener] Distance : $zoomDistance > 1500 , return"
                )
                return@setOnCameraIdleListener
            }

            currentLatLng = this.myGoogleMap.cameraPosition.target
            Log.d(
                TAG,
                "[setOnCameraIdleListener] currentLatLng = ${currentLatLng.latitude},${currentLatLng.longitude}"
            )

            getCurrentStationInfo()

            Log.d(
                TAG, "[setOnCameraIdleListener] lat : ${currentLatLng.latitude}, " +
                        "lon : ${currentLatLng.longitude}, distance : ${zoomDistance.toInt()}"
            )

            if (!isRefreshed)
                latestRefreshTime = Calendar.getInstance().time
        }

        // show current location button
        val myLocationButton =
            (mapView.findViewById<View>(Integer.parseInt("1")).parent as View)
                .findViewById<View>(Integer.parseInt("2"))
        val rlp = myLocationButton.layoutParams as (RelativeLayout.LayoutParams)
        // set current location button position on right bottom
        rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
        rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        rlp.setMargins(0, 0, 30, 30)
    }

}
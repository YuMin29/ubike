package com.yumin.ubike

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.maps.android.SphericalUtil
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentMapBinding
import com.yumin.ubike.databinding.LayoutBottomSheetDialogBinding
import com.yumin.ubike.repository.RemoteRepository
import java.util.*


/**
 * MapFragment responsible for show map
 */
class MapFragment : Fragment(), LocationListener, OnMapReadyCallback {
    private lateinit var fragmentMapBinding: FragmentMapBinding
    private lateinit var locationManager: LocationManager
    private lateinit var mapView: View
    private lateinit var myGoogleMap: GoogleMap
    private val mapViewModel: MapViewModel by activityViewModels {
        val repository = RemoteRepository(SessionManager(requireActivity()))
        MyViewModelFactory(repository)
    }
    private var isDrawCurrentPosition: Boolean = false
    private lateinit var currentLocationWhenStart: LatLng
    private var availableList: ArrayList<AvailabilityInfoItem> = ArrayList()
    private var ubikeType: Int = 0 // 0 -> all
    private var zoomDistance: Double = 0.0
    private lateinit var latestRefreshTime: Date
    private var isRefreshed = false
    private var isMoveToSelectedStation = false
    private var selectStationUid = ""
    private var googleMapMarkers: HashMap<String, Marker> = HashMap()
    private var isMoveToSearchStation = false
    private var searchStationUid = ""
    private var showingMarker: Marker? = null
    private lateinit var receiver: BroadcastReceiver
    private var mapCircle: Circle? = null
    private lateinit var sessionManager: SessionManager

    companion object {
        private const val TAG = "[MapFragment]"
        lateinit var currentLatLng: LatLng
        fun getLocation(): Location {
            var location = Location("")
            location.longitude = currentLatLng.longitude
            location.latitude = currentLatLng.latitude
            return location
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "[onCreateView]")
        Log.d(TAG, "[onCreateView]")
        fragmentMapBinding = FragmentMapBinding.inflate(inflater)
        // setup status bar color to transparent
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.transparent)
        return fragmentMapBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        setupMap()
//        setupUI()
        checkPermission()
    }

    private fun checkPermission() {
        if ((ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED)) {
            requestLocationPermission();
        } else {
            // permission granted
            init()
        }
    }

    private fun requestLocationPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // show dialog to explain why this app need these permissions
            showPermissionDialog()
        } else {
            // You can directly ask for the permission.
            launchSinglePermission()
        }
    }

    private fun showPermissionDialog() {
        val builder = AlertDialog.Builder(requireContext()).apply {
            setMessage(R.string.dialog_message).setTitle(R.string.dialog_title)
            setPositiveButton(R.string.dialog_ok) { dialog, which ->
                launchSinglePermission()
            }
        }
        val dialog = builder.create()
        dialog.show()
    }

    private fun launchSinglePermission() {
        requestSinglePermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val requestSinglePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // granted
            init()
        } else {
            // show no permission error
            Toast.makeText(requireContext(), getString(R.string.denied_location_permission), Toast.LENGTH_SHORT).show()
        }
    }

    private fun init() {
        fragmentMapBinding.mapGroup.visibility = View.VISIBLE
        fragmentMapBinding.locationOff.visibility = View.GONE
        sessionManager = SessionManager(requireContext())
        locationManager = requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Get current location by network
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.0f, this)
        setupMap()
        setupUI()
        setupReceiver()
    }

    private fun setupReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                refreshAvailabilityData()
                if (!isRefreshed)
                    isRefreshed = true

                latestRefreshTime = Calendar.getInstance().time

                context?.let {
                    if (NetworkChecker.checkConnectivity(it)) {
                        mapViewModel.getAllCityStationInfo()
                        mapViewModel.getAllCityAvailabilityInfo()
                    }
                }
            }
        }
        requireContext().registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    private fun setupMap() {
        (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)
        mapView =
            (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).requireView()
    }

    private fun refreshAvailabilityData() {
        Log.d(
            TAG, "[refreshAvailabilityData] lat : ${currentLatLng.latitude}, " +
                    "lon : ${currentLatLng.longitude}, distance : ${zoomDistance.toInt()}"
        )
        mapViewModel.getAvailabilityNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            2000,
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

        fragmentMapBinding.favoriteStationInfo.setOnClickListener {
            // switch to favorite fragment
            if (NetworkChecker.checkConnectivity(requireContext())) {
                mapViewModel.getAllCityStationInfo()
                mapViewModel.getAllCityAvailabilityInfo()
            }
            findNavController().navigate(R.id.action_map_to_favorite)
        }

        fragmentMapBinding.stationInfoListView.setOnClickListener {
            val bundle = Bundle().apply {
                putDouble("longitude", currentLatLng.longitude)
                putDouble("latitude", currentLatLng.latitude)
                putInt("distance", zoomDistance.toInt())
                putParcelable("location", myGoogleMap.myLocation)
                putInt("type", ubikeType)
            }
            findNavController().navigate(R.id.action_map_to_list, bundle)
        }

        fragmentMapBinding.searchView.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_search)
        }
    }

    private fun clearMap() {
        myGoogleMap.clear()
        googleMapMarkers.clear()
    }

    private fun getCurrentStationInfo() {
        mapViewModel.getStationInfoNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            2000,
            ubikeType
        )
        mapViewModel.getAvailabilityNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            2000,
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
        mapViewModel.searchStationUid.observe(viewLifecycleOwner, Observer {
            it.getContentIfNotHandled()?.apply {
                Log.d(TAG, "[observeViewModelData] searchStationUid = ${this.first.stationUID}")

                searchStationUid = this.first.stationUID
                isMoveToSearchStation = true

                // clear markers
                clearMap()
                // create new cluster
                addMarker(this.first, this.second)
            }
        })

        mapViewModel.selectStationUid.observe(viewLifecycleOwner, Observer {
            Log.d(TAG, "[observeViewModelData] selectStationUid = $it")
            it.getContentIfNotHandled()?.apply {
                selectStationUid = this
                isMoveToSelectedStation = true
                if (myGoogleMap.cameraPosition.zoom < 16)
                    myGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(16f))
            }
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
                stationWholeInfo.first?.let { stationValue ->
                    addGoogleMapMarkers(stationValue)
                }
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
                updateGoogleMapMarkers(refreshAvailability)
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // release here
        Log.d(TAG, "[onDestroyView]")
        clearMap()
        context?.unregisterReceiver(receiver)
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

    private fun showBottomSheetDialog(
        stationInfoItem: StationInfoItem,
        availabilityInfoItem: AvailabilityInfoItem
    ) {
        // scale icon bitmap => big bitmap
        val iconId = getRateIcon(
            availabilityInfoItem.AvailableRentBikes,
            availabilityInfoItem.AvailableReturnBikes
        )
        showingMarker?.setIcon(getBitmapFromVector(context, iconId, 130, 130))


        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_NoWiredStrapInNavigationBar)
        val bindView = LayoutBottomSheetDialogBinding.inflate(layoutInflater)
        val stationNameSplitList = stationInfoItem.stationName.zhTw.split("_")

        var isFavorite = false
        // check if favorite station
        if (sessionManager.fetchFavoriteList().contains(stationInfoItem.stationUID)) {
            bindView.favorite.setCompoundDrawablesWithIntrinsicBounds(
                null,
                ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_favorite_24),
                null,
                null
            )
            isFavorite = true
        }

        // show update time
        var currentTimeDate = Calendar.getInstance().time
        var diff = currentTimeDate.time - latestRefreshTime.time
        val diffSeconds = (diff / 1000).toInt()
        Log.d(
            TAG,
            "[showBottomSheetDialog] latestRefreshTime time = ${latestRefreshTime.toString()}"
        )
        Log.d(TAG, "[showBottomSheetDialog] currentTimeDate = ${currentTimeDate.toString()}")

        bindView.updateTime.text = diffSeconds.toString() + "秒前更新"

        // show station distance
        val stationLatLng = LatLng(
            stationInfoItem.stationPosition.positionLat,
            stationInfoItem.stationPosition.positionLon
        )
        val distance = getStationDistance(stationLatLng)
        bindView.distance.text = "距離" + distance
        Log.d(TAG, "[showBottomSheetDialog] show distance = " + getStationDistance(stationLatLng))
        bindView.stationName.text = stationNameSplitList[1]
        bindView.stationAddress.text = stationInfoItem.stationAddress.zhTw
        bindView.type.text = stationNameSplitList[0]
        bindView.availableRent.text = availabilityInfoItem.AvailableRentBikes.toString() + "可借"
        bindView.availableReturn.text = availabilityInfoItem.AvailableReturnBikes.toString() + "可還"
        bindView.share.setOnClickListener {
            val sendIntent: Intent = Intent().apply {
                val mapUri =
                    "https://www.google.com/maps/dir/?api=1&destination=" + stationInfoItem.stationPosition.positionLat + "," + stationInfoItem.stationPosition.positionLon
                action = Intent.ACTION_SEND
                putExtra(
                    Intent.EXTRA_TEXT,
                    stationNameSplitList[1] + "有" + availabilityInfoItem.AvailableRentBikes.toString() + "可借" +
                            availabilityInfoItem.AvailableReturnBikes.toString() + "可還"
                            + "，地點在$mapUri"
                )
                type = "text/plain"
            }
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
        bindView.navigate.setOnClickListener {
            val gmmIntentUri =
                Uri.parse("google.navigation:q=" + stationInfoItem.stationPosition.positionLat + "," + stationInfoItem.stationPosition.positionLon + "&mode=w")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }
        bindView.favorite.setOnClickListener {
            if (isFavorite) {
                isFavorite = false
                sessionManager.removeFromFavoriteList(stationInfoItem.stationUID)
                bindView.favorite.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.ic_baseline_favorite_border_24
                    ),
                    null,
                    null
                )
            } else {
                isFavorite = true
                sessionManager.addToFavoriteList(stationInfoItem.stationUID)
                bindView.favorite.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_favorite_24),
                    null,
                    null
                )
            }
        }
        dialog.setOnCancelListener {
            // dismiss
            Log.d(TAG, "[Dialog] dismiss")
            // scale icon bitmap => big bitmap
            val iconId = getRateIcon(
                availabilityInfoItem.AvailableRentBikes,
                availabilityInfoItem.AvailableReturnBikes
            )
            showingMarker?.setIcon(getBitmapFromVector(context, iconId, -1, -1))
            showingMarker = null
        }

        dialog.setCancelable(true)
        dialog.setContentView(bindView.root)
        dialog.show()
    }

    private fun getStationDistance(stationLatLng: LatLng): String {
        var stationLocation = Location("station")
        stationLocation.latitude = stationLatLng.latitude
        stationLocation.longitude = stationLatLng.longitude
        val distance = stationLocation.distanceTo(myGoogleMap.myLocation)

        return if (distance > 1000) {
            "%.2f".format(distance / 1000).toString() + getString(R.string.km)
        } else
            distance.toInt().toString() + getString(R.string.meter)
    }

    private fun getStationDistanceF(stationLatLng: LatLng): Float {
        var stationLocation = Location("station")
        stationLocation.latitude = stationLatLng.latitude
        stationLocation.longitude = stationLatLng.longitude

        var currentLocation = Location("")
        currentLocation.latitude = currentLatLng.latitude
        currentLocation.longitude = currentLatLng.longitude

        val distance = stationLocation.distanceTo(currentLocation)

        return distance
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
        for (item in availableList) {
            if (item.StationUID == stationId)
                return item
        }
        return null
    }

    private fun addMarker(
        stationInfoItem: StationInfoItem,
        availabilityInfoItem: AvailabilityInfoItem
    ) {
        // move to search station
        if (isMoveToSearchStation && !isMoveToSelectedStation) {
            Log.d(
                TAG,
                "Find SEARCH station! position = " + stationInfoItem.stationPosition.toString()
            )
            myGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(
                    stationInfoItem.stationPosition.positionLat,
                    stationInfoItem.stationPosition.positionLon
                ),
                16f
            ), 1000,
                object : GoogleMap.CancelableCallback {
                    override fun onCancel() {

                    }

                    override fun onFinish() {
                        val iconId = getRateIcon(
                            availabilityInfoItem.AvailableRentBikes,
                            availabilityInfoItem.AvailableReturnBikes
                        )
                        val markerOptions = MarkerOptions()
                            .position(
                                LatLng(
                                    stationInfoItem.stationPosition.positionLat,
                                    stationInfoItem.stationPosition.positionLon
                                )
                            )
                            .title(stationInfoItem.stationName.zhTw)
                            .icon(getBitmapFromVector(context, iconId, 130, 130))

                        val marker = myGoogleMap.addMarker(markerOptions)

                        if (marker != null) {
                            googleMapMarkers.put(stationInfoItem.stationUID, marker)
                            marker.tag = stationInfoItem
                            showingMarker = marker
                            showBottomSheetDialog(
                                stationInfoItem,
                                availabilityInfoItem
                            )
                        }
                        isMoveToSearchStation = false
                    }
                })
        } else {
            Log.d(TAG, "googleMapMarkers NOT containsKey: SEARCH");
        }
    }

    private fun getBitmapFromVector(
        context: Context?,
        drawableId: Int,
        width: Int,
        height: Int
    ): BitmapDescriptor {
        var drawable = context?.let { ContextCompat.getDrawable(it, drawableId) }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable!!).mutate()
        }

        var bitmap = if (width != -1 && height != -1) {
            Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
        } else {
            Bitmap.createBitmap(
                drawable!!.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        }

        val canvas = Canvas(bitmap)
        if (drawable != null) {
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun addGoogleMapMarkers(stationInfo: StationInfo) {
        stationInfo.forEach { stationInfoItem ->
            if (googleMapMarkers.containsKey(stationInfoItem.stationUID)) {

                val distance = getStationDistanceF(
                    LatLng(
                        stationInfoItem.stationPosition.positionLat,
                        stationInfoItem.stationPosition.positionLon
                    )
                )
                if (distance > 2000) {
                    googleMapMarkers.get(stationInfoItem.stationUID)?.remove()
                    googleMapMarkers.remove(stationInfoItem.stationUID)
                } else {
                    // update marker
                    val availabilityInfoItem = findAvailableInfoItem(stationInfoItem.stationUID)
                    availabilityInfoItem?.let {
                        val iconId = getRateIcon(it.AvailableRentBikes, it.AvailableReturnBikes)
                        var marker = googleMapMarkers.get(stationInfoItem.stationUID)
                        Log.d(TAG, "00000")

                        if (marker != showingMarker)
                            marker?.setIcon(getBitmapFromVector(context, iconId, -1, -1))
                    }
                }
            } else {
                // add
                val availabilityInfoItem = findAvailableInfoItem(stationInfoItem.stationUID)
                availabilityInfoItem?.let {
                    val iconId = getRateIcon(it.AvailableRentBikes, it.AvailableReturnBikes)
                    val markerOptions = MarkerOptions()
                        .position(
                            LatLng(
                                stationInfoItem.stationPosition.positionLat,
                                stationInfoItem.stationPosition.positionLon
                            )
                        )
                        .title(stationInfoItem.stationName.zhTw)
                        .icon(getBitmapFromVector(context, iconId, -1, -1))

                    val marker = myGoogleMap.addMarker(markerOptions)

                    if (marker != null) {
                        googleMapMarkers.put(stationInfoItem.stationUID, marker)
                        marker.tag = stationInfoItem
                    }
                }
            }
        }


        // check current marker's distance
        val iterator = googleMapMarkers.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val distance = getStationDistanceF(
                LatLng(
                    entry.value.position.latitude,
                    entry.value.position.longitude
                )
            )
            if (distance > 2000) {
                entry.value.remove()
                iterator.remove()
            }
        }

        // check selected or not
        if (isMoveToSelectedStation && googleMapMarkers.containsKey(selectStationUid) && !isMoveToSearchStation) {
            val marker = googleMapMarkers.get(selectStationUid)
            Log.d(TAG, "Find selected station! ")
            // move to select station
            if (marker != null) {
                myGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    marker.position,
                    16f
                ), 500,
                    object : GoogleMap.CancelableCallback {
                        override fun onCancel() {

                        }

                        override fun onFinish() {
                            findAvailableInfoItem(selectStationUid)?.let { availabilityInfoItem ->
                                showingMarker = marker
                                showBottomSheetDialog(
                                    marker.tag as StationInfoItem,
                                    availabilityInfoItem
                                )
                                isMoveToSelectedStation = false
                            }
                        }
                    })
            } else {
                Log.d(TAG, "SELECT MARKET IS NULL");
            }
        } else {
            Log.d(TAG, "googleMapMarkers NOT containsKey");
        }


        mapCircle?.remove()

        mapCircle =
            myGoogleMap.addCircle(
                CircleOptions().center(currentLatLng).radius(2000.0)
                    .strokeColor(Color.parseColor("#96CCCCCC")).strokeWidth(5f)
                    .fillColor(Color.parseColor("#96CCCCCC"))
            )

    }

    private fun updateGoogleMapMarkers(availabilityInfo: AvailabilityInfo) {
        var refreshTimes = 0
        availabilityInfo.forEach { item ->
            if (googleMapMarkers.containsKey(item.StationUID)) {
                // need to update icon
                val updateMarker = googleMapMarkers.get(item.StationUID)
                val iconId = getRateIcon(
                    item.AvailableRentBikes,
                    item.AvailableReturnBikes
                )
                if (updateMarker != showingMarker)
                    updateMarker?.setIcon(getBitmapFromVector(context, iconId, -1, -1))
                refreshTimes++
            }
            Log.d(TAG, "[refreshClusterItems] refreshTimes = $refreshTimes")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "[onMapReady]")
        this.myGoogleMap = googleMap
        myGoogleMap.clear()

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

            Log.d(TAG, "Current zoom level : " + myGoogleMap.cameraPosition.zoom);

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

        this.myGoogleMap.setOnMarkerClickListener(object : GoogleMap.OnMarkerClickListener {
            override fun onMarkerClick(marker: Marker): Boolean {
                val stationInfoItem = marker.tag as StationInfoItem

                findAvailableInfoItem(stationInfoItem.stationUID)?.let {
                    showingMarker = marker
                    showBottomSheetDialog(
                        stationInfoItem,
                        it
                    )
                }
                return true
            }
        })

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
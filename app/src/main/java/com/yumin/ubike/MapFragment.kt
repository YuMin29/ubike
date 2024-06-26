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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.maps.android.SphericalUtil
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.UbikeStationWithFavorite
import com.yumin.ubike.databinding.FragmentMapBinding
import com.yumin.ubike.databinding.LayoutBottomSheetDialogBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


@AndroidEntryPoint
class MapFragment : Fragment(), LocationListener, OnMapReadyCallback {
    private val TAG = "[MapFragment]"
    private lateinit var fragmentMapBinding: FragmentMapBinding
    private lateinit var locationManager: LocationManager
    private lateinit var mapView: View
    private lateinit var myGoogleMap: GoogleMap
    private val mapViewModel: MapViewModel by activityViewModels()
    private var isDrawCurrentPosition: Boolean = false
    private lateinit var currentLocationWhenStart: LatLng
    private var ubikeType: UbikeType = UbikeType.VERSION_ALL
    private var zoomDistance: Double = 0.0
    private var isRefreshed = false
    private var isMoveToSelectedStation = false
    private var selectStationUid = ""
    private var googleMapMarkers: HashMap<String, Marker> = HashMap()
    private var isMoveToSearchStation = false
    private var showingMarker: Marker? = null
    private lateinit var receiver: BroadcastReceiver
    private var mapCircle: Circle? = null
    private var ubikeStationWithFavoriteList = listOf<UbikeStationWithFavorite>()


    enum class UbikeType constructor(val value: Int) {
        VERSION_ONE(1),
        VERSION_TWO(2),
        VERSION_ALL(0)
    }

    companion object {
        const val KEY_LONGITUDE = "longitude"
        const val KEY_LATITUDE = "latitude"
        const val KEY_UBIKE_TYPE = "type"
        const val stationRange = 1000

        // default => Taipei station
        var currentLocation: Location = Location(LocationManager.NETWORK_PROVIDER).apply {
            longitude = 25.048874128990544
            latitude = 121.513878757331
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "[onCreateView]")
        fragmentMapBinding = FragmentMapBinding.inflate(inflater)
        return fragmentMapBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // setup status bar color to transparent
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        requireActivity().window.statusBarColor = requireActivity().getColor(R.color.transparent)

        ViewCompat.getWindowInsetsController(requireActivity().window.decorView)?.apply {
            isAppearanceLightStatusBars = true
        }

        checkPermission()
    }

    private fun checkPermission() {
        if ((ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED)
        ) {
            requestLocationPermission();
        } else {
            // permission granted
            initializeMapFragment()
        }
    }

    private fun initializeMapFragment() {
        fragmentMapBinding.mapGroup.visibility = View.VISIBLE
        fragmentMapBinding.locationOff.visibility = View.GONE
        locationManager =
            requireActivity().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Get current location by network
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.0f, this)
        setupUI()
        setupReceiver()
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

    private val requestSinglePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initializeMapFragment()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.denied_location_permission),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private fun setupReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "[onReceive] intent action : " + intent?.action.toString())
                getStationInfo()
                mapViewModel.refreshAllCityStation()
                if (!isRefreshed)
                    isRefreshed = true
            }
        }
        requireContext().registerReceiver(receiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    private fun setupUI() {
        (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)
        mapView =
            (childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).requireView()

        fragmentMapBinding.ubikeAll.setOnClickListener {
            changeUbikeType(UbikeType.VERSION_ALL)
        }

        fragmentMapBinding.ubike10.setOnClickListener {
            changeUbikeType(UbikeType.VERSION_ONE)
        }

        fragmentMapBinding.ubike20.setOnClickListener {
            changeUbikeType(UbikeType.VERSION_TWO)
        }

        fragmentMapBinding.favoriteStationInfo.setOnClickListener {
            // switch to favorite fragment
            findNavController().navigate(R.id.action_map_to_favorite)
        }

        fragmentMapBinding.stationInfoListView.setOnClickListener {
            val bundle = Bundle().apply {
                putDouble(KEY_LONGITUDE, currentLocation.longitude)
                putDouble(KEY_LATITUDE, currentLocation.latitude)
                putInt(KEY_UBIKE_TYPE, ubikeType.value)
            }
            findNavController().navigate(R.id.action_map_to_list, bundle)
        }

        fragmentMapBinding.searchView.setOnClickListener {
            findNavController().navigate(R.id.action_map_to_search)
        }
    }

    private fun changeUbikeType(type: UbikeType) {
        if (type != ubikeType) {
            ubikeType = type
            clearMap()
        }
        getStationInfo()
    }

    private fun clearMap() {
        myGoogleMap.clear()
        googleMapMarkers.clear()
    }

    private fun getStationInfo() {
        mapViewModel.getNearbyStation(
            currentLocation.latitude,
            currentLocation.longitude,
            stationRange,
            ubikeType.value
        )
    }

    private fun getZoomDistance(): Double {
        val visibleRegion = myGoogleMap.projection.visibleRegion
        val distance: Double = SphericalUtil.computeDistanceBetween(
            visibleRegion.farLeft,
            myGoogleMap.cameraPosition.target
        )
        Log.d(TAG, "[getZoomDistance] DISTANCE = $distance");
        return distance
    }

    private fun observeViewModelData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mapViewModel.nearbyStation.collect {
                    Log.d(TAG, "[nearbyStationDataWithFavorite] DATA SIZE = " + it.size)
                    ubikeStationWithFavoriteList = it
                    checkGoogleMapMarkers(ubikeStationWithFavoriteList)
                    addRangeCircle()
                }
            }
        }

        lifecycleScope.launch {
            mapViewModel.selectStationUid.collect {
                Log.d(TAG, "[observeViewModelData] selectStationUid = $it")
                selectStationUid = it
                isMoveToSelectedStation = true
                if (myGoogleMap.cameraPosition.zoom < 16)
                    myGoogleMap.animateCamera(CameraUpdateFactory.zoomTo(16f))
            }
        }

        lifecycleScope.launch {
            mapViewModel.searchStationUid.collect {
                Log.d(TAG, "[observeViewModelData] searchStationUid = ${it.item.stationUID}")
                isMoveToSearchStation = true
                clearMap()
                addSingleMarker(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // release here
        Log.d(TAG, "[onDestroyView]")
        clearMap()
        requireContext().unregisterReceiver(receiver)
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

    private val convertTime: (time: String) -> Calendar = { time ->
        val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+08:00")
        val calendar = Calendar.getInstance().apply {
            setTime(simpleDateFormat.parse(time))
        }
        calendar
    }

    private fun showBottomSheetDialog(ubikeStationWithFavorite: UbikeStationWithFavorite) {
        // scale icon bitmap
        val iconId = getRateIcon(
            ubikeStationWithFavorite.item.availableRentBikes,
            ubikeStationWithFavorite.item.availableReturnBikes
        )
        showingMarker?.setIcon(getBitmapFromVector(iconId, 130, 130))

        val stationNameSplitList = ubikeStationWithFavorite.item.stationName.zhTw.split("_")
        var isFavorite = false

        val dialogView = LayoutBottomSheetDialogBinding.inflate(layoutInflater).apply {
            // show update time
            var currentTimeDate = Calendar.getInstance().time
            var diff =
                (currentTimeDate.time - convertTime(ubikeStationWithFavorite.item.updateTime).time.time) / 1000
            updateTime.text = diff.toInt().toString() + getString(R.string.updated_string)
            Log.d(TAG, "[showBottomSheetDialog] diff = " + diff)

            // show station distance
            val stationLatLng = LatLng(
                ubikeStationWithFavorite.item.stationPosition.positionLat,
                ubikeStationWithFavorite.item.stationPosition.positionLon
            )
            val distance = getStationDistance(stationLatLng)
            distanceTextView.text = "距離" + distance
            Log.d(
                TAG,
                "[showBottomSheetDialog] show distance = " + getStationDistance(stationLatLng)
            )
            stationName.text = stationNameSplitList[1]
            stationAddress.text = ubikeStationWithFavorite.item.stationAddress.zhTw
            type.apply {
                text = stationNameSplitList[0]

                background = if (ubikeStationWithFavorite.item.serviceType == 1)
                    context.getDrawable(R.drawable.ubike_type_bg)
                else
                    context.getDrawable(R.drawable.ubike_type2_bg)
            }
            availableRent.text =
                ubikeStationWithFavorite.item.availableRentBikes.toString() + "可借"
            availableReturn.text =
                ubikeStationWithFavorite.item.availableReturnBikes.toString() + "可還"
            share.setOnClickListener {
                val sendIntent: Intent = Intent().apply {
                    val mapUri =
                        "https://www.google.com/maps/dir/?api=1&destination=" + ubikeStationWithFavorite.item.stationPosition.positionLat + "," + ubikeStationWithFavorite.item.stationPosition.positionLon
                    action = Intent.ACTION_SEND
                    putExtra(
                        Intent.EXTRA_TEXT,
                        stationNameSplitList[1] + "有" + ubikeStationWithFavorite.item.availableRentBikes.toString() + "可借" +
                                ubikeStationWithFavorite.item.availableReturnBikes.toString() + "可還"
                                + "，地點在$mapUri"
                    )
                    type = "text/plain"
                }
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            }
            navigate.setOnClickListener {
                val gmmIntentUri =
                    Uri.parse("google.navigation:q=" + ubikeStationWithFavorite.item.stationPosition.positionLat + "," + ubikeStationWithFavorite.item.stationPosition.positionLon + "&mode=w")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }

            // check if favorite station
            if (ubikeStationWithFavorite.isFavorite) {
                favorite.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_favorite_24),
                    null, null
                )
                isFavorite = true
            }

            favorite.setOnClickListener {
                if (isFavorite) {
                    isFavorite = false
                    mapViewModel.removeFromFavoriteList(ubikeStationWithFavorite.item.stationUID)

                    favorite.setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_baseline_favorite_border_24
                        ), null, null
                    )
                } else {
                    isFavorite = true
                    mapViewModel.addToFavoriteList(ubikeStationWithFavorite.item.stationUID)


                    favorite.setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.ic_baseline_favorite_24
                        ), null, null
                    )
                }
            }
        }

        BottomSheetDialog(requireContext(), R.style.Theme_NoWiredStrapInNavigationBar).apply {
            setOnCancelListener {
                // dismiss
                Log.d(TAG, "[Dialog] dismiss")
                // scale icon bitmap
                val iconId = getRateIcon(
                    ubikeStationWithFavorite.item.availableRentBikes,
                    ubikeStationWithFavorite.item.availableReturnBikes
                )
                showingMarker?.setIcon(getBitmapFromVector(iconId))
                showingMarker = null
            }
            setCancelable(true)
            setContentView(dialogView.root)
            show()
        }
    }

    private fun getStationDistance(stationLatLng: LatLng): String {
        var stationLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = stationLatLng.latitude
            longitude = stationLatLng.longitude
        }

        val distance = stationLocation.distanceTo(myGoogleMap.myLocation)

        return if (distance > 1000) {
            "%.2f".format(distance / 1000).toString() + getString(R.string.km)
        } else {
            distance.toInt().toString() + getString(R.string.meter)
        }
    }

    private fun getStationDistanceF(stationLatLng: LatLng): Float {
        var stationLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = stationLatLng.latitude
            longitude = stationLatLng.longitude
        }
        return stationLocation.distanceTo(currentLocation)
    }

    private fun getRateIcon(availableRent: Int, availableReturn: Int): Int {
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

    private fun addSingleMarker(ubikeStationWithFavorite: UbikeStationWithFavorite) {
        // move to search station
        if (isMoveToSearchStation && !isMoveToSelectedStation) {
            Log.d(
                TAG,
                "Find SEARCH station! position = " + ubikeStationWithFavorite.item.stationPosition.toString()
            )
            myGoogleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(
                        ubikeStationWithFavorite.item.stationPosition.positionLat,
                        ubikeStationWithFavorite.item.stationPosition.positionLon
                    ), 16f
                ), 1000,
                object : GoogleMap.CancelableCallback {
                    override fun onCancel() {}

                    override fun onFinish() {
                        val iconId = getRateIcon(
                            ubikeStationWithFavorite.item.availableRentBikes,
                            ubikeStationWithFavorite.item.availableReturnBikes
                        )
                        val markerOptions = MarkerOptions()
                            .position(
                                LatLng(
                                    ubikeStationWithFavorite.item.stationPosition.positionLat,
                                    ubikeStationWithFavorite.item.stationPosition.positionLon
                                )
                            )
                            .title(ubikeStationWithFavorite.item.stationName.zhTw)
                            .icon(getBitmapFromVector(iconId, 130, 130))

                        myGoogleMap.addMarker(markerOptions)?.let {
                            googleMapMarkers.put(ubikeStationWithFavorite.item.stationUID, it)
                            it.tag = ubikeStationWithFavorite
                            showingMarker = it
                            showBottomSheetDialog(ubikeStationWithFavorite)
                        }
                        isMoveToSearchStation = false
                    }
                })
        } else {
            Log.d(TAG, "googleMapMarkers NOT containsKey: SEARCH");
        }
    }

    private fun getBitmapFromVector(
        drawableId: Int,
        width: Int = 100,
        height: Int = 100
    ): BitmapDescriptor {
        var drawable = ContextCompat.getDrawable(requireContext(), drawableId)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable!!).mutate()
        }

        var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(bitmap)

        drawable?.let {
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // TODO 20230605 修改這個
    private fun checkGoogleMapMarkers(ubikeStationWithFavoriteList: List<UbikeStationWithFavorite>) {
        ubikeStationWithFavoriteList.forEach { it ->
            val existingMarker = googleMapMarkers.containsKey(it.item.stationUID)

            if (existingMarker) {
                updateExistingMarker(it)
            } else {
                // add
                addNewMarker(it)
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
            if (distance > stationRange) {
                entry.value.remove()
                iterator.remove()
            }
        }

        // check selected or not
        moveToSelectedMarker()
    }

    private fun moveToSelectedMarker() {
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
                        override fun onCancel() {}

                        override fun onFinish() {
                            showingMarker = marker
                            showBottomSheetDialog(marker.tag as UbikeStationWithFavorite)
                            isMoveToSelectedStation = false
                        }
                    })
            } else {
                Log.d(TAG, "SELECT MARKET IS NULL");
            }
        } else {
            Log.d(TAG, "googleMapMarkers NOT containsKey");
        }
    }

    private fun addRangeCircle() {
        mapCircle?.remove()
        mapCircle = myGoogleMap.addCircle(
            CircleOptions().center(LatLng(currentLocation.latitude, currentLocation.longitude))
                .radius(stationRange.toDouble())
                .strokeColor(requireContext().getColor(R.color.map_circle)).strokeWidth(5f)
                .fillColor(requireContext().getColor(R.color.map_circle))
        )
    }

    private fun addNewMarker(ubikeStationWithFavorite: UbikeStationWithFavorite) {
        val iconId = getRateIcon(
            ubikeStationWithFavorite.item.availableRentBikes,
            ubikeStationWithFavorite.item.availableReturnBikes
        )
        val markerOptions = MarkerOptions()
            .position(
                LatLng(
                    ubikeStationWithFavorite.item.stationPosition.positionLat,
                    ubikeStationWithFavorite.item.stationPosition.positionLon
                )
            )
            .title(ubikeStationWithFavorite.item.stationName.zhTw)
            .icon(getBitmapFromVector(iconId))

        val marker = myGoogleMap.addMarker(markerOptions)
        marker?.let {
            googleMapMarkers.put(ubikeStationWithFavorite.item.stationUID, it)
            it.tag = ubikeStationWithFavorite
        }
    }

    private fun updateExistingMarker(ubikeStationWithFavorite: UbikeStationWithFavorite) {
        val distance = getStationDistanceF(
            LatLng(
                ubikeStationWithFavorite.item.stationPosition.positionLat,
                ubikeStationWithFavorite.item.stationPosition.positionLon
            )
        )

        if (distance > stationRange) {
            googleMapMarkers.get(ubikeStationWithFavorite.item.stationUID)?.remove()
            googleMapMarkers.remove(ubikeStationWithFavorite.item.stationUID)
        } else {
            // update marker
            val iconId = getRateIcon(
                ubikeStationWithFavorite.item.availableRentBikes,
                ubikeStationWithFavorite.item.availableReturnBikes
            )
            var marker = googleMapMarkers.get(ubikeStationWithFavorite.item.stationUID)
            if (marker != showingMarker)
                marker?.setIcon(getBitmapFromVector(iconId))
            marker?.tag = ubikeStationWithFavorite
            Log.d(
                TAG,
                "[updateExistingMarker] Marker name = ${ubikeStationWithFavorite.item.stationName.zhTw} updatetime = ${ubikeStationWithFavorite.item.updateTime}"
            )
        }
    }

    private fun refreshMapMarkers(availabilityInfo: AvailabilityInfo) {
        var refreshTimes = 0
        availabilityInfo.forEach { item ->
            if (googleMapMarkers.containsKey(item.StationUID)) {
                Log.d(TAG, "[refreshClusterItems] containsKey = ${item.StationUID}")
                // need to update icon
                val updateMarker = googleMapMarkers.get(item.StationUID)
                val iconId = getRateIcon(item.AvailableRentBikes, item.AvailableReturnBikes)
                if (updateMarker != showingMarker)
                    updateMarker?.setIcon(getBitmapFromVector(iconId))
                refreshTimes++
            }
            Log.d(TAG, "[refreshClusterItems] refreshTimes = $refreshTimes")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "[onMapReady]")
        this.myGoogleMap = googleMap
        this.myGoogleMap.isMyLocationEnabled = true

        this.myGoogleMap.apply {
            setOnCameraIdleListener {
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

                currentLocation = Location(LocationManager.NETWORK_PROVIDER).apply {
                    latitude = myGoogleMap.cameraPosition.target.latitude
                    longitude = myGoogleMap.cameraPosition.target.longitude
                }
                Log.d(
                    TAG,
                    "[setOnCameraIdleListener] currentLocation = ${currentLocation.latitude},${currentLocation.longitude} " + ", distance : ${zoomDistance.toInt()}"
                )

                getStationInfo()

                Log.d(
                    TAG,
                    "[setOnCameraIdleListener] lat : ${currentLocation.latitude}, " + "lon : ${currentLocation.longitude}, distance : ${zoomDistance.toInt()}"
                )
            }

            setOnMarkerClickListener(object : GoogleMap.OnMarkerClickListener {
                override fun onMarkerClick(marker: Marker): Boolean {
                    val ubikeStationWithFavorite = marker.tag as UbikeStationWithFavorite
                    showingMarker = marker
                    showBottomSheetDialog(ubikeStationWithFavorite)
                    return true
                }
            })
        }

        // show current location button
        val myLocationButton =
            (mapView.findViewById<View>(Integer.parseInt("1")).parent as View).findViewById<View>(
                Integer.parseInt("2")
            )
        (myLocationButton.layoutParams as (RelativeLayout.LayoutParams)).apply {
            // set current location button position on right bottom
            addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
            setMargins(0, 0, 30, 30)
        }
        observeViewModelData()
    }
}
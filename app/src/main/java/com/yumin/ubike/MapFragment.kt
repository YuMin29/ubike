package com.yumin.ubike

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.maps.android.SphericalUtil
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentMapBinding
import com.yumin.ubike.repository.RemoteRepository


/**
 * MapFragment responsible for show map
 */
class MapFragment : Fragment(), LocationListener {
    private val TAG: String = "[MapFragment]"
    private lateinit var fragmentMapBinding: FragmentMapBinding
    private lateinit var locationManager: LocationManager
    private lateinit var mapView: View
    private lateinit var mMap: GoogleMap
    private lateinit var mapViewModel: MapViewModel
    private val remoteRepository = RemoteRepository()
    private var isDrawCurrentPosition: Boolean = false
    private lateinit var currentLocationWhenStart: LatLng
    private var availableList: ArrayList<AvailabilityInfoItem> = ArrayList()
    private var ubikeType: Int = 0 // 0 -> all
    private var stationMarkerMap : HashMap<String,Marker> = HashMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentMapBinding = FragmentMapBinding.inflate(inflater)

        locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Get current location by network
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.0f, this)


        // Initialize view model
        mapViewModel = MapViewModel(remoteRepository, requireContext())

        // Initialize map fragment
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(object : OnMapReadyCallback {
            override fun onMapReady(googleMap: GoogleMap) {
                Log.d(TAG, "[onMapReady]")
                mMap = googleMap
                mMap.isMyLocationEnabled = true
                mMap.setOnMarkerClickListener(object : GoogleMap.OnMarkerClickListener {
                    override fun onMarkerClick(marker: Marker): Boolean {
                        // open bottom sheet dialog
                        showBottomSheetDialog(marker)
                        return true
                    }
                })

                mMap.setOnCameraMoveStartedListener {
                    Log.d(TAG, "[setOnCameraMoveStartedListener]")
                }

                mMap.setOnCameraMoveCanceledListener {
                    Log.d(TAG, "[setOnCameraMoveCanceledListener]")
                }

                mMap.setOnCameraIdleListener {
                    val animation = AnimationUtils.loadAnimation(context, R.anim.position_animation)
                    animation.setAnimationListener(object : Animation.AnimationListener {
                        override fun onAnimationStart(animation: Animation?) {
                            fragmentMapBinding.tempUse.visibility = View.VISIBLE
                        }

                        override fun onAnimationEnd(animation: Animation?) {
                            fragmentMapBinding.tempUse.visibility = View.INVISIBLE
                        }

                        override fun onAnimationRepeat(animation: Animation?) {
                        }
                    })
                    fragmentMapBinding.tempUse.startAnimation(animation)
                    // Query station from view model
                    // TODO: 20230115
                    // 取得目前的ZOOM LEVEL，算出目前畫面的範圍，再利用near by 去取得範圍內的站點資訊(最大範圍:1km)

                    val distance = calculateDistance()
                    val currentLatLng = mMap.cameraPosition.target

                    Log.d(
                        TAG,
                        "[setOnCameraIdleListener] current position = " + mMap.cameraPosition.toString()
                    )
                    Log.d(
                        TAG,
                        "[setOnCameraIdleListener] currentLatLng = ${currentLatLng.latitude},${currentLatLng.longitude}"
                    )

                    mapViewModel.getStationInfoNearBy(
                        currentLatLng.latitude,
                        currentLatLng.longitude,
                        distance.toInt(),
                        ubikeType
                    )
                    mapViewModel.getAvailabilityNearBy(
                        currentLatLng.latitude,
                        currentLatLng.longitude,
                        distance.toInt(),
                        ubikeType
                    )
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
        })

        mapView = mapFragment.requireView()

        initButton()

        observeViewModelData()

        return fragmentMapBinding.root
    }

    private fun initButton() {
        fragmentMapBinding.ubikeAll.setOnClickListener {
            ubikeType = 0
            stationMarkerMap.clear()
            mMap?.clear()
            mapViewModel.getUbikeAvailabilityByType(ubikeType)
            mapViewModel.getUbikeInfoByType(ubikeType)
        }

        fragmentMapBinding.ubike10.setOnClickListener {
            ubikeType = 1
            stationMarkerMap.clear()
            mMap?.clear()
            mapViewModel.getUbikeAvailabilityByType(ubikeType)
            mapViewModel.getUbikeInfoByType(ubikeType)
        }

        fragmentMapBinding.ubike20.setOnClickListener {
            ubikeType = 2
            stationMarkerMap.clear()
            mMap?.clear()
            // update map marker
            mapViewModel.getUbikeAvailabilityByType(ubikeType)
            mapViewModel.getUbikeInfoByType(ubikeType)
        }

        fragmentMapBinding.favoriteStationInfo.setOnClickListener { TODO("Switch to favorite fragment") }

        fragmentMapBinding.stationInfoListView.setOnClickListener {
            // 切換fragment
            fragmentManager?.beginTransaction()
                ?.replace(R.id.frame_layout, StationInfoListFragment())?.commit()
        }
    }

    private fun calculateDistance(): Double {
        val visibleRegion = mMap.projection.visibleRegion
        val distance: Double = SphericalUtil.computeDistanceBetween(
            visibleRegion.farLeft, mMap.cameraPosition.target
        )
        Log.d(TAG, "[calculateDistance] DISTANCE = $distance");
        return distance
    }

    private fun observeViewModelData() {
        mapViewModel.stationWholeInfo.observe(viewLifecycleOwner, Observer { stationWholeInfo ->
            Log.d(TAG,"[observeViewModelData] stationWholeInfo first  : "+stationWholeInfo.first?.size)
            Log.d(TAG,"[observeViewModelData] stationWholeInfo second : "+stationWholeInfo.second?.size)
            if (stationWholeInfo.first?.size == stationWholeInfo.second?.size) {
                stationWholeInfo.second?.let { availableValue -> availableList = availableValue }
                stationWholeInfo.first?.let { stationValue -> createStationMarkerList(stationValue) }
            }
        })
    }

    private fun createStationMarkerList(stationInfo: StationInfo) {
        Log.d(TAG,"[createStationMarkerList]")
        stationInfo.iterator().forEach { infoItem ->

            // if stationMarkerMap already have this station info marker, return
            if (stationMarkerMap.containsKey(infoItem.stationUID)) {
                Log.d(TAG,"[createStationMarkerList] station name = ${infoItem.stationName}, " +
                        "station UID = ${infoItem.stationUID}")
                return@forEach
            }

            val stationName = infoItem.stationName.zhTw.split("_")[1]
            val availableRent = findAvailableRent(infoItem.stationID)
            val availableReturn = findAvailableReturn(infoItem.stationID)
            getRateIcon(availableRent, availableReturn)
            val markerOptions = MarkerOptions()
                .position(
                    LatLng(
                        infoItem.stationPosition.positionLat,
                        infoItem.stationPosition.positionLon
                    )
                )
                .title("$stationName\n可借")
                .icon(
                    getBitmapFromVectorDrawable(
                        requireContext(),
                        getRateIcon(availableRent, availableReturn)
                    )
                )

            val marker = mMap.addMarker(markerOptions)

            if (marker != null) {
                marker.tag = infoItem
                stationMarkerMap[infoItem.stationUID] = marker
            }
        }
    }

    private fun getBitmapFromVectorDrawable(context: Context?, drawableId: Int): BitmapDescriptor {
        var drawable = ContextCompat.getDrawable(requireContext(), drawableId)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            drawable = DrawableCompat.wrap(drawable!!).mutate()
        }
        val bitmap = Bitmap.createBitmap(
            drawable!!.intrinsicWidth,
            drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // release here
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "LOCATION = " + location.latitude + "," + location.longitude)

        if (!isDrawCurrentPosition) {
            isDrawCurrentPosition = true
            currentLocationWhenStart = LatLng(location.latitude, location.longitude)
            // move to current position
            mMap?.moveCamera(CameraUpdateFactory.newLatLng(currentLocationWhenStart))
//            mapViewModel.getStationInfo("NewTaipei")
//            mapViewModel.getAvailabilityByCity("NewTaipei")
            //  1.應該根據目前的經緯度區分是哪個縣市，再呼叫view model的loadStationInfo
        }
    }

    private fun showBottomSheetDialog(marker: Marker) {
        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_NoWiredStrapInNavigationBar)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_dialog_layout, null)
        val stationInfoItem = marker.tag as StationInfoItem
        val stationNameByTag = stationInfoItem.stationName.zhTw.split("_")
        val stationId = stationInfoItem.stationID

        val stationName = view.findViewById<TextView>(R.id.station_name)
        stationName.text = stationNameByTag[1]

        val stationAddress = view.findViewById<TextView>(R.id.station_address)
        stationAddress.text = stationInfoItem.stationAddress.zhTw

        val type = view.findViewById<TextView>(R.id.type)
        type.text = stationNameByTag[0]

        val availableRent = view.findViewById<TextView>(R.id.available_rent)
        availableRent.text = findAvailableRent(stationId).toString() + "可借"

        val availableReturn = view.findViewById<TextView>(R.id.available_return)
        availableReturn.text = findAvailableReturn(stationId).toString() + "可還"

        dialog.setCancelable(true)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun getRateIcon(availableRent: Int, availableReturn: Int): Int {
        Log.d(TAG, "[getRateIcon] availableRent = $availableRent ,availableReturn = $availableReturn")

        if (availableRent == 0 && availableReturn == 0)
            return R.drawable.ic_ubike_icon_0

        val availableRate =
            ((availableRent.toFloat() / (availableRent.toFloat() + availableReturn.toFloat())) * 100).toInt()
//        Log.d(TAG, "[getRateIcon] availableRate = $availableRate")

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

    private fun findAvailableRent(stationId: String): Int {
        for (item in availableList) {
            if (item.StationID == stationId)
                return item.AvailableRentBikes
        }
        return 0
    }

    private fun findAvailableReturn(stationId: String): Int {
        for (item in availableList) {
            if (item.StationID == stationId)
                return item.AvailableReturnBikes
        }
        return 0
    }
}
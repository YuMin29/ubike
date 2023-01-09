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
    private var mMap: GoogleMap? = null
    private lateinit var mapViewModel: MapViewModel
    private val remoteRepository = RemoteRepository()
    private var stationMarkerList: ArrayList<Marker> = ArrayList()
    private var isDrawCurrentPosition: Boolean = false
    private lateinit var currentLocationWhenStart: LatLng
    private var availableList: ArrayList<AvailabilityInfoItem> = ArrayList()
    private lateinit var stationInfoList: StationInfo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        fragmentMapBinding = FragmentMapBinding.inflate(inflater)

        //
        locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Get current location by GPS
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.0f, this)
        //Get current location by network

        // Initialize view model
        mapViewModel = MapViewModel(remoteRepository, requireContext())

        // Initialize google map
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(object : OnMapReadyCallback {
            override fun onMapReady(googleMap: GoogleMap) {
                Log.d(TAG, "[onMapReady]")
                mMap = googleMap
                mMap?.isMyLocationEnabled = true
                mMap?.setOnMarkerClickListener(object : GoogleMap.OnMarkerClickListener {
                    override fun onMarkerClick(marker: Marker): Boolean {
                        // open bottom sheet dialog
                        showBottomSheetDialog(marker)
                        return false
                    }
                })

                val myLocationButton =
                    (mapView.findViewById<View>(Integer.parseInt("1")).parent as View)
                        .findViewById<View>(Integer.parseInt("2"))
                val rlp = myLocationButton.layoutParams as (RelativeLayout.LayoutParams)
                // position on right bottom
                rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0)
                rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                rlp.setMargins(0, 0, 30, 30)
            }
        })

        // TODO 應該要取得map移動的事件，再即時更新地圖
        // 取得地圖目前可視範圍內的經緯度距離，從stationInfoList找出站點並refresh fragment

        // 計算地圖zoom level的距離
        // 1.利用站點經緯度+目前位置的經緯度算出距離
        // 2.要取得目前map camera scale的距離(可能與zoom level有關?)

        // 了解怎麼呼叫ubike api 取得附近距離的站點即時資訊-> nearby(25.047675, 121.517055, 1000)
        // 語法:nearby({Lat},{Lon},{DistanceInMeters}) DistanceInMeters最大搜尋半徑為1000公尺

        mapView = mapFragment.requireView()

        observeViewModelData()

        // Initialize button
        fragmentMapBinding.ubikeAll.setOnClickListener {
            stationMarkerList.clear()
            mMap?.clear()
            createStationMarkerList(stationInfoList,0)
        }

        fragmentMapBinding.ubike10.setOnClickListener {
            stationMarkerList.clear()
            mMap?.clear()
            createStationMarkerList(stationInfoList,1)
        }

        fragmentMapBinding.ubike20.setOnClickListener {
            stationMarkerList.clear()
            mMap?.clear()
            // update map marker
            createStationMarkerList(stationInfoList,2)
        }

        fragmentMapBinding.favoriteStationInfo.setOnClickListener { TODO("Switch to favorite fragment") }

        fragmentMapBinding.stationInfoListView.setOnClickListener {
            // 切換fragment
            fragmentManager?.beginTransaction()?.replace(R.id.frame_layout,StationInfoListFragment())?.commit()
        }

        return fragmentMapBinding.root
    }

    private fun observeViewModelData() {
        mapViewModel.stationInfoList.observe(viewLifecycleOwner, Observer {
            if (it.size > 0) {
                stationInfoList = it
                stationMarkerList.clear()
                mMap?.clear()

                Log.d(TAG, "[observeViewModelData] stationInfoList SIZE = " + it.size)
                createStationMarkerList(it,0)
            }
        })

        mapViewModel.availabilityInfoList.observe(viewLifecycleOwner, Observer {
            Log.d(TAG, "[observeViewModelData] availabilityInfoList SIZE = " + it.size)
            availableList = it
        })
    }

    private fun createStationMarkerList(stationInfo: StationInfo, type:Int) {
        for (infoItem in stationInfo) {
            val stationName = infoItem.stationName.zhTw.split("_")[1]
            Log.d(TAG,"[createStationMarkerList] service type = "+infoItem.serviceType)

            if (type != 0 && type != infoItem.serviceType) {
                Log.d(TAG,"[createStationMarkerList] RETURN")
                continue
            }


            val availableRent = findAvailableRent(infoItem.stationID)
            val availableReturn = findAvailableReturn(infoItem.stationID)
            getRateIcon(availableRent,availableReturn)
            val markerOptions = MarkerOptions()
                .position(
                    LatLng(
                        infoItem.stationPosition.positionLat,
                        infoItem.stationPosition.positionLon
                    )
                )
                .title("$stationName\n可借")
                .icon(getBitmapFromVectorDrawable(requireContext(), getRateIcon(availableRent,availableReturn)))

            val marker = mMap?.addMarker(markerOptions)
            if (marker != null) {
                marker.tag = infoItem
            }

            if (marker != null) {
                stationMarkerList.add(marker)
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
            mapViewModel.loadStationInfo("NewTaipei")
            mapViewModel.loadAvailabilityByCity("NewTaipei")
            //  1.應該根據目前的經緯度區分是哪個縣市，再呼叫view model的loadStationInfo

        }
    }

    private fun showBottomSheetDialog(marker: Marker) {
        val dialog = BottomSheetDialog(requireContext(),R.style.Theme_NoWiredStrapInNavigationBar)
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
        availableRent.text = findAvailableRent(stationId).toString()+"可借"

        val availableReturn = view.findViewById<TextView>(R.id.available_return)
        availableReturn.text = findAvailableReturn(stationId).toString()+"可還"

        dialog.setCancelable(true)
        dialog.setContentView(view)
        dialog.show()
    }

    private fun getRateIcon(availableRent:Int, availableReturn:Int):Int{
        Log.d(TAG, "[getRateIcon] availableRent = $availableRent ,availableReturn = $availableReturn")

        if (availableRent == 0 && availableReturn == 0)
            return R.drawable.ic_ubike_icon_0

        val availableRate = ((availableRent.toFloat() / (availableRent.toFloat() + availableReturn.toFloat())) * 100).toInt()
        Log.d(TAG, "[createStationMarkerList] availableRate = $availableRate")

        if (availableRate == 0)
            return R.drawable.ic_ubike_icon_0
        else if (availableRate <= 10)
            return R.drawable.ic_ubike_icon_10
        else if (availableRate <= 20)
            return R.drawable.ic_ubike_icon_20
        else if (availableRate <= 30)
            return R.drawable.ic_ubike_icon_30
        else if (availableRate <= 40)
            return R.drawable.ic_ubike_icon_40
        else if (availableRate <= 50)
            return R.drawable.ic_ubike_icon_50
        else if (availableRate <= 60)
            return R.drawable.ic_ubike_icon_60
        else if (availableRate <= 70)
            return R.drawable.ic_ubike_icon_70
        else if (availableRate <= 80)
            return R.drawable.ic_ubike_icon_80
        else if (availableRate <= 90)
            return R.drawable.ic_ubike_icon_90
        else
            return R.drawable.ic_ubike_icon_100
    }

    private fun findAvailableRent(stationId:String):Int {
        for (item in availableList) {
            if (item.StationID == stationId)
                return item.AvailableRentBikes
        }
        return 0
    }

    private fun findAvailableReturn(stationId: String):Int {
        for (item in availableList) {
            if (item.StationID == stationId)
                return item.AvailableReturnBikes
        }
        return 0
    }
}
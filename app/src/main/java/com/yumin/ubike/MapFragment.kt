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
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.databinding.FragmentMapBinding
import com.yumin.ubike.repository.RemoteRepository

class MapFragment : Fragment(), LocationListener {
    private val TAG: String = "[MapFragment]"
    private lateinit var fragmentMapBinding: FragmentMapBinding
    private lateinit var locationManager: LocationManager
    private lateinit var mapView: View
    private var mMap: GoogleMap? = null
    private lateinit var mapViewModel:MapViewModel
    private val remoteRepository = RemoteRepository()
    private var stationMarkerList: ArrayList<Marker> = ArrayList()
    private var isDrawCurrentPosition:Boolean = false
    private lateinit var currentLocationWhenStart: LatLng

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentMapBinding = FragmentMapBinding.inflate(inflater)

        //
        locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Get current location by GPS
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1000,0.0f,this)
        //Get current location by network

        // Initialize view model
        mapViewModel = MapViewModel(remoteRepository, requireContext())

        // Initialize google map
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(object:OnMapReadyCallback{
            override fun onMapReady(googleMap: GoogleMap) {
                Log.d(TAG,"[onMapReady]")
                mMap = googleMap
                mMap?.isMyLocationEnabled = true

                val myLocationButton = (mapView.findViewById<View>(Integer.parseInt("1")).parent as View)
                    .findViewById<View>(Integer.parseInt("2"))
                val rlp = myLocationButton.layoutParams as (RelativeLayout.LayoutParams)
                // position on right bottom
                rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP,0)
                rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                rlp.setMargins(0,0,30,30)

                // 計算地圖zoom level的距離
                // 了解怎麼呼叫ubike api 取得附近距離的站點即時資訊
            }
        })
        mapView = mapFragment.requireView()

        observeViewModelData()
        return fragmentMapBinding.root
    }

    private fun observeViewModelData(){
        mapViewModel.stationInfoList.observe(viewLifecycleOwner, Observer {
            if (it.size > 0) {
                stationMarkerList.clear()
                mMap?.clear()

                Log.d(TAG,"[getViewModelData] SIZE = "+it.size)
                createStationMarkerList(it)
            }
        })
    }

    private fun createStationMarkerList(stationInfo: StationInfo){
        for (infoItem in stationInfo) {
            val markerOptions = MarkerOptions()
                .position(LatLng(infoItem.stationPosition.positionLat,infoItem.stationPosition.positionLon))
                .title(infoItem.stationName.zhTw)
                .icon(getBitmapFromVectorDrawable(requireContext(),R.drawable.ubike_location_24))

            val marker = mMap?.addMarker(markerOptions)

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
        Log.d(TAG,"LOCATION = "+location.latitude+","+location.longitude)

        if (!isDrawCurrentPosition) {
            isDrawCurrentPosition = true
            currentLocationWhenStart = LatLng(location.latitude, location.longitude)
            // move to current position
            mMap?.moveCamera(CameraUpdateFactory.newLatLng(currentLocationWhenStart))
        }
    }
}
package com.yumin.ubike

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.databinding.ActivityMapsBinding
import com.yumin.ubike.repository.RemoteRepository

class MapsActivity : AppCompatActivity(), OnMapReadyCallback , LocationListener{
    private val TAG: String = "[MapsActivity]"
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var layout: View
    private lateinit var locationManager: LocationManager
    private lateinit var currentLocationWhenStart: LatLng
    private var stationMarkerList: ArrayList<Marker> = ArrayList()
    private lateinit var mapViewModel: MapViewModel
    private val remoteRepository = RemoteRepository()

    override fun onStart() {
        super.onStart()
        // request location runtime permissions
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        layout = binding.root
        setContentView(layout)

        // hide action bar
        supportActionBar?.hide()

        mapViewModel = MapViewModel(remoteRepository,baseContext)

        initViewModelData()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        //Get current location by GPS
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1000,0.0f,this)
        //Get current location by network

        //If both GPS and network not work, show can't get location warning

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun initViewModelData(){
        mapViewModel.stationInfoList.observe(this, Observer {
            if (it.size > 0) {
                Log.d(TAG,"[getViewModelData] SIZE = "+it.size)
                createStationMarkerList(it)
            }
        })
    }

    private fun createStationMarkerList(stationInfo: StationInfo){
        for (infoItem in stationInfo) {
            val marker = mMap.addMarker(MarkerOptions()
                .position(LatLng(infoItem.stationPosition.positionLat,infoItem.stationPosition.positionLon))
                .title(infoItem.stationName.zhTw))
            if (marker != null) {
                stationMarkerList.add(marker)
            }
        }
    }

    private fun checkPermissions() {
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) ||
            (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_DENIED)) {
            // one of the permission is denied

            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.d(TAG, "未取得權限@")
                // show dialog to explain why this app need these permissions?
                showAlertDialog()
            } else {
                // ask user to require permission
                requestPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        } else {
            // get all permission granted
            Log.d(TAG, "已取得權限@")
        }
    }

    private fun showAlertDialog(){
       val builder: AlertDialog.Builder? = this?.let {
            AlertDialog.Builder(it)
        }
        builder?.setMessage(R.string.dialog_message)
            ?.setTitle(R.string.dialog_title)

        builder?.setPositiveButton(R.string.dialog_ok) { dialog, which ->
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        builder?.setNeutralButton(R.string.dialog_cancel,null)

        val dialog: AlertDialog? = builder?.create()
        dialog?.show()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(TAG, "[requestPermissionLauncher] ${it.key} = ${it.value}")
                if (!it.value) {
                    // permission denied
                    // show dialog to request permissions
                    Log.d(TAG, "[requestPermissionLauncher] ${it.key} permission denied")
                    showAlertDialog()
                }
            }
        }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG,"[onMapReady]")
        mMap = googleMap
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG,"LOCATION = "+location.latitude+","+location.longitude)
        currentLocationWhenStart = LatLng(location.latitude, location.longitude)
        mMap.addMarker(MarkerOptions().position(currentLocationWhenStart).title("Current location"))
        // move to current position
        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLocationWhenStart))
    }
}
package com.yumin.ubike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
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
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.ui.IconGenerator
import com.yumin.ubike.data.AvailabilityInfo
import com.yumin.ubike.data.AvailabilityInfoItem
import com.yumin.ubike.data.StationInfo
import com.yumin.ubike.data.StationInfoItem
import com.yumin.ubike.databinding.FragmentMapBinding
import com.yumin.ubike.repository.RemoteRepository
import java.util.*


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
    private lateinit var clusterManager: ClusterManager<StationClusterItem>
    private var clusterItemMap : HashMap<String,StationClusterItem> = HashMap()
    private lateinit var currentLatLng:LatLng
    private var currentDistance:Double = 0.0
    private lateinit var latestRefreshTime: Date
    private var isRefreshed = false

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

                setUpClusterManager()

                mMap.isMyLocationEnabled = true

                mMap.setOnCameraMoveStartedListener {
                    Log.d(TAG, "[setOnCameraMoveStartedListener]")
                }

                mMap.setOnCameraMoveCanceledListener {
                    Log.d(TAG, "[setOnCameraMoveCanceledListener]")
                }

                mMap.setOnCameraIdleListener {
                    Log.d(TAG,"[setOnCameraIdleListener]")
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

                    currentDistance = calculateDistance()
                    if (currentDistance > 15000) {
                        Log.d(TAG,"[setOnCameraIdleListener] Distance : $currentDistance > 1500 , return")
                        return@setOnCameraIdleListener
                    }

                    currentLatLng = mMap.cameraPosition.target
                    Log.d(TAG, "[setOnCameraIdleListener] currentLatLng = ${currentLatLng.latitude},${currentLatLng.longitude}")

                    getCurrentStationInfo()

                    Log.d(TAG,"[setOnCameraIdleListener] lat : ${currentLatLng.latitude}, " +
                            "lon : ${currentLatLng.longitude}, distance : ${currentDistance.toInt()}")

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
        })

        mapView = mapFragment.requireView()

        initButton()

        observeViewModelData()

        context?.registerReceiver(object : BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG,"[onReceive] intent action : "+intent?.action.toString())
                refreshAvailabilityData()
                // 再根據目前available list上沒有的station cluster 從 map上刪除
                // 紀錄更新時間
                if (!isRefreshed)
                    isRefreshed = true

                latestRefreshTime = Calendar.getInstance().time
            }
        }, IntentFilter(Intent.ACTION_TIME_TICK))

        return fragmentMapBinding.root
    }

    private fun refreshAvailabilityData() {
        Log.d(TAG,"[refreshAvailabilityData] lat : ${currentLatLng.latitude}, " +
                "lon : ${currentLatLng.longitude}, distance : ${currentDistance.toInt()}")
        mapViewModel.getAvailabilityNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            currentDistance.toInt(),
            ubikeType,
            true
        )
    }

    private fun initButton() {
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

        fragmentMapBinding.favoriteStationInfo.setOnClickListener { TODO("Switch to favorite fragment") }

        fragmentMapBinding.stationInfoListView.setOnClickListener {
            // 切換fragment
            fragmentManager?.beginTransaction()
                ?.replace(R.id.frame_layout, StationInfoListFragment())?.commit()
        }
    }

    private fun clearMap() {
        clusterManager.clearItems()
        clusterItemMap.clear()
    }

    private fun getCurrentStationInfo() {
        mapViewModel.getStationInfoNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            currentDistance.toInt(),
            ubikeType
        )
        mapViewModel.getAvailabilityNearBy(
            currentLatLng.latitude,
            currentLatLng.longitude,
            currentDistance.toInt(),
            ubikeType,
            false
        )
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
                stationWholeInfo.first?.let { stationValue -> addClusterItems(stationValue) }
            }
        })

        mapViewModel.refreshAvailability.observe(viewLifecycleOwner, Observer { refreshAvailability ->
            Log.d(TAG,"[observeViewModelData] refreshAvailability size : "+refreshAvailability.size)
            availableList = refreshAvailability
            // TODO : need to update cluster icon
            refreshClusterItems(refreshAvailability)
        })
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
        }
    }

    private fun showBottomSheetDialog(stationInfoItem: StationInfoItem) {
        val dialog = BottomSheetDialog(requireContext(), R.style.Theme_NoWiredStrapInNavigationBar)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_dialog_layout, null)
        val stationInfoItem = stationInfoItem
        val stationNameByTag = stationInfoItem.stationName.zhTw.split("_")
        val stationId = stationInfoItem.stationUID

        // 顯示最後更新時間
        var currentTimeDate = Calendar.getInstance().time
        var diff = currentTimeDate.time - latestRefreshTime.time
        val diffSeconds = (diff / 1000).toInt()
        Log.d(TAG, "[showBottomSheetDialog] latestRefreshTime time = ${latestRefreshTime.toString()}")
        Log.d(TAG, "[showBottomSheetDialog] currentTimeDate = ${currentTimeDate.toString()}")

        val updateTime = view.findViewById<TextView>(R.id.updateTime)
        updateTime.text = diffSeconds.toString() + "秒前更新"

        // 顯示站點距離
        val stationLatLng = LatLng(stationInfoItem.stationPosition.positionLat,stationInfoItem.stationPosition.positionLon)
        val distance = getStationDistance(stationLatLng)
        val showDistance = view.findViewById<TextView>(R.id.distance)
        showDistance.text = "距離"+distance
        Log.d(TAG,"[showBottomSheetDialog] show distance = "+getStationDistance(stationLatLng))

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

    private fun getStationDistance(stationLatLng:LatLng): String {
        var stationLocation = Location("station")
        stationLocation.latitude = stationLatLng.latitude
        stationLocation.longitude = stationLatLng.longitude
        val distance = stationLocation.distanceTo(mMap.myLocation)

        return if (distance > 1000) {
            "%.2f".format(distance/1000).toString()+"公里"
        } else
            distance.toInt().toString()+"公尺"
    }

    private fun getRateIcon(availableRent: Int, availableReturn: Int): Int {
//        Log.d(TAG, "[getRateIcon] availableRent = $availableRent ,availableReturn = $availableReturn")

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
            if (item.StationUID == stationId)
                return item.AvailableRentBikes
        }
        return 0
    }

    private fun findAvailableReturn(stationId: String): Int {
        for (item in availableList) {
            if (item.StationUID == stationId)
                return item.AvailableReturnBikes
        }
        return 0
    }

    private fun setUpClusterManager(){
        clusterManager = ClusterManager(context,mMap)
        clusterManager.renderer = StationClusterRenderer(context,mMap,clusterManager)
        mMap.setOnMarkerClickListener(clusterManager)
        mMap.setOnInfoWindowClickListener(clusterManager)
        clusterManager.setOnClusterClickListener {
            Toast.makeText(context,"[setOnClusterClickListener]",Toast.LENGTH_SHORT).show()
            true
        }
        clusterManager.setOnClusterInfoWindowClickListener {
            Toast.makeText(context,"[setOnClusterInfoWindowClickListener]",Toast.LENGTH_SHORT).show()
        }


        clusterManager.setOnClusterItemClickListener {
            Toast.makeText(context,"[setOnClusterItemClickListener]",Toast.LENGTH_SHORT).show()
            showBottomSheetDialog(it.getStationInfoItem())
            true
        }
        clusterManager.setOnClusterItemInfoWindowLongClickListener {
            Toast.makeText(context,"[setOnClusterItemInfoWindowLongClickListener]",Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshClusterItems(availabilityInfo: AvailabilityInfo){
        var refreshTimes = 0
        availabilityInfo.forEach{ item ->
            if (clusterItemMap.containsKey(item.StationUID)) {
                // need to update icon
                val updateItem = clusterItemMap.get(item.StationUID)
                val iconId = getRateIcon(findAvailableRent(item.StationUID), findAvailableReturn(item.StationUID))
                updateItem?.imageId = iconId
                val success = clusterManager.updateItem(updateItem)
                Log.d(TAG,"[refreshClusterItems] success = $success")
                refreshTimes++
            }
            clusterManager.cluster()
            Log.d(TAG,"[refreshClusterItems] refreshTimes = $refreshTimes")
        }
    }

    private fun addClusterItems(stationInfo : StationInfo) {
        var addTimes = 0
        var updateTimes = 0
        stationInfo.forEach { stationInfoItem ->
            if (clusterItemMap.containsKey(stationInfoItem.stationUID)) {
                // need to update icon
                val updateItem = clusterItemMap[stationInfoItem.stationUID]
                val iconId = getRateIcon(findAvailableRent(stationInfoItem.stationUID), findAvailableReturn(stationInfoItem.stationUID))
                updateItem?.imageId = iconId
                val success = clusterManager.updateItem(updateItem)
                updateTimes++
            } else {
                val availableRent = findAvailableRent(stationInfoItem.stationUID)
                val availableReturn = findAvailableReturn(stationInfoItem.stationUID)
                val iconId = getRateIcon(availableRent, availableReturn)

                val myItem = StationClusterItem(stationInfoItem.stationPosition.positionLat, stationInfoItem.stationPosition.positionLon, "Title ${stationInfoItem.stationName}",
                    "Snippet ${stationInfoItem.stationUID}",iconId,stationInfoItem)
                clusterManager.addItem(myItem)
                clusterItemMap.put(stationInfoItem.stationUID,myItem)
                addTimes++
            }
        }



        availableList.forEach{ availabilityInfoItem ->
            Log.d(TAG,"[addClusterItems] [update] availability item update time : ${availabilityInfoItem.UpdateTime}")
        }

        Log.d(TAG,"[addClusterItems] [update] availability SIZE : ${availableList.size}")
        Log.d(TAG,"[addClusterItems] addTimes : $addTimes, updateTimes : $updateTimes")

        clusterManager.cluster()
        clusterManager.onCameraIdle()
    }

    inner class StationClusterRenderer(context: Context?, map: GoogleMap?, clusterManager: ClusterManager<StationClusterItem>?) : DefaultClusterRenderer<StationClusterItem>(context, map, clusterManager) {
        private val iconGenerator = IconGenerator(context)
        private val imageView = ImageView(context)

        init {
            imageView.layoutParams = ViewGroup.LayoutParams(100,100)
            imageView.setPadding(2,2,2,2)
            iconGenerator.setContentView(imageView)
        }

        override fun onBeforeClusterItemRendered(item: StationClusterItem, markerOptions: MarkerOptions) {
            markerOptions.icon(getItemIcon(item)).title(item.title)
        }

        override fun onClusterItemUpdated(item: StationClusterItem, marker: Marker) {
            marker.setIcon(getItemIcon(item))
            marker.title = item.title
        }

        private fun getItemIcon(item:StationClusterItem): BitmapDescriptor {
            imageView.setImageResource(item.imageId)
            iconGenerator.setBackground(null)
            val icon  = iconGenerator.makeIcon()
            return BitmapDescriptorFactory.fromBitmap(icon)
        }
    }

    inner class StationClusterItem() : ClusterItem{
        private lateinit var position:LatLng
        private lateinit var title:String
        private lateinit var snippet:String
        private val zIndex:Float? = null
        var imageId:Int = 0
        private lateinit var stationInfoItem:StationInfoItem

        constructor(latitude:Double, longitude:Double, title:String, snippet:String, imageId:Int, stationInfoItem: StationInfoItem) : this() {
            this.position = LatLng(latitude,longitude)
            this.title = title
            this.snippet = snippet
            this.imageId = imageId
            this.stationInfoItem = stationInfoItem
        }

        override fun getPosition(): LatLng {
            return position
        }

        override fun getTitle(): String? {
            return title
        }

        override fun getSnippet(): String? {
            return snippet
        }

        override fun getZIndex(): Float? {
            return zIndex
        }

        fun getStationInfoItem(): StationInfoItem {
            return stationInfoItem
        }
    }
}
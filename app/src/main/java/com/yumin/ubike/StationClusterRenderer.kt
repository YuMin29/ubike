package com.yumin.ubike

import android.content.Context
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.ui.IconGenerator

class StationClusterRenderer(
    context: Context?,
    map: GoogleMap?,
    clusterManager: ClusterManager<StationClusterItem>?,
    val listener: Callback
) : DefaultClusterRenderer<StationClusterItem>(context, map, clusterManager) {
    private val iconGenerator = IconGenerator(context)
    private val imageView = ImageView(context)

    init {
        imageView.layoutParams = ViewGroup.LayoutParams(100, 100)
        imageView.setPadding(2, 2, 2, 2)
        iconGenerator.setContentView(imageView)
    }

    override fun onBeforeClusterItemRendered(
        item: StationClusterItem,
        markerOptions: MarkerOptions
    ) {
        markerOptions.icon(getItemIcon(item)).title(item.title)
    }

    override fun onClusterItemUpdated(item: StationClusterItem, marker: Marker) {
        marker.setIcon(getItemIcon(item))
        marker.title = item.title
    }

    private fun getItemIcon(item: StationClusterItem): BitmapDescriptor {
        imageView.setImageResource(item.imageId)
        iconGenerator.setBackground(null)
        val icon = iconGenerator.makeIcon()
        return BitmapDescriptorFactory.fromBitmap(icon)
    }

    override fun onClusterItemRendered(clusterItem: StationClusterItem, marker: Marker) {
        super.onClusterItemRendered(clusterItem, marker)
        listener.clusterItemRendered(clusterItem,marker)
    }

    interface Callback{
        fun clusterItemRendered(clusterItem: StationClusterItem, marker: Marker)
    }
}
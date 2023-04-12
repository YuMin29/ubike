package com.yumin.ubike

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yumin.ubike.databinding.ActivityMapsBinding


/**
 * MapsActivity responsible to check runtime permission
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMapsBinding
    private lateinit var layout: View

    companion object{
        private const val TAG: String = "[MapsActivity]"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "[onCreate] ")

        // request location runtime permissions
        checkPermissions()

        // Initialize view
        binding = ActivityMapsBinding.inflate(layoutInflater)
        layout = binding.root
        setContentView(layout)
    }

//    override fun onNewIntent(intent: Intent?) {
//        super.onNewIntent(intent)
//        Log.d(TAG, "[onNewIntent] ")
//        // check intent here
//        if (intent != null) {
//            val stationUid = intent.getStringExtra("StationUid")
//            Log.d(TAG, "[onNewIntent] GET intent stationUid = $stationUid")
//        }
//        setIntent(intent)
//    }

    private fun checkPermissions() {
        if ((ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_DENIED) ||
            (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_DENIED)
        ) {
            // one of the permission is denied

            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Log.d(TAG, "Permission not granted")
                // show dialog to explain why this app need these permissions?
                showAlertDialog()
            } else {
                // ask user to require permission
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            // get all permission granted
            Log.d(TAG, "Permission granted")

            if (isInternetConnected()) {
                // Initialize fragment
                val mapFragment = MapFragment()
                // Open fragment
                supportFragmentManager.beginTransaction().replace(R.id.frame_layout, mapFragment).commit()
            } else {
                Toast.makeText(this,"Please check internet!",Toast.LENGTH_LONG).show()
                finish()
                // show no internet fragment
            }
        }
    }

    fun isInternetConnected(): Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.activeNetwork != null && cm.getNetworkCapabilities(cm.activeNetwork) != null
        } else {
            cm.activeNetworkInfo != null && cm.activeNetworkInfo!!.isConnectedOrConnecting
        }
    }

    private fun showAlertDialog() {
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

        builder?.setNeutralButton(R.string.dialog_cancel, null)

        val dialog: AlertDialog? = builder?.create()
        dialog?.show()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d(TAG, "[requestPermissionLauncher] ${it.key} = ${it.value}")
                if (!it.value) {
                    // permission denied , show dialog to request permissions
                    Log.d(TAG, "[requestPermissionLauncher] ${it.key} permission denied")
                    showAlertDialog()
                } else {
                    if (isInternetConnected()) {
                        // Initialize fragment
                        val mapFragment = MapFragment()
                        // Open fragment
                        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, mapFragment).commit()
                    } else {
                        Toast.makeText(this,"Please check internet!",Toast.LENGTH_LONG).show()
                        finish()
                        // show no internet fragment
                    }
                }
            }
        }

    fun replaceStationListFragment(bundle: Bundle){
        val stationListFragment = StationListFragment()

        if (bundle != null)
            stationListFragment.arguments = bundle

        supportFragmentManager.beginTransaction().replace(R.id.frame_layout,stationListFragment)
            .addToBackStack("station_list").commit()
    }

    fun replaceSearchFragment(){
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout,SearchFragment())
            .addToBackStack("search").commit()
    }

    fun replaceFavoriteFragment(){
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout,FavoriteFragment())
            .addToBackStack("favorite").commit()
    }
}
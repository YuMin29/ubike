package com.yumin.ubike

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.yumin.ubike.databinding.ActivityMapsBinding
import com.yumin.ubike.repository.RemoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


/**
 * MapsActivity responsible to check runtime permission
 */
class MapsActivity : AppCompatActivity() {
    private val TAG: String = "[MapsActivity]"
    private lateinit var binding: ActivityMapsBinding
    private lateinit var layout: View

//    companion object {
//        lateinit var sessionManager: SessionManager
//        lateinit var repository: RemoteRepository
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "[onCreate] ")

        // request location runtime permissions
        checkPermissions()

        // Initialize view
        binding = ActivityMapsBinding.inflate(layoutInflater)
        layout = binding.root
        setContentView(layout)

        // Initialize fragment
        val mapFragment = MapFragment()
        // Open fragment
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, mapFragment).commit()

//        sessionManager = SessionManager(this)
//        repository = RemoteRepository(sessionManager)
//
//        lifecycleScope.launch(Dispatchers.IO) {
//            repository.getToken()
//        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "[onNewIntent] ")
        // check intent here
        if (intent != null) {
            val stationUid = intent.getStringExtra("StationUid")
            Log.d(TAG, "[onNewIntent] GET intent stationUid = $stationUid")
        }
        setIntent(intent)
    }

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
                }
            }
        }

    fun replaceFragment(bundle: Bundle){
        val stationListFragment = StationListFragment()

        if (bundle != null)
            stationListFragment.arguments = bundle

        supportFragmentManager.beginTransaction().replace(R.id.frame_layout,stationListFragment)
            .addToBackStack("1").commit()
    }

}
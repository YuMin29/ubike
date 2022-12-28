package com.yumin.ubike

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yumin.ubike.databinding.ActivityMapsBinding


/**
 * MapsActivity responsible to check runtime permission
 */
class MapsActivity : AppCompatActivity() {
    private val TAG: String = "[MapsActivity]"
    private lateinit var binding: ActivityMapsBinding
    private lateinit var layout: View

    override fun onStart() {
        super.onStart()
        // request location runtime permissions
        checkPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize view
        binding = ActivityMapsBinding.inflate(layoutInflater)
        layout = binding.root
        setContentView(layout)

        // Initialize fragment
        val mapFragment = MapFragment()
        // Open fragment
        supportFragmentManager.beginTransaction().replace(R.id.frame_layout, mapFragment).commit()

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
}
package io.github.andyradionov.hroutetracker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.support.design.widget.Snackbar
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task

import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mSettingsClient: SettingsClient? = null

    private var mLocationRequest: LocationRequest? = null
    private var mLocationSettingsRequest: LocationSettingsRequest? = null

    private var mLocationCallback: LocationCallback? = null

    private var mCurrentLocation: Location? = null

    private var mStartUpdatesButton: Button? = null
    private var mStopUpdatesButton: Button? = null
    private var mLastUpdateTimeTextView: TextView? = null
    private var mLatitudeTextView: TextView? = null
    private var mLongitudeTextView: TextView? = null

    private var mLatitudeLabel: String? = null
    private var mLongitudeLabel: String? = null
    private var mLastUpdateTimeLabel: String? = null

    private var mRequestingLocationUpdates: Boolean? = null

    private var mLastUpdateTime: String? = null
    private var mNeverAskPermissionShowed: Boolean = false

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        mStartUpdatesButton = findViewById(R.id.start_updates_button)
        mStopUpdatesButton = findViewById(R.id.stop_updates_button)
        mLatitudeTextView = findViewById(R.id.latitude_text)
        mLongitudeTextView = findViewById(R.id.longitude_text)
        mLastUpdateTimeTextView = findViewById(R.id.last_update_time_text)

        mLatitudeLabel = resources.getString(R.string.latitude_label)
        mLongitudeLabel = resources.getString(R.string.longitude_label)
        mLastUpdateTimeLabel = resources.getString(R.string.last_update_time_label)

        mRequestingLocationUpdates = false
        mLastUpdateTime = ""

        updateValuesFromBundle(savedInstanceState)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSettingsClient = LocationServices.getSettingsClient(this)

        createLocationCallback()
        createLocationRequest()
        buildLocationSettingsRequest()
    }

    /**
     * Updates fields based on data stored in the bundle.
     *
     * @param savedInstanceState The activity state saved in the Bundle.
     */
    private fun updateValuesFromBundle(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(KEY_REQUESTING_LOCATION_UPDATES)) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(
                        KEY_REQUESTING_LOCATION_UPDATES)
            }

            if (savedInstanceState.keySet().contains(KEY_LOCATION)) {
                mCurrentLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            }

            if (savedInstanceState.keySet().contains(KEY_LAST_UPDATED_TIME_STRING)) {
                mLastUpdateTime = savedInstanceState.getString(KEY_LAST_UPDATED_TIME_STRING)
            }
            updateUI()
        }
    }

    /**
     * Sets up the location request. Android has two location request settings:
     * `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION`. These settings control
     * the accuracy of the current location. This sample uses ACCESS_FINE_LOCATION, as defined in
     * the AndroidManifest.xml.
     *
     *
     * When the ACCESS_FINE_LOCATION setting is specified, combined with a fast update
     * interval (5 seconds), the Fused Location Provider API returns location updates that are
     * accurate to within a few feet.
     *
     *
     * These settings are appropriate for mapping applications that show real-time location
     * updates.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest()
                .setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
                .setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
    }

    private fun createLocationCallback() {
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                mCurrentLocation = locationResult!!.lastLocation
                mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
                updateLocationUI()
            }
        }
    }

    private fun buildLocationSettingsRequest() {
        mLocationSettingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest!!)
                .build()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        when (requestCode) {
            REQUEST_CHECK_SETTINGS -> when (resultCode) {
                Activity.RESULT_OK -> Log.i(TAG, "User agreed to make required location settings changes.")
                Activity.RESULT_CANCELED -> {
                    Log.i(TAG, "User chose not to make required location settings changes.")
                    mRequestingLocationUpdates = false
                    updateUI()
                }
            }
        }
    }

    fun startUpdatesButtonHandler(view: View) {
        if ((!mRequestingLocationUpdates)!!) {
            mRequestingLocationUpdates = true
            setButtonsEnabledState()
            startLocationUpdates()
        }
    }

    fun stopUpdatesButtonHandler(view: View) {
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        mSettingsClient!!.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this) {
                    Log.i(TAG, "All location settings are satisfied.")

                    mFusedLocationClient!!.requestLocationUpdates(mLocationRequest,
                            mLocationCallback!!, Looper.myLooper())

                    updateUI()
                }
                .addOnFailureListener(this) { e ->
                    val statusCode = (e as ApiException).statusCode
                    when (statusCode) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                            Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " + "location settings ")
                            try {
                                val rae = e as ResolvableApiException
                                rae.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)
                            } catch (sie: IntentSender.SendIntentException) {
                                Log.i(TAG, "PendingIntent unable to execute request.")
                            }

                        }
                        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                            val errorMessage = "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings."
                            Log.e(TAG, errorMessage)
                            Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                            mRequestingLocationUpdates = false
                        }
                    }

                    updateUI()
                }
    }

    private fun updateUI() {
        setButtonsEnabledState()
        updateLocationUI()
    }

    private fun setButtonsEnabledState() {
        if (mRequestingLocationUpdates!!) {
            mStartUpdatesButton!!.isEnabled = false
            mStopUpdatesButton!!.isEnabled = true
        } else {
            mStartUpdatesButton!!.isEnabled = true
            mStopUpdatesButton!!.isEnabled = false
        }
    }

    private fun updateLocationUI() {
        if (mCurrentLocation != null) {
            mLatitudeTextView!!.text = String.format(Locale.ENGLISH, "%s: %f", mLatitudeLabel,
                    mCurrentLocation!!.latitude)
            mLongitudeTextView!!.text = String.format(Locale.ENGLISH, "%s: %f", mLongitudeLabel,
                    mCurrentLocation!!.longitude)
            mLastUpdateTimeTextView!!.text = String.format(Locale.ENGLISH, "%s: %s",
                    mLastUpdateTimeLabel, mLastUpdateTime)
        }
    }

    private fun stopLocationUpdates() {
        if ((!mRequestingLocationUpdates)!!) {
            Log.d(TAG, "stopLocationUpdates: updates never requested, no-op.")
            return
        }

        mFusedLocationClient!!.removeLocationUpdates(mLocationCallback!!)
                .addOnCompleteListener(this) {
                    mRequestingLocationUpdates = false
                    setButtonsEnabledState()
                }
    }

    public override fun onResume() {
        super.onResume()
        if (mRequestingLocationUpdates!! && checkPermissions()) {
            startLocationUpdates()
        } else if (!mNeverAskPermissionShowed && !checkPermissions()) {
            requestPermissions()
        }

        updateUI()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle) {
        savedInstanceState.putBoolean(KEY_REQUESTING_LOCATION_UPDATES, mRequestingLocationUpdates!!)
        savedInstanceState.putParcelable(KEY_LOCATION, mCurrentLocation)
        savedInstanceState.putString(KEY_LAST_UPDATED_TIME_STRING, mLastUpdateTime)
        super.onSaveInstanceState(savedInstanceState)
    }

    private fun showSnackbar(mainTextStringId: Int, actionStringId: Int,
                             listener: View.OnClickListener) {
        Snackbar.make(
                findViewById<View>(android.R.id.content),
                getString(mainTextStringId),
                Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(actionStringId), listener).show()
    }


    private fun checkPermissions(): Boolean {
        val permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
        return permissionState == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            showSnackbar(R.string.permission_rationale,
                    android.R.string.ok, View.OnClickListener {
                ActivityCompat.requestPermissions(this@MainActivity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        REQUEST_PERMISSIONS_REQUEST_CODE)
            })
        } else {
            Log.i(TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(this@MainActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSIONS_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.size <= 0) {
                Log.i(TAG, "User interaction was cancelled.")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mRequestingLocationUpdates!!) {
                    Log.i(TAG, "Permission granted, updates requested, starting location updates")
                    startLocationUpdates()
                }
            } else {
                // Permission denied.

                // Notify the user via a SnackBar that they have rejected a core permission for the
                // app, which makes the Activity useless. In a real app, core permissions would
                // typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.

                val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                if (!shouldProvideRationale) {
                    mNeverAskPermissionShowed = true
                    showSnackbar(R.string.permission_denied_explanation,
                            R.string.settings, View.OnClickListener {
                        // Build intent that displays the App settings screen.
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        val uri = Uri.fromParts("package",
                                BuildConfig.APPLICATION_ID, null)
                        intent.data = uri
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        startActivity(intent)
                    })
                }
            }
        }
    }

    companion object {

        private val TAG = MainActivity::class.java.simpleName


        private val REQUEST_PERMISSIONS_REQUEST_CODE = 42
        private val REQUEST_CHECK_SETTINGS = 21


        private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000
        private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

        // Keys for storing activity state in the Bundle.
        private val KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates"
        private val KEY_LOCATION = "location"
        private val KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string"
    }
}

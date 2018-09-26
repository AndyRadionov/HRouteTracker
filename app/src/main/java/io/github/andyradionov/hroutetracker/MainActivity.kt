package io.github.andyradionov.hroutetracker

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener

class MainActivity : AppCompatActivity() {

    private lateinit var connectionCallbacks: GoogleApiClient.ConnectionCallbacks
    private lateinit var onConnectionFailedListener: GoogleApiClient.OnConnectionFailedListener
    private lateinit var locationListener: LocationListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = mutableListOf(1,2)
        list.forEach {
            list.remove(0)
        }
    }
}

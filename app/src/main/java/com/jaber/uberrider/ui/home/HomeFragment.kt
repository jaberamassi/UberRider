package com.jaber.uberrider.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.jaber.uberrider.R
import com.jaber.uberrider.common.Common
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener

class HomeFragment : Fragment(), OnMapReadyCallback {
    var currentUserUid = FirebaseAuth.getInstance().currentUser?.uid.toString()
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap

    //Location System Variables
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //Online System Variables
    private lateinit var onlineRef: DatabaseReference
    private lateinit var currentUserRef: DatabaseReference
    private lateinit var riderLocationRef: DatabaseReference
    private lateinit var geoFire: GeoFire
    private val onlineValueEventListener = object : ValueEventListener {

        override fun onDataChange(snapshot: DataSnapshot) {
            if (snapshot.exists())
                currentUserRef.onDisconnect().removeValue()
        }

        override fun onCancelled(error: DatabaseError) {
            Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show()
        }
    }


    override fun onDestroy() {
        //Remove Current Location System when shut down
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)


        //Remove Online System when shut down
        geoFire.removeLocation(currentUserUid)
        onlineRef.removeEventListener(onlineValueEventListener)

        super.onDestroy()
    }


    override fun onResume() {
        super.onResume()
        registerOnlineSystem()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        init()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        return root
    }

    @SuppressLint("MissingPermission")
    private fun init() {
        //Online System Init
        onlineRef = FirebaseDatabase.getInstance().reference.child(".info/connected")
        riderLocationRef = FirebaseDatabase.getInstance().getReference(Common.RIDERS_LOCATION_REFERENCE)
        currentUserRef = riderLocationRef.child(currentUserUid)

        geoFire = GeoFire(riderLocationRef)
        registerOnlineSystem()


        //location System Init
        locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.fastestInterval = 3000L
        locationRequest.interval = 5000
        locationRequest.smallestDisplacement = 10f

        locationCallback = object : LocationCallback() {
            //Ctrl + o
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)

                val newPos = LatLng(locationResult!!.lastLocation!!.latitude, locationResult.lastLocation.longitude)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                //Update Location
                geoFire.setLocation(currentUserUid, GeoLocation(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
                ) { key: String, error: DatabaseError? ->
                    if (error != null) { Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show() }
                    else{Snackbar.make(mapFragment.requireView(),"You're online", Snackbar.LENGTH_SHORT).show()}

                }
            }
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomControlsEnabled = true

        //Request permission
        Dexter.withContext(context)
            .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {

                @SuppressLint("MissingPermission")
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {
                    //Enable button first
                    mMap.isMyLocationEnabled =true
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {
                        Toast.makeText(context, "Location Button Clicked", Toast.LENGTH_LONG).show()
                        fusedLocationProviderClient.lastLocation
                            .addOnFailureListener { ex ->
                                Toast.makeText(context, "Permission Request Error ${ex.message}", Toast.LENGTH_LONG).show()
                            }.addOnSuccessListener { location ->
                                val userLatLng = LatLng(location.latitude,location.longitude)
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng,18f))
                            }
                        true
                    }
                }
                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Snackbar.make(requireView(), "Permission ${p0!!.permissionName} is denied", Snackbar.LENGTH_LONG).show()
                }

                override fun onPermissionRationaleShouldBeShown(p0: PermissionRequest?, p1: PermissionToken?) {}
            })
            .check()

        //Location Button Layout
        val view = mapFragment.requireView().findViewById<View>("1".toInt()).parent as View
        val locationButton = view.findViewById<View>("2".toInt())
        val params = locationButton?.layoutParams as RelativeLayout.LayoutParams
        params.addRule(RelativeLayout.ALIGN_TOP,0)
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
        params.bottomMargin = 50

        //Eternal Map Style
        try {
            val success = googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context,R.raw.uber_maps_style))
            if(!success){
                Log.e("JABER_ERROR", "parsing style error")
            }

        }catch (e: Resources.NotFoundException){
            Log.e("JABER_ERROR", e.message.toString())
        }

    }

    private fun registerOnlineSystem() {
        onlineRef.addValueEventListener(onlineValueEventListener)
    }
}
package com.jaber.uberrider.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.firebase.geofire.GeoQueryEventListener
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.jaber.uberrider.R
import com.jaber.uberrider.callback.FirebaseDriverInfoListener
import com.jaber.uberrider.callback.FirebaseFailedListener
import com.jaber.uberrider.common.Common
import com.jaber.uberrider.model.DriverGeoModel
import com.jaber.uberrider.model.DriverInfoModel
import com.jaber.uberrider.model.GeoQueryModel
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.*

class HomeFragment : Fragment(), OnMapReadyCallback, FirebaseDriverInfoListener {
    var currentUserUid = FirebaseAuth.getInstance().currentUser?.uid.toString()
    private lateinit var mapFragment: SupportMapFragment
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var mMap: GoogleMap

    //Location System Variables
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    //Load Driver lecture#17
    var distance = 1.0
    val LIMIT_RANGE = 10.0
    var previousLocation:Location?=null
    var currentLocation:Location?=null
    var cityName = ""
    var isFirstTime = true
        //Listener
    lateinit var iFirebaseDriverInfoListener : FirebaseDriverInfoListener
    lateinit var iFirebaseFailedListener : FirebaseFailedListener


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


    private fun init() {
        iFirebaseDriverInfoListener = this
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


                //if user has change location, calculate and load driver again
                if(isFirstTime){
                    previousLocation = locationResult.lastLocation
                    currentLocation = locationResult.lastLocation
                    isFirstTime = false
                }else{
                    previousLocation = currentLocation
                    currentLocation = locationResult.lastLocation
                }


                if(previousLocation!!.distanceTo(currentLocation)/1000 <= LIMIT_RANGE){
                    loadAvailableDrivers()
                }



                //Update Location
                geoFire.setLocation(currentUserUid, GeoLocation(locationResult.lastLocation.latitude, locationResult.lastLocation.longitude)
                ) { key: String, error: DatabaseError? ->
                    if (error != null) { Snackbar.make(mapFragment.requireView(), error.message, Snackbar.LENGTH_LONG).show() }
                    else{Snackbar.make(mapFragment.requireView(),"You're online", Snackbar.LENGTH_SHORT).show()}

                }
            }
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(mapFragment.requireView(), getString(R.string.permission_require), Snackbar.LENGTH_LONG).show()
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())

        loadAvailableDrivers()
    }


    private fun loadAvailableDrivers() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
                Snackbar.make(requireView(),getString(R.string.permission_require),Snackbar.LENGTH_SHORT).show()
            return
        }
        fusedLocationProviderClient.lastLocation
            .addOnFailureListener {e->
                Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_SHORT).show()
            }.addOnSuccessListener {location ->
                //Load all drivers in city
                val geoCoder = Geocoder(requireContext(), Locale.getDefault())
                val addressList: List<Address>

                try {
                    addressList = geoCoder.getFromLocation(location.latitude,location.longitude,1)
                    cityName = addressList[0].locality

                    //Query
                    val driverLocationRef = FirebaseDatabase.getInstance().getReference(Common.DRIVERS_LOCATION_REFERENCE).child(cityName)
                    val geoFire = GeoFire(driverLocationRef)
                    val geoQuery = geoFire.queryAtLocation(GeoLocation(location.latitude,location.longitude),distance)
                    geoQuery.removeAllListeners()

                    geoQuery.addGeoQueryEventListener(object :GeoQueryEventListener{
                        override fun onGeoQueryReady() {
                           if (distance <= LIMIT_RANGE){
                               distance++
                               loadAvailableDrivers()
                           }else{
                               distance = 0.0
                               addDriverMarker()
                           }
                        }

                        override fun onKeyEntered(key: String?, location: GeoLocation?) { Common.driversFound.add(DriverGeoModel(key!!,location!!)) }

                        override fun onKeyExited(key: String?) {}

                        override fun onKeyMoved(key: String?, location: GeoLocation?) {}

                        override fun onGeoQueryError(error: DatabaseError?) { Snackbar.make(requireView(),error!!.message,Snackbar.LENGTH_SHORT).show() }

                    })

                    driverLocationRef.addChildEventListener(object:ChildEventListener{
                        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                            val geoQueryModel = snapshot.getValue(GeoQueryModel::class.java)
                            val geoLocation = GeoLocation(geoQueryModel!!.l!![0], geoQueryModel.l!![1])
                            val driverGeoModel = DriverGeoModel(snapshot.key!!,geoLocation)
                            val newDriverLocation = Location("")
                            newDriverLocation.latitude = geoLocation.latitude
                            newDriverLocation.longitude = geoLocation.longitude
                            val newDistance = location.distanceTo(newDriverLocation)/1000 // in km
                            if(newDistance <= LIMIT_RANGE){
                                findDriverByKey(driverGeoModel)
                            }

                        }

                        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}

                        override fun onChildRemoved(snapshot: DataSnapshot) {}

                        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

                        override fun onCancelled(error: DatabaseError) {
                            Snackbar.make(requireView(), error.message,Snackbar.LENGTH_SHORT).show()
                        }

                    })


                }catch (e:IOException){
                    Snackbar.make(requireView(),e.message!!,Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    @SuppressLint("CheckResult")
    private fun addDriverMarker() {
       if(Common.driversFound.size > 0){
           Observable.fromIterable(Common.driversFound)
               .subscribeOn(Schedulers.newThread())
               .observeOn(AndroidSchedulers.mainThread())
               .subscribe({driverGeoModel:DriverGeoModel? ->
                   findDriverByKey(driverGeoModel)
               },{
                   Snackbar.make(mapFragment.requireView(), it.message!!, Snackbar.LENGTH_LONG).show()
               })
       }else{
           Snackbar.make(mapFragment.requireView(), getString(R.string.drivers_not_found), Snackbar.LENGTH_LONG).show()
       }
    }

    private fun findDriverByKey(driverGeoModel: DriverGeoModel?) {
        FirebaseDatabase.getInstance().getReference(Common.DRIVER_INFO_REFERENCE)
            .child(driverGeoModel!!.key)
            .addListenerForSingleValueEvent(object :ValueEventListener{
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.hasChildren()){
                        driverGeoModel.driverInfoModel = (snapshot.getValue(DriverInfoModel::class.java))
                        iFirebaseDriverInfoListener.onDriverInfoLoadSuccess(driverGeoModel)
                    }else
                        iFirebaseFailedListener.onFirebaseFailed(getString(R.string.key_not_found) + driverGeoModel.key)

                }

                override fun onCancelled(error: DatabaseError) {
                    iFirebaseFailedListener.onFirebaseFailed(error.message)
                }

            })
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

    override fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?) {
        //if already have marker with this key,don't set it again

        if(!Common.markerList.containsKey(driverGeoModel!!.key)){
            Common.markerList.put(driverGeoModel!!.key,
                mMap.addMarker(MarkerOptions().position(LatLng(driverGeoModel.geoLocation!!.latitude, driverGeoModel.geoLocation!!.longitude))
                    .flat(true)
                    .title(Common.buildName(driverGeoModel.driverInfoModel!!.firstName, driverGeoModel.driverInfoModel!!.lastName))
                    .snippet(driverGeoModel.driverInfoModel!!.phoneNumber)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.car))
                ))

            if(!TextUtils.isEmpty(cityName)){
                val driverLocation = FirebaseDatabase.getInstance()
                    .getReference(Common.DRIVERS_LOCATION_REFERENCE)
                    .child(cityName)
                    .child(driverGeoModel.key)
                driverLocation.addValueEventListener(object :ValueEventListener{
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if(!snapshot.hasChildren()){
                            if (Common.markerList.get(driverGeoModel.key) != null){
                                val marker = Common.markerList[driverGeoModel.key]
                                marker!!.remove() // Remove marker from map
                                Common.markerList.remove(driverGeoModel.key)// Remove marker information
                                driverLocation.removeEventListener(this)
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Snackbar.make(requireView(), error.message,Snackbar.LENGTH_SHORT).show()
                    }

                })
            }
        }
    }
}
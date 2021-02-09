package com.jaber.uberrider.callbasck

import com.jaber.uberrider.model.DriverGeoModel

interface FirebaseDriverInfoListener {
    fun onDriverInfoLoadSuccess(driverGeoModel: DriverGeoModel?)
}
package com.jaber.uberrider.model

import com.firebase.geofire.GeoLocation

class DriverGeoModel(
    var key: String, var geoLocation: GeoLocation?,
) {

    var driverInfoModel: DriverInfoModel?= null
}
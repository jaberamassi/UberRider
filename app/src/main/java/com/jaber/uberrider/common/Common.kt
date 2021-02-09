package com.jaber.uberrider.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.android.gms.maps.model.Marker
import com.jaber.uberrider.R
import com.jaber.uberrider.model.DriverGeoModel
import com.jaber.uberrider.model.RiderInfoModel
import java.lang.StringBuilder

object Common {
    val markerList: MutableMap<String,Marker> = HashMap<String,Marker>()
    var driversFound: MutableSet<DriverGeoModel> = HashSet<DriverGeoModel>()
    const val DRIVER_INFO_REFERENCE = "Drivers"
    const val DRIVERS_LOCATION_REFERENCE: String = "driversLocation"
    const val TOKEN_REFERENCE: String = "Token"
    val RIDERS_LOCATION_REFERENCE: String = "ridersLocation"
    var currentRider: RiderInfoModel ?= null
    const val RIDER_INFO_REFERENCE: String = "Riders"

    const val NOTIF_TITLE: String = "title"
    const val NOTIF_BODY: String = "body"


    fun showNotification(context: Context, id: Int, title: String?, body: String?, intent: Intent?) {
        var pendingIntent: PendingIntent? = null
        if (intent != null){
            pendingIntent = PendingIntent.getActivity(context,id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            val NOTIFECATION_CHANNEL_ID = "jaber_uber"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
                val notificationChannel = NotificationChannel(NOTIFECATION_CHANNEL_ID,"Uber",
                    NotificationManager.IMPORTANCE_HIGH)
                notificationChannel.description = "Uber"
                notificationChannel.enableLights(true)
                notificationChannel.lightColor = Color.RED
                notificationChannel.enableVibration(true)
                notificationChannel.vibrationPattern = longArrayOf(0,1000,500,100)

                notificationManager.createNotificationChannel(notificationChannel)
            }

            val builder = NotificationCompat.Builder(context,NOTIFECATION_CHANNEL_ID)
            builder.setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_VIBRATE)
                .setSmallIcon(R.drawable.ic_car)
                .setLargeIcon(BitmapFactory.decodeResource(context.resources,R.drawable.ic_car))

            if (pendingIntent !=null){
                builder.setContentIntent(pendingIntent)
            }

            val notification = builder.build()
            notificationManager.notify(id,notification)
        }

    }

    fun buildName(firstName: String, lastName: String): String? {
        return StringBuilder(firstName).append(" ").append(lastName).toString()
    }
}
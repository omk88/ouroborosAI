package com.example.aimobileapp

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import com.google.firebase.FirebaseApp

class MyApplication : Application() {
    private val networkRequest: NetworkRequest = NetworkRequest.Builder().build()

    override fun onCreate() {
        super.onCreate()
        context = this
        FirebaseApp.initializeApp(this)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerNetworkCallback(networkRequest, networkCallback)

    }

    companion object {
        lateinit var context: Context
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            super.onLost(network)
            val intent = Intent(this@MyApplication, NoInternetActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        override fun onAvailable(network: Network) {
            super.onAvailable(network)
        }
    }

    override fun onTerminate() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.unregisterNetworkCallback(networkCallback)
        super.onTerminate()
    }
}

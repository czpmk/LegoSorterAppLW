package com.lsorter.utils

import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import android.text.format.Formatter.formatIpAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.nio.ByteBuffer

class NetworkUtils {
    companion object {
        suspend fun getDeviceIP(context: Context) : String {
            val appContext: Context = context.applicationContext
            val wifiManager: WifiManager? = appContext.getSystemService(WIFI_SERVICE) as WifiManager?
            var ipAddress = wifiManager?.getConnectionInfo()?.getIpAddress()
            var ipAddressBytes = ipAddress?.let { intIPToByteArrayIP(it) };
            return withContext(Dispatchers.IO){
                return@withContext InetAddress.getByAddress(ipAddressBytes).hostAddress
            }

        }
        internal fun intIPToByteArrayIP(ipAddress : Int) : ByteArray {
            return ByteBuffer.allocate(Int.SIZE_BYTES).putInt(ipAddress).array().reversedArray()
        }
    }
}
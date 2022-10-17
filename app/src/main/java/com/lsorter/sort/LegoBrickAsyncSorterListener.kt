package com.lsorter.sort

import android.util.Log
import java.net.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


open class LegoBrickAsyncSorterListener() {

    private var socket: DatagramSocket = DatagramSocket()
    open var isListening: AtomicBoolean = AtomicBoolean(false)

    init {
        this.socket.broadcast = true
    }

    open fun start(port: Int, callback: (result: String?) -> Unit){
        Log.d("[AsyncSorterListener]", "Starting listener.")
        thread(start = true) {
            //this.socket.connect(InetSocketAddress(port))
            this.socket = DatagramSocket(port)
            Log.d("[AsyncSorterListener]", "Starting packet capturing.")
            isListening.set(true)
            while(isListening.get()){
                receiveUDP(){
                        result -> callback.invoke(result)
                }
            }
            this.socket.disconnect()
            this.socket.close()
        }
    }

    private fun receiveUDP(callback: (result: String?) -> Unit) {
        var buffer = ByteArray(2048)
        try {
            val packet = DatagramPacket(buffer, buffer.size)
            Log.d("[AsyncSorterListener]", "Recieving packets.")
            this.socket.receive(packet)
            val data = String(packet.data.sliceArray(IntRange(0,packet.length-1)))
            Log.d("[AsyncSorterListener]", "Packet recieved: " + data)
            callback.invoke(data)
        } catch (e: Exception) {
            Log.d("[AsyncSorterListener]", "receiveUDP catched exception: " + e.toString())
            e.printStackTrace()
        }
    }


    open fun stop() {
        Log.d("[AsyncSorterListener]", "Closing socket.")
        isListening.set(false);
    }

}
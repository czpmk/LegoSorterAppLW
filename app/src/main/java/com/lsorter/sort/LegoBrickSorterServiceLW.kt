package com.lsorter.sort

import com.lsorter.common.CommonMessagesProto
import com.lsorter.connection.ConnectionManager
import com.lsorter.sorterLW.LegoSorterLWGrpc
import com.lsorter.sorterLW.LegoSorterProtoLW
import io.grpc.stub.StreamObserver


class LegoBrickSorterServiceLW(connectionManager: ConnectionManager) {
    private val channel = connectionManager.getConnectionChannel()
    private val legoSorterLWService: LegoSorterLWGrpc.LegoSorterLWStub
        = LegoSorterLWGrpc.newStub(channel);
    private val observer = object: StreamObserver<LegoSorterProtoLW.PhotoRequest> {
        override fun onNext(value: LegoSorterProtoLW.PhotoRequest?) {
            println(value!!.test);
        }

        override fun onError(t: Throwable?) {
            println("DEBUG: "+t!!.message)
        }

        override fun onCompleted() {
            TODO("Not yet implemented")
        }

    };
    fun startMachine(){}
    fun stopMachine(){}
    fun photoRequest(){
        val request = legoSorterLWService.photoRequestStream(CommonMessagesProto.Empty.getDefaultInstance(), observer);
    }
}
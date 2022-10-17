package com.lsorter.sort;

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.internal.utils.ImageUtil
import com.google.protobuf.ByteString
import com.lsorter.analyze.common.RecognizedLegoBrick
import com.lsorter.asyncSorter.LegoAsyncSorterGrpc
import com.lsorter.common.CommonMessagesProto
import com.lsorter.connection.ConnectionManager
import com.lsorter.sorter.LegoSorterGrpc
import com.lsorter.sorter.LegoSorterLWGrpc
import com.lsorter.sorter.LegoSorterProtoLW
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean



class LegoBrickAsyncSorterService {
    data class SorterConfiguration(val speed: Int = 50, val deviceIp: String)
    private val captureExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private val connectionManager: ConnectionManager = ConnectionManager()
    private val legoAsyncSorterService: LegoAsyncSorterGrpc.LegoAsyncSorterBlockingStub

    init {
        this.legoAsyncSorterService =
            LegoAsyncSorterGrpc.newBlockingStub(connectionManager.getConnectionChannel())
    }

    @SuppressLint("CheckResult")
    fun start() {
        this.legoAsyncSorterService.start(CommonMessagesProto.Empty.getDefaultInstance())
    }

    @SuppressLint("CheckResult")
    fun stop() {
        this.legoAsyncSorterService.stop(CommonMessagesProto.Empty.getDefaultInstance())
    }


    @SuppressLint("CheckResult")
    fun updateConfig(configuration: CommonMessagesProto.SorterConfigurationWithIP) {
        this.legoAsyncSorterService.updateConfiguration(configuration)
    }

    fun getConfig(): SorterConfiguration {
        TODO("Not yet implemented")
    }


    @SuppressLint("CheckResult", "RestrictedApi")
    fun processImage(image: ImageProxy) {
        val imageRequest = CommonMessagesProto.ImageRequest.newBuilder()
            .setImage(
                ByteString.copyFrom(
                    ImageUtil.imageToJpegByteArray(image)
                )
            ).setRotation(image.imageInfo.rotationDegrees)
            .build()
        image.close()
        Log.d("[processImage2]", "DEBUG")
        this.legoAsyncSorterService.processImage(imageRequest)
    }

    fun captureImage(
        imageCapture: ImageCapture,
        callback: (ImageProxy) -> Unit
    ) {
        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) = callback(image)
            }
        )
    }

}
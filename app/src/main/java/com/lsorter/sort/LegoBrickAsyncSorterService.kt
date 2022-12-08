package com.lsorter.sort

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean


class LegoBrickAsyncSorterService {
    data class SorterConfiguration(val speed: Int = 50)

    private val captureExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private var terminated: AtomicBoolean = AtomicBoolean(false)
    private var canProcessNext: AtomicBoolean = AtomicBoolean(true)
    private val connectionManager: ConnectionManager = ConnectionManager()
    private val legoAsyncSorterService: LegoAsyncSorterGrpc.LegoAsyncSorterBlockingStub
    private val legoSorterService: LegoSorterGrpc.LegoSorterBlockingStub

    init {
        this.legoSorterService =
            LegoSorterGrpc.newBlockingStub(connectionManager.getConnectionChannel())
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
    fun updateConfig(configuration: CommonMessagesProto.SorterConfiguration) {
        this.legoAsyncSorterService.updateConfiguration(configuration)
    }

    @SuppressLint("CheckResult")
    fun startConveyorBelt() {
        this.legoSorterService.startMachine(CommonMessagesProto.Empty.getDefaultInstance())
    }

    @SuppressLint("CheckResult")
    fun stopConveyorBelt() {
        this.legoSorterService.stopMachine(CommonMessagesProto.Empty.getDefaultInstance())
    }

    fun stopImageCapturing() {
        terminated.set(true)
    }

    fun scheduleImageCapturingAndStartMachine(
        imageCapture: ImageCapture,
        runTime: Int,
        /*continousMode: Boolean,*/
        callback: (ImageProxy) -> Unit
    ) {
        terminated.set(false)
        canProcessNext.set(true)
        captureExecutor.submit {
            //if (continousMode)
            startConveyorBelt()
            while (!terminated.get()) {
                synchronized(canProcessNext) {
                    if (canProcessNext.get()) {
                        canProcessNext.set(false)
                        //if (!continousMode)
                        stopConveyorBelt()
                        captureImage(imageCapture) { image ->
                            callback(image)
                            if (!terminated.get()) {
                                //if (!continousMode)
                                startConveyorBelt()
                                println("[LegoBrickAsyncSorterService] Delaying capture for $runTime")
                                Thread.sleep(runTime.toLong())
                                canProcessNext.set(true)
                            }
                        }
                    }
                    Thread.sleep(10)
                }
            }
            stopConveyorBelt()
        }
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
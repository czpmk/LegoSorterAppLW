package com.lsorter.utils

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.lsorter.sort.LegoBrickAsyncSorterService

class DelayedAsyncImageAnalyzer(
    private val legoBrickAsyncSorterService: LegoBrickAsyncSorterService,
    private val delayTime: Int
) : ImageAnalysis.Analyzer {
    var latestAnalysisTimestamp = 0L
    override fun analyze(image: ImageProxy) {
        if (System.currentTimeMillis() - latestAnalysisTimestamp < delayTime) {

            //drop frame if not enough time between captures
            image.close()

            return
        }

        latestAnalysisTimestamp = System.currentTimeMillis()
        legoBrickAsyncSorterService.processImage(image)
    }
}
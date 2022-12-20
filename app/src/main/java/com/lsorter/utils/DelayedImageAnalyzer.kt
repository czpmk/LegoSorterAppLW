package com.lsorter.utils

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.lsorter.sort.LegoBrickSorterService

class DelayedImageAnalyzer(
    private val legoBrickSorterService: LegoBrickSorterService,
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
        legoBrickSorterService.processImage(image)
    }
}
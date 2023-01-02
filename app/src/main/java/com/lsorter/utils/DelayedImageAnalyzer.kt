package com.lsorter.utils

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.lsorter.sort.LegoBrickSorterService
import com.lsorter.view.sort.SortFragment

class DelayedImageAnalyzer(
    private val delayTime: Int,
    private val parent: SortFragment
) : ImageAnalysis.Analyzer {
    var latestAnalysisTimestamp = 0L
    override fun analyze(image: ImageProxy) {
        if (System.currentTimeMillis() - latestAnalysisTimestamp < delayTime) {

            //drop frame if not enough time between captures
            image.close()

            return
        }

        latestAnalysisTimestamp = System.currentTimeMillis()
        parent.processImageAndDrawBricks(image)
    }
}
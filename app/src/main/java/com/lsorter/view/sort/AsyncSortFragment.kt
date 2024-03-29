package com.lsorter.view.sort

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.preference.PreferenceManager
import com.google.common.util.concurrent.ListenableFuture
import com.lsorter.common.CommonMessagesProto
import com.lsorter.databinding.FragmentSortBinding
import com.lsorter.sort.DefaultLegoBrickSorterService
import com.lsorter.sort.LegoBrickAsyncSorterService
import com.lsorter.sort.LegoBrickSorterService
import com.lsorter.utils.DelayedAsyncImageAnalyzer
import com.lsorter.utils.PreferencesUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AsyncSortFragment : Fragment() {

    private var cameraExecutor: ExecutorService = Executors.newFixedThreadPool(4)
    private lateinit var viewModel: SortViewModel
    private lateinit var binding: FragmentSortBinding
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var asyncSorterService: LegoBrickAsyncSorterService


    private var isSortingStarted: AtomicBoolean = AtomicBoolean(false)
    private var isMachineStarted: AtomicBoolean = AtomicBoolean(false)
    private var initialized: AtomicBoolean = AtomicBoolean(false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentSortBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this).get(SortViewModel::class.java)

        binding.sortViewModel = viewModel
        binding.lifecycleOwner = viewLifecycleOwner
        binding.focusBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val progressScaled = progress.toFloat() / (seekBar?.max ?: 100)
                viewModel.apply {
                    cameraFocusDistance = calculateFocusDistance(progressScaled)
                    binding.focusDistanceValue.text = String.format("%.2f", cameraFocusDistance)
                }

                initialize(startProcessing = false)
            }

            private fun SortViewModel.calculateFocusDistance(progressScaled: Float) =
                maximumCameraFocusDistance + (minimumCameraFocusDistance - maximumCameraFocusDistance) * progressScaled

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        asyncSorterService = LegoBrickAsyncSorterService()

        viewModel.eventStartStopSortingButtonClicked.observe(
            viewLifecycleOwner,
            Observer { startSorting ->
                if (startSorting) {
                    setVisibilityOfFocusSeeker(View.GONE)
                    thread(start = true) {
                        asyncSorterService.start()
                    }
                    startSorting()
                    binding.startStopSortingButton.text =
                        getString(com.lsorter.R.string.stop_sorting_text)
                    isSortingStarted.set(true)
                } else {
                    isSortingStarted.set(false)
                    setVisibilityOfFocusSeeker(View.VISIBLE)
                    thread(start = true) {
                        asyncSorterService.stop()
                    }
                    stopSorting()
                    binding.startStopSortingButton.text =
                        getString(com.lsorter.R.string.start_sorting_text)
                }
            }
        )

        viewModel.eventStartStopMachineButtonClicked.observe(
            viewLifecycleOwner,
            Observer {
                if (isMachineStarted.get()) {
                    isMachineStarted.set(false)
                    stopMachine()
                    binding.startStopMachineButton.text =
                        getString(com.lsorter.R.string.start_machine_text)
                } else {
                    isMachineStarted.set(true)
                    startMachine()
                    binding.startStopMachineButton.text =
                        getString(com.lsorter.R.string.stop_machine_text)
                }
            }
        )

    }

    override fun onResume() {
        super.onResume()
        initialize()
    }

    private fun startMachine() {
        asyncSorterService.startMachine()
    }

    private fun stopMachine() {
        asyncSorterService.stopMachine()
    }

    private fun setVisibilityOfFocusSeeker(visibility: Int) {
        binding.focusBar.visibility = visibility
        binding.focusBarLabel.visibility = visibility
    }

    private fun startSorting() {
        initialize(startProcessing = true)
    }

    private fun stopSorting() {
        asyncSorterService.stopImageCapturing()
        cameraProvider.unbindAll()
        initialize(startProcessing = false)
    }

    private fun initialize(startProcessing: Boolean = false): ListenableFuture<ProcessCameraProvider> {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val conveyorSpeed = pref.getInt(CONVEYOR_SPEED_VALUE_PREFERENCE_KEY, 50)
        val runConveyorTime =
            pref.getString(RUN_CONVEYOR_TIME_PREFERENCE_KEY, "500")!!.toInt()
        val sortingMode = pref.getString(SORTING_MODE_PREFERENCE_KEY, "0")!!.toInt()

        val config = CommonMessagesProto.SorterConfiguration.newBuilder()
            .setSpeed(conveyorSpeed)
            .build()
        asyncSorterService.updateConfig(config)

        cameraProviderFuture.addListener(Runnable {
            this.cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val preview = PreferencesUtils.extendPreviewView(Preview.Builder(), context)
                .apply { setFocusDistance(this) }
                .build()

            if (startProcessing) {
                if (sortingMode == STOP_CAPTURE_RUN_PREFERENCE) {
                    val imageCapture = getImageCapture()
                    val camera =
                        cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview)

                    asyncSorterService.scheduleImageCapturingAndStartMachine(
                        imageCapture,
                        runConveyorTime
                    ) { image -> processImage(image) }
                    camera
                } else {
                    val imageAnalysis = getImageAnalysis()
                    cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)
                }
            } else {
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            }.apply {
                extractLensCharacteristics()
                PreferencesUtils.applyPreferences(this, context)
            }

            preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            initialized.set(true)
        }, ContextCompat.getMainExecutor(this.requireContext()))

        return cameraProviderFuture
    }

    @SuppressLint("UnsafeExperimentalUsageError", "RestrictedApi")
    private fun Camera.extractLensCharacteristics() {
        Camera2CameraInfo.extractCameraCharacteristics(this.cameraInfo).apply {
            this@AsyncSortFragment.viewModel.minimumCameraFocusDistance =
                this.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                    ?: 0f

            this@AsyncSortFragment.viewModel.maximumCameraFocusDistance =
                this.get(CameraCharacteristics.LENS_INFO_HYPERFOCAL_DISTANCE)
                    ?: Float.MAX_VALUE
        }
    }


    private fun processImage(image: ImageProxy) {
        asyncSorterService.processImage(image)
    }

    private fun getImageCapture(): ImageCapture {
        return PreferencesUtils.extendImageCapture(ImageCapture.Builder(), context).build()
    }

    private fun getImageAnalysis(): ImageAnalysis {
        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val sleepTime =
            pref.getString(RUN_CONVEYOR_TIME_PREFERENCE_KEY, "500")!!.toInt()
        val sortingMode = pref.getString(SORTING_MODE_PREFERENCE_KEY, "0")!!.toInt()
        Log.d("[AsyncSortFragment]", "sortingMode: $sortingMode sleepTime: $sleepTime")
        var analyzer = ImageAnalysis.Analyzer { image -> processImage(image) }

        if (sortingMode == DELAYED_CAPTURE_CONTINUOUS_MOVE_PREFERENCE) {
            analyzer = DelayedAsyncImageAnalyzer(asyncSorterService, sleepTime)
        }

        Log.d("[AsyncSortFragment]", analyzer.toString())
        return PreferencesUtils.extendImageAnalysis(ImageAnalysis.Builder(), context)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setImageQueueDepth(1)
            .build()
            .also {
                it.setAnalyzer(
                    cameraExecutor,
                    analyzer
                )
            }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun setFocusDistance(previewBuilder: Preview.Builder) {
        Camera2Interop.Extender(previewBuilder).apply {
            this.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_OFF
            )
            this.setCaptureRequestOption(
                CaptureRequest.LENS_FOCUS_DISTANCE,
                viewModel.cameraFocusDistance
            )
        }
    }


    override fun onDestroy() {
        if (initialized.get()) {
            if (isSortingStarted.get() || isMachineStarted.get()) {
                stopSorting()
            }
            initialized.set(false)
        }
        super.onDestroy()
    }

    companion object {
        const val SORTING_MODE_PREFERENCE_KEY: String = "SORTER_MODE_PREFERENCE"
        const val STOP_CAPTURE_RUN_PREFERENCE: Int = 0
        const val CONTINUOUS_MOVE_PREFERENCE: Int = 1
        const val DELAYED_CAPTURE_CONTINUOUS_MOVE_PREFERENCE: Int = 2
        const val RUN_CONVEYOR_TIME_PREFERENCE_KEY: String = "RUN_CONVEYOR_TIME_VALUE"
        const val CONVEYOR_SPEED_VALUE_PREFERENCE_KEY: String = "SORTER_CONVEYOR_SPEED_VALUE"
    }
}
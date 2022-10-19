package com.lsorter.view.sort

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.util.Log
import android.util.Size
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
import com.lsorter.analyze.common.RecognizedLegoBrick
import com.lsorter.analyze.layer.LegoGraphic
import com.lsorter.databinding.FragmentSortBinding
import com.lsorter.sort.DefaultLegoBrickSorterService
import com.lsorter.sort.LegoBrickAsyncSorterListener
import com.lsorter.sort.LegoBrickAsyncSorterService
import com.lsorter.sort.LegoBrickSorterService
import com.lsorter.utils.NetworkUtils
import com.lsorter.utils.PreferencesUtils
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class AsyncSortFragment : Fragment() {

    private var cameraExecutor: ExecutorService = Executors.newFixedThreadPool(4)
    private lateinit var viewModel: SortViewModel
    private lateinit var binding: FragmentSortBinding
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var sorterService: LegoBrickSorterService
    private var imageCapture: ImageCapture? = null
    private var ipAddr: String = ""
    private val listener: LegoBrickAsyncSorterListener = LegoBrickAsyncSorterListener()
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

        sorterService = DefaultLegoBrickSorterService()
        asyncSorterService = LegoBrickAsyncSorterService()

        viewModel.eventStartStopSortingButtonClicked.observe(
            viewLifecycleOwner,
            Observer { startSorting ->
                if (startSorting) {
                    setVisibilityOfFocusSeeker(View.GONE)
                    thread(start=true) {
                        asyncSorterService.start()
                    }
                    startSorting()
                    binding.startStopSortingButton.text =
                        getString(com.lsorter.R.string.stop_sorting_text)
                    isSortingStarted.set(true)
                } else {
                    isSortingStarted.set(false)
                    setVisibilityOfFocusSeeker(View.VISIBLE)
                    thread(start=true) {
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
        sorterService.startMachine()
    }

    private fun stopMachine() {
        sorterService.stopMachine()
    }

    private fun setVisibilityOfFocusSeeker(visibility: Int) {
        binding.focusBar.visibility = visibility
        binding.focusBarLabel.visibility = visibility
    }

    private fun startSorting() {
        if(!(listener.isListening.get())){
            listener.start(LISTENER_PORT) { result ->
                Log.d("[AsyncSortFragment]", "Listener callback: $result")
                imageCapture?.let {
                    asyncSorterService.captureImage(
                        it
                    ) { image -> processImage(image) }
                }
            }
            initialize(startProcessing = true)
        }
    }

    private fun stopSorting() {
        sorterService.stopImageCapturing()
        if(listener.isListening.get()){
            listener.stop()
        }
    }

    private fun initialize(startProcessing: Boolean = false): ListenableFuture<ProcessCameraProvider> {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        val pref = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val conveyorSpeed = pref.getInt(CONVEYOR_SPEED_VALUE_PREFERENCE_KEY, 50)

        if(ipAddr == ""){
            ipAddr = runBlocking { return@runBlocking NetworkUtils.getDeviceIP(requireContext()) }
        }

        val config = CommonMessagesProto.SorterConfigurationWithIP.newBuilder()
            .setSpeed(conveyorSpeed)
            .setDeviceIP(ipAddr)
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
                if(imageCapture == null) imageCapture = getImageCapture()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview)
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
            if(listener.isListening.get()){
                listener.stop()
            }
            initialized.set(false)
        }
        super.onDestroy()
    }

    companion object {
        const val SORTING_MODE_PREFERENCE_KEY: String = "SORTER_MODE_PREFERENCE"
        const val STOP_CAPTURE_RUN_PREFERENCE: Int = 0
        const val CONTINUOUS_MOVE_PREFERENCE: Int = 1
        const val RUN_CONVEYOR_TIME_PREFERENCE_KEY: String = "RUN_CONVEYOR_TIME_VALUE"
        const val CONVEYOR_SPEED_VALUE_PREFERENCE_KEY: String = "SORTER_CONVEYOR_SPEED_VALUE"
        const val LISTENER_PORT = 50052
    }
}
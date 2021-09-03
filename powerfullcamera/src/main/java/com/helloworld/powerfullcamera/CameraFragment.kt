/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.helloworld.powerfullcamera

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ProgressDialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.helloworld.powerfullcamera.databinding.PowerCameraUiContainerBinding
import com.helloworld.powerfullcamera.databinding.PowerFragmentCameraBinding
import com.helloworld.powerfullcamera.event.DeleteEvent
import com.helloworld.powerfullcamera.extension.ANIMATION_FAST_MILLIS
import com.helloworld.powerfullcamera.extension.ANIMATION_SLOW_MILLIS
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import top.zibin.luban.Luban
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("NotifyDataSetChanged")
class CameraFragment : Fragment() {

  private var _fragmentCameraBinding: PowerFragmentCameraBinding? = null

  private val fragmentCameraBinding get() = _fragmentCameraBinding!!

  private var cameraUiContainerBinding: PowerCameraUiContainerBinding? = null

  private lateinit var outputDirectory: File
  private lateinit var broadcastManager: LocalBroadcastManager

  private var displayId: Int = -1
  private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
  private var preview: Preview? = null
  private var imageCapture: ImageCapture? = null
  private var imageAnalyzer: ImageAnalysis? = null
  private var camera: Camera? = null
  private var cameraProvider: ProcessCameraProvider? = null
  private lateinit var windowManager: WindowManager
  private lateinit var progressDialog: ProgressDialog

  private var photos = mutableListOf<File>()

  private val displayManager by lazy {
    requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
  }

  private lateinit var photoAdapter: TakePhotosListAdapter

  private lateinit var cameraExecutor: ExecutorService


  private val displayListener = object : DisplayManager.DisplayListener {
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onDisplayChanged(displayId: Int) = view?.let { view ->
      if (displayId == this@CameraFragment.displayId) {
        Log.d(TAG, "Rotation changed: ${view.display.rotation}")
        imageCapture?.targetRotation = view.display.rotation
        imageAnalyzer?.targetRotation = view.display.rotation
      }
    } ?: Unit
  }

  var touchHelper: ItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
    override fun getMovementFlags(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder
    ): Int {
      val dragFrlg = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
      return makeMovementFlags(dragFrlg,0)
    }

    override fun onMove(
      recyclerView: RecyclerView,
      viewHolder: RecyclerView.ViewHolder,
      target: RecyclerView.ViewHolder
    ): Boolean {
      //得到当拖拽的viewHolder的Position
      val fromPosition = viewHolder.adapterPosition
      //拿到当前拖拽到的item的viewHolder
      val toPosition = target.adapterPosition
      if (fromPosition < toPosition) {
        for (i in fromPosition until toPosition){
          Collections.swap(photos, i, i + 1)
        }
      } else {
        for (i in fromPosition downTo toPosition +1){
          Collections.swap(photos, i, i - 1)
        }
      }
      photoAdapter.notifyItemMoved(fromPosition, toPosition)
      return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
    }

    override fun isLongPressDragEnabled(): Boolean = true

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
      super.clearView(recyclerView, viewHolder)
      photoAdapter.notifyDataSetChanged()
    }

  })

  override fun onDestroyView() {
    _fragmentCameraBinding = null
    super.onDestroyView()

    cameraExecutor.shutdown()

    displayManager.unregisterDisplayListener(displayListener)

    EventBus.getDefault().unregister(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _fragmentCameraBinding = PowerFragmentCameraBinding.inflate(inflater, container, false)

    EventBus.getDefault().register(this)

    return fragmentCameraBinding.root
  }

  @SuppressLint("MissingPermission")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    progressDialog = ProgressDialog(requireContext()).apply {
      setMessage("压缩中..")
    }
    cameraExecutor = Executors.newSingleThreadExecutor()

    broadcastManager = LocalBroadcastManager.getInstance(view.context)

    displayManager.registerDisplayListener(displayListener, null)

    windowManager = view.context.getSystemService(Service.WINDOW_SERVICE) as WindowManager

    outputDirectory = getOutputDirectory(requireContext())

    Log.d(TAG, "$outputDirectory")

    fragmentCameraBinding.viewFinder.post {

      displayId = fragmentCameraBinding.viewFinder.display.displayId

      updateCameraUi()

      setUpCamera()

      setupList()
    }
  }

  private fun setupList() {
    photoAdapter = TakePhotosListAdapter(photos).apply {
      setOnItemChildClickListener { _, view, position ->
        when (view.id) {
          R.id.action_delete -> {
            photos[position].delete()
            remove(position)
            refreshConfirmState()
          }
          R.id.iv_photo -> {
            startActivity(Intent(requireContext(), GalleryActivity::class.java).apply {
              putExtra("rootDirectory", outputDirectory.absolutePath)
              putExtra("index", position)
            })
          }
        }
      }
    }
    cameraUiContainerBinding?.catchListView?.let {
      it.adapter = photoAdapter
      it.layoutManager =
        LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
      touchHelper.attachToRecyclerView(it)
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)

    bindCameraUseCases()
  }

  private fun setUpCamera() {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
    cameraProviderFuture.addListener(Runnable {
      cameraProvider = cameraProviderFuture.get()

      lensFacing = when {
        hasBackCamera() -> CameraSelector.LENS_FACING_BACK
        hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
        else -> throw IllegalStateException("Back and front camera are unavailable")
      }

      bindCameraUseCases()
    }, ContextCompat.getMainExecutor(requireContext()))
  }

  private fun bindCameraUseCases() {

    val metrics =  DisplayMetrics()
    windowManager.defaultDisplay.getRealMetrics(metrics)
    Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

    val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
    Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

    val rotation = fragmentCameraBinding.viewFinder.display.rotation

    val cameraProvider = cameraProvider
      ?: throw IllegalStateException("Camera initialization failed.")

    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

    preview = Preview.Builder()
      .setTargetAspectRatio(screenAspectRatio)
      .setTargetRotation(rotation)
      .build()

    imageCapture = ImageCapture.Builder()
      .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
      .setTargetAspectRatio(screenAspectRatio)
      .setTargetRotation(rotation)
      .build()

    cameraProvider.unbindAll()

    try {
      camera = cameraProvider.bindToLifecycle(
        this, cameraSelector, preview, imageCapture
      )

      preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
    } catch (exc: Exception) {
      Log.e(TAG, "Use case binding failed", exc)
    }
  }

  private fun aspectRatio(width: Int, height: Int): Int {
    val previewRatio = max(width, height).toDouble() / min(width, height)
    if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
      return AspectRatio.RATIO_4_3
    }
    return AspectRatio.RATIO_16_9
  }

  private fun updateCameraUi() {

    cameraUiContainerBinding?.root?.let {
      (fragmentCameraBinding.root as ViewGroup).removeView(it)
    }

    cameraUiContainerBinding = PowerCameraUiContainerBinding.inflate(
      LayoutInflater.from(requireContext()),
      fragmentCameraBinding.root as ViewGroup,
      true
    )

    val compresses = arrayListOf<String>()
    val handler = Handler(Looper.getMainLooper(), Handler.Callback {
      when(it.what){
        0 -> {
          progressDialog.dismiss()
          requireActivity().setResult(Activity.RESULT_OK, Intent().apply {
            putExtra(RESULT_KEY, compresses)
          })
          Log.d(TAG,"删除状态：${outputDirectory.deleteRecursively()}")
          requireActivity().finish()
        }
      }
      true
    })
    cameraUiContainerBinding?.actionConfirm?.setOnClickListener {
      progressDialog.show()
      cameraExecutor.execute {
        compresses.clear()
        val files = Luban.with(requireContext()).ignoreBy(100).setTargetDir(outputDirectory.parent).load(photos).get()
        compresses.addAll(files.map { it.absolutePath }.toMutableList())
        handler.sendEmptyMessage(0)
      }
    }

    cameraUiContainerBinding?.cameraCaptureButton?.setOnClickListener {

      imageCapture?.let { imageCapture ->

        val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)

        val metadata = Metadata().apply {

          isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
          .setMetadata(metadata)
          .build()

        imageCapture.takePicture(
          outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
              Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
              val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
              Log.d(TAG, "Photo capture succeeded: $savedUri")
              Log.d(TAG, "Photo capture succeeded: ${photoFile.absoluteFile}")
              Log.d(TAG, "Photo capture succeeded: ${photoFile.canonicalPath}")

              lifecycleScope.launch {
                if (photos.size >= DEFAULT_MAX_PHOTOS) {
                  Toast.makeText(requireContext(), "拍太多了..", Toast.LENGTH_SHORT).show()
                } else {
                  photos.add(photoFile)
                  photoAdapter.notifyItemInserted(photos.size - 1)
                  cameraUiContainerBinding?.catchListView?.smoothScrollToPosition(photos.size - 1)
                  refreshConfirmState()
                }
              }
            }
          })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

          fragmentCameraBinding.root.postDelayed({
            fragmentCameraBinding.root.foreground = ColorDrawable(Color.WHITE)
            fragmentCameraBinding.root.postDelayed(
              { fragmentCameraBinding.root.foreground = null }, ANIMATION_FAST_MILLIS
            )
          }, ANIMATION_SLOW_MILLIS)
        }
      }
    }

  }

  private fun refreshConfirmState() {
    if (photos.isNotEmpty()) {
      cameraUiContainerBinding?.actionConfirm?.visibility = View.VISIBLE
    } else {
      cameraUiContainerBinding?.actionConfirm?.visibility = View.GONE
    }
  }

  private fun hasBackCamera(): Boolean {
    return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
  }

  private fun hasFrontCamera(): Boolean {
    return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
  }

  companion object {

    const val RESULT_KEY = "result-key"
    const val TAG = "Powerful"
    const val DEFAULT_MAX_PHOTOS = 4
    private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val PHOTO_EXTENSION = ".jpg"
    private const val RATIO_4_3_VALUE = 4.0 / 3.0
    private const val RATIO_16_9_VALUE = 16.0 / 9.0

    private fun createFile(baseFolder: File, format: String, extension: String) =
      File(
        baseFolder, SimpleDateFormat(format, Locale.US)
          .format(System.currentTimeMillis()) + extension
      )

    fun getOutputDirectory(context: Context): File {
      val appContext = context.applicationContext
      val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
        File(it, UUID.randomUUID().toString()).apply { mkdirs() }
      }
      return if (mediaDir != null && mediaDir.exists())
        mediaDir else appContext.filesDir
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onMessage(event: DeleteEvent) {
    photoAdapter.remove(event.index)
    refreshConfirmState()
  }
}

class TakePhotosListAdapter(photos: MutableList<File>) :
  BaseQuickAdapter<File, BaseViewHolder>(R.layout.power_item_photo, photos) {
  override fun convert(helper: BaseViewHolder, item: File?) {
    helper.addOnClickListener(R.id.action_delete, R.id.iv_photo)
    Glide.with(helper.itemView)
      .load(item)
      .into(helper.getView(R.id.iv_photo))
  }
}
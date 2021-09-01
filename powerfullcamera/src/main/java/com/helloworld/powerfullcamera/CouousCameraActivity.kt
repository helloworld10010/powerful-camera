package com.helloworld.powerfullcamera

import androidx.fragment.app.Fragment

class CouousCameraActivity : BaseActivity() {
  override fun fragment(): Fragment = CameraFragment().apply {
    arguments = intent.extras
  }
}
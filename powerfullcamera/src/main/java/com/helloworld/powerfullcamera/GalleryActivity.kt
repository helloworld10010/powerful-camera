package com.helloworld.powerfullcamera

import androidx.fragment.app.Fragment

class GalleryActivity : BaseActivity(){

  override fun fragment(): Fragment = GalleryFragment().apply {
    arguments = intent.extras
  }
}
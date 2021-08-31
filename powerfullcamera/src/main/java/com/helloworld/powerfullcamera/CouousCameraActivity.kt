package com.helloworld.powerfullcamera

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.helloworld.powerfullcamera.CameraFragment
import com.helloworld.powerfullcamera.databinding.ActivityMainBinding

class CouousCameraActivity : BaseActivity() {
  override fun fragment(): Fragment = CameraFragment().apply {
    arguments = intent.extras
  }
}
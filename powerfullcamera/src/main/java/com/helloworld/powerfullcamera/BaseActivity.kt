package com.helloworld.powerfullcamera

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.helloworld.powerfullcamera.databinding.PowerActivityMainBinding

abstract class BaseActivity : AppCompatActivity() {

  private lateinit var activityMainBinding: PowerActivityMainBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityMainBinding = PowerActivityMainBinding.inflate(layoutInflater)
    setContentView(activityMainBinding.root)

    supportFragmentManager.beginTransaction()
      .replace(R.id.fragment_container,fragment())
      .commit()

  }

  override fun onResume() {
    super.onResume()
    activityMainBinding.fragmentContainer.postDelayed( {
      hideSystemUI()
    },500)
  }

  private fun hideSystemUI() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, activityMainBinding.fragmentContainer).let { controller ->
      controller.hide(WindowInsetsCompat.Type.systemBars())
      controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  abstract fun fragment() : Fragment
}
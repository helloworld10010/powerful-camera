package com.helloworld.powerfulcamera

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.helloworld.powerfulcamera.databinding.ActivityMainSampleBinding
//import com.helloworld.powerfullcamera.CouousCameraActivity

class MainActivity : AppCompatActivity() {

  lateinit var binding:ActivityMainSampleBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainSampleBinding.inflate(layoutInflater)
    setContentView(binding.root)

    binding.jump.setOnClickListener {
//      startActivity(Intent(this,CouousCameraActivity::class.java))
    }

  }
}
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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import java.io.File
import android.os.Build
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.helloworld.powerfullcamera.databinding.FragmentGalleryBinding
import com.helloworld.powerfullcamera.event.DeleteEvent
import com.helloworld.powerfullcamera.extension.padWithDisplayCutout
import org.greenrobot.eventbus.EventBus
import java.util.Locale

val EXTENSION_WHITELIST = arrayOf("JPG")

class GalleryFragment internal constructor() : Fragment() {

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null

    private val fragmentGalleryBinding get() = _fragmentGalleryBinding!!

    private lateinit var mediaList: MutableList<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        retainInstance = true

        val rootDirectory = File(arguments?.getString("rootDirectory")?:"")

        mediaList = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
        }?.toMutableList() ?: mutableListOf()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding = FragmentGalleryBinding.inflate(inflater, container, false)
        return fragmentGalleryBinding.root
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (mediaList.isEmpty()) {
            fragmentGalleryBinding.deleteButton.isEnabled = false
            fragmentGalleryBinding.shareButton.isEnabled = false
        }

        fragmentGalleryBinding.photoViewPager.apply {
            adapter = PicturesLookAdapter(mediaList)
            setCurrentItem(arguments?.getInt("index")?:0,false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            fragmentGalleryBinding.cutoutSafeArea.padWithDisplayCutout()
        }

        fragmentGalleryBinding.backButton.setOnClickListener {
            requireActivity().finish()
        }

        fragmentGalleryBinding.deleteButton.setOnClickListener {

            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)?.let { mediaFile ->

                AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                        .setMessage(getString(R.string.delete_dialog))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(R.string.yes) { _, _ ->

                            // Delete current photo
                            if(mediaFile.delete()){
                                EventBus.getDefault().post(DeleteEvent(fragmentGalleryBinding.photoViewPager.currentItem))
                            }

                            mediaList.removeAt(fragmentGalleryBinding.photoViewPager.currentItem)
                            fragmentGalleryBinding.photoViewPager.adapter?.notifyDataSetChanged()


                            if (mediaList.isEmpty()) {
                                requireActivity().finish()
                            }

                        }
                        .setNegativeButton(android.R.string.no, null)
                        .create().show()
            }
        }
    }

    override fun onDestroyView() {
        _fragmentGalleryBinding = null
        super.onDestroyView()
    }
}

class PicturesLookAdapter(imgPaths:List<File>)
    : BaseQuickAdapter<File, BaseViewHolder>(R.layout.item_check_img,imgPaths){

    override fun convert(helper: BaseViewHolder, item: File) {
      Glide.with(mContext).load(item).into(helper.getView(R.id.photo_view))
    }
}

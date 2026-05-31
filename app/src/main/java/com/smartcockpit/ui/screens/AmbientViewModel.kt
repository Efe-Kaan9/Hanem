package com.smartcockpit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcockpit.data.local.GalleryEntity
import com.smartcockpit.data.local.dao.GalleryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AmbientViewModel @Inject constructor(
    private val galleryDao: GalleryDao
) : ViewModel() {

    val images: StateFlow<List<GalleryEntity>> = galleryDao.getAllImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addImages(uris: List<String>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                galleryDao.addImage(GalleryEntity(imageUri = uri))
            }
        }
    }

    fun removeImage(image: GalleryEntity) {
        viewModelScope.launch {
            galleryDao.removeImage(image)
        }
    }
}

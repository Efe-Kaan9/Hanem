package com.smartcockpit.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartcockpit.data.local.GalleryEntity
import com.smartcockpit.data.local.dao.GalleryDao
import com.smartcockpit.os.KioskManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AmbientViewModel @Inject constructor(
    private val galleryDao: GalleryDao,
    private val kioskManager: KioskManager
) : ViewModel() {

    val images: StateFlow<List<GalleryEntity>> = galleryDao.getAllImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex

    init {
        viewModelScope.launch {
            kioskManager.ambientImageIndex.collectLatest { index ->
                _currentImageIndex.value = index
            }
        }
    }

    fun updateImageIndex(index: Int) {
        viewModelScope.launch {
            _currentImageIndex.value = index
            kioskManager.saveAmbientImageIndex(index)
        }
    }

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

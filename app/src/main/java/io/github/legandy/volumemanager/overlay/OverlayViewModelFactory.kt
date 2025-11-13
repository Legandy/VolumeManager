package io.github.legandy.volumemanager.overlay

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel

@Suppress("UNCHECKED_CAST")
class OverlayViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OverlayViewModel::class.java)) {
            return OverlayViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
package io.github.legandy.volumemixerpanel.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.legandy.volumemixerpanel.core.MyApplication

class SetupViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        // Get the Application object from extras
        val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        // Create a SavedStateHandle for this ViewModel from extras
        val savedStateHandle = extras.createSavedStateHandle()

        return SetupViewModel(
            application,
            savedStateHandle,
            MyApplication.setupDataStore
        ) as T
    }
}
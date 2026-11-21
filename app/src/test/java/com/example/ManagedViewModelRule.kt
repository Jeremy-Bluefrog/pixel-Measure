package com.example

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.ui.viewmodel.MeasureViewModel
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.atomic.AtomicInteger

/**
 * JUnit rule that binds test-created [MeasureViewModel] instances to a [ViewModelStore]
 * and clears the store on teardown so [MeasureViewModel.onCleared] stops background threads,
 * thermal monitors, receivers, and coroutine scopes deterministically between tests.
 */
class ManagedViewModelRule : TestWatcher() {
    private val store = ViewModelStore()
    private val counter = AtomicInteger(0)

    fun create(app: Application): MeasureViewModel {
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        val key = "test_measure_vm_${counter.getAndIncrement()}"
        return ViewModelProvider(store, factory)[key, MeasureViewModel::class.java]
    }

    override fun finished(description: Description) {
        store.clear()
        if (Snapshot.current.hasPendingChanges()) {
            Snapshot.sendApplyNotifications()
        }
    }
}

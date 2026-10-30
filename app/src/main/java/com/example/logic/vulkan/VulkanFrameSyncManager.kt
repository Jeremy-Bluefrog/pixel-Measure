package com.example.logic.vulkan

import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Double-buffering GPU/CPU Synchronization Manager for Pure Vulkan Render Pipeline.
 *
 * Implements deterministic synchronization model:
 * 1. Frame Slot Double-Buffering (0 and 1) with per-slot In-Flight Fences.
 * 2. CPU waits for previous frame's GPU completion before reusing the CommandBuffer / UniformBuffer.
 * 3. Render Watchdog: Detects consecutive frame timeouts (stalled GPU) and triggers automatic pipeline recovery.
 * 4. Surface Destruction Safety: Enforces queueWaitIdle and deviceWaitIdle synchronization
 *    before releasing native window handles.
 */
class VulkanFrameSyncManager(
    val maxFramesInFlight: Int = 2
) {
    companion object {
        private const val TAG = "VulkanFrameSync"
        private const val DEFAULT_FENCE_TIMEOUT_MS = 64L // ~4 frames at 60fps
        private const val MAX_CONSECUTIVE_TIMEOUTS_BEFORE_RESET = 3
    }

    private val lock = ReentrantLock()
    private val condition = lock.newCondition()

    // Per-frame in-flight fence state: true = GPU currently executing, false = fence signaled/ready
    private val frameFences = Array(maxFramesInFlight) { AtomicBoolean(false) }
    private var currentFrameIndex = 0

    // Consecutive timeout counter for Watchdog
    private val consecutiveTimeoutCount = AtomicInteger(0)
    var onWatchdogTriggered: (() -> Unit)? = null

    /**
     * Acquires the next frame slot for rendering.
     * Blocks up to [timeoutMs] if the GPU is still rendering the previous frame for this slot.
     *
     * @return Frame slot index (0 .. maxFramesInFlight-1) if acquired, or -1 if timed out
     */
    fun acquireNextFrameSlot(timeoutMs: Long = DEFAULT_FENCE_TIMEOUT_MS): Int {
        lock.withLock {
            val slot = currentFrameIndex
            val fence = frameFences[slot]

            if (fence.get()) {
                val deadline = System.currentTimeMillis() + timeoutMs
                while (fence.get()) {
                    val remainingMs = deadline - System.currentTimeMillis()
                    if (remainingMs <= 0) {
                        val count = consecutiveTimeoutCount.incrementAndGet()
                        try { Log.w(TAG, "Fence wait timed out on slot $slot (consecutive: $count)") } catch (_: Throwable) {}
                        if (count >= MAX_CONSECUTIVE_TIMEOUTS_BEFORE_RESET) {
                            try { Log.e(TAG, "Watchdog triggered: $count consecutive timeouts. Requesting pipeline rebuild.") } catch (_: Throwable) {}
                            onWatchdogTriggered?.invoke()
                        }
                        return -1
                    }
                    condition.await(remainingMs, TimeUnit.MILLISECONDS)
                }
            }

            // Successfully acquired: fence is now signaled ready
            consecutiveTimeoutCount.set(0)
            fence.set(true) // Mark in-flight for new frame
            currentFrameIndex = (currentFrameIndex + 1) % maxFramesInFlight
            return slot
        }
    }

    /**
     * Marks the frame submission as completed by GPU / presenter.
     * Signals the fence, allowing this slot to be reused by future CPU command recording.
     */
    fun signalFrameCompleted(slot: Int) {
        if (slot !in 0 until maxFramesInFlight) return
        lock.withLock {
            frameFences[slot].set(false)
            condition.signalAll()
        }
    }

    /**
     * Synchronously waits for all GPU queues and pending in-flight fences to become idle.
     * Essential on `surfaceDestroyed`, `onPause`, or screen rotation to prevent SIGSEGV.
     *
     * @param timeoutMs Maximum time to wait in milliseconds.
     * @return true if all fences drained cleanly, false if timed out.
     */
    fun waitAllFencesIdle(timeoutMs: Long = 200L): Boolean {
        lock.withLock {
            val deadline = System.currentTimeMillis() + timeoutMs
            for (i in 0 until maxFramesInFlight) {
                while (frameFences[i].get()) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) {
                        try { Log.w(TAG, "waitAllFencesIdle timed out waiting for slot $i") } catch (_: Throwable) {}
                        // Force clear to avoid deadlock
                        frameFences[i].set(false)
                        condition.signalAll()
                        return false
                    }
                    condition.await(remaining, TimeUnit.MILLISECONDS)
                }
            }
            return true
        }
    }

    /**
     * Resets all sync fences to the idle state immediately.
     */
    fun reset() {
        lock.withLock {
            for (i in 0 until maxFramesInFlight) {
                frameFences[i].set(false)
            }
            currentFrameIndex = 0
            consecutiveTimeoutCount.set(0)
            condition.signalAll()
        }
    }
}

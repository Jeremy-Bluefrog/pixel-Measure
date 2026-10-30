package com.example.logic.vulkan

/**
 * Deterministic Vulkan Hardware Render Pipeline Lifecycle States.
 * Strict state machine ensuring zero native window race conditions,
 * driver deadlocks, or SIGSEGV crashes during rotation, backgrounding, or surface rebuilds.
 */
enum class VulkanRenderState {
    /** Initial uninitialized state before any SurfaceHolder is attached */
    UNINITIALIZED,

    /** Surface has been created and valid native window attached */
    SURFACE_ATTACHED,

    /** Swapchain extents (width > 0, height > 0), render pass, and framebuffers configured */
    SWAPCHAIN_CONFIGURED,

    /** Frame loop active with Choreographer and continuous hardware rendering */
    ACTIVE_RENDER_LOOP,

    /** Surface destroyed or app backgrounded; GPU queues waited idle and resources frozen safely */
    SAFE_SUSPENDED,

    /** Surface configuration change or rotation occurred; teardown old swapchain and rebuild */
    REBUILDING_PIPELINE;

    /**
     * Verifies if this state allows active frame submission to the GPU.
     */
    val canRender: Boolean
        get() = this == ACTIVE_RENDER_LOOP || this == SWAPCHAIN_CONFIGURED
}

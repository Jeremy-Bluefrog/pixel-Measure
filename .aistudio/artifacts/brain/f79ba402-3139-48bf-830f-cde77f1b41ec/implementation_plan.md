# Implementation Plan - Enhancing Application Stability & Fluidity (Modern Best Practices)

Based on recent Android & Jetpack Compose architecture standards (Strong Skipping Mode, Phase-Aware State Reads, and Zero-Allocation Rendering), we will implement the following optimizations:

## 1. Phase-Aware State Reads & Lambda Modifiers
- **Avoid Composition Phase Invalidation**: Ensure that high-frequency state updates (such as orientation angles and camera view matrices) only read or react during the **Drawing** or **Layout** phase rather than triggering full composable tree recompositions.
- **`derivedStateOf` Integration**: Wrap frequently changing measurement summaries and position thresholds in `derivedStateOf` to prevent unnecessary UI thread dispatches.

## 2. Zero-Allocation Render Loops in AR Canvas Overlays
- **Eliminate Heap Churn**: Building on our `ProjectedPointPool` implementation, ensure that all 3D-to-2D projection steps, area centroids, and path resets reuse pre-allocated primitive arrays (`FloatArray`, `ScreenPoint`) to eliminate garbage collection (`GC_FOR_ALLOC`) pauses.

## 3. Robust Session Recovery & Exception Boundaries
- **ARCore Lifecycle Safety**: Protect AR session initialization, camera open/close transitions, and sensor registrations with fault-tolerant `try-catch` blocks and automatic graceful fallback to sensor-driven spatial mode if camera/ARCore frames drop or fail.

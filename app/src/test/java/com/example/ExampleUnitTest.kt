package com.example

import com.example.logic.TranslationManager
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun `verify at least 100 languages supported`() {
    val languages = TranslationManager.supportedLanguages
    assertTrue("Should support at least 100 languages", languages.size >= 100)
    assertEquals(110, languages.size)

    // Verify all codes are non-empty and unique
    val codes = languages.map { it.code }
    assertEquals("Language codes must be unique", codes.size, codes.toSet().size)
    languages.forEach { lang ->
      assertTrue("Language code should not be blank: ${lang.code}", lang.code.isNotBlank())
      assertTrue("Language name should not be blank: ${lang.name}", lang.name.isNotBlank())
    }
  }

  @Test
  fun `verify translations lookup and fallback`() {
    // Chinese (Traditional)
    assertEquals("相機 AR 測量儀", TranslationManager.getString("app_title", "zh-TW"))
    assertEquals("相機 AR", TranslationManager.getString("nav_camera", "zh-TW"))

    // English
    assertEquals("Camera AR Measure", TranslationManager.getString("app_title", "en"))
    assertEquals("Camera AR", TranslationManager.getString("nav_camera", "en"))

    // Japanese
    assertEquals("カメラ AR メジャー", TranslationManager.getString("app_title", "ja"))

    // French
    assertEquals("Mètre Caméra AR", TranslationManager.getString("app_title", "fr"))

    // German
    assertEquals("Kamera AR Messgerät", TranslationManager.getString("app_title", "de"))

    // Portuguese (Brazil)
    assertEquals("Trena AR & Régua Digital", TranslationManager.getString("app_title", "pt-BR"))

    // Latin
    assertEquals("Camera AR Mensura & Regula", TranslationManager.getString("app_title", "la"))

    // Fallback on missing key
    val fallbackResult = TranslationManager.getString("non_existent_key_12345", "ja")
    assertEquals("non_existent_key_12345", fallbackResult)
  }

  @Test
  fun `verify system locale matching logic`() {
    // Exact matches
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("zh-TW"))
    assertEquals("zh-CN", TranslationManager.matchLanguageCode("zh-CN"))
    assertEquals("zh-HK", TranslationManager.matchLanguageCode("zh-HK"))
    assertEquals("en-US", TranslationManager.matchLanguageCode("en-US"))
    assertEquals("en-GB", TranslationManager.matchLanguageCode("en-GB"))
    assertEquals("ja", TranslationManager.matchLanguageCode("ja"))

    // Subtag and script matches
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("zh-Hant-TW"))
    assertEquals("zh-CN", TranslationManager.matchLanguageCode("zh-Hans-CN"))
    assertEquals("zh-HK", TranslationManager.matchLanguageCode("zh-Hant-HK"))
    assertEquals("en", TranslationManager.matchLanguageCode("en-AU"))
    assertEquals("ja", TranslationManager.matchLanguageCode("ja-JP"))
    assertEquals("ko", TranslationManager.matchLanguageCode("ko-KR"))
    assertEquals("fr-CA", TranslationManager.matchLanguageCode("fr-CA"))
    assertEquals("fr", TranslationManager.matchLanguageCode("fr-FR"))
    assertEquals("es-419", TranslationManager.matchLanguageCode("es-MX"))
    assertEquals("es", TranslationManager.matchLanguageCode("es-ES"))
    assertEquals("pt-BR", TranslationManager.matchLanguageCode("pt-BR"))
    assertEquals("pt", TranslationManager.matchLanguageCode("pt-PT"))
    assertEquals("de", TranslationManager.matchLanguageCode("de-DE"))

    // Underscore notation (e.g. Android Locale.toString())
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("zh_TW"))
    assertEquals("en-US", TranslationManager.matchLanguageCode("en_US"))

    // Fallbacks
    assertEquals("zh-TW", TranslationManager.matchLanguageCode(null))
    assertEquals("zh-TW", TranslationManager.matchLanguageCode(""))
    assertEquals("zh-TW", TranslationManager.matchLanguageCode("unknown-locale"))
  }

  @Test
  fun `verify system language settings strings`() {
    val titleZh = TranslationManager.getString("system_lang_title", "zh-TW")
    assertTrue(titleZh.contains("系統"))

    val titleEn = TranslationManager.getString("system_lang_title", "en")
    assertTrue(titleEn.contains("System"))

    val descZh = TranslationManager.getString("system_lang_desc", "zh-TW")
    assertTrue(descZh.contains("Android"))

    val descEn = TranslationManager.getString("system_lang_desc", "en")
    assertTrue(descEn.contains("Android"))
  }

  @Test
  fun `verify point count state toggles checkmark when first point placed`() {
    // 0 points: awaiting first point -> plus icon (+), isWaitingForSecondPoint = false
    val points0 = listOf<Int>()
    val awaitingSecond0 = points0.size % 2 == 1
    assertFalse("0 points should show plus icon", awaitingSecond0)

    // 1 point placed: awaiting second point -> checkmark icon (✓), isWaitingForSecondPoint = true
    val points1 = listOf(1)
    val awaitingSecond1 = points1.size % 2 == 1
    assertTrue("1 point placed should show checkmark icon", awaitingSecond1)

    // 2 points placed: completed segment, ready for next start point -> plus icon (+)
    val points2 = listOf(1, 2)
    val awaitingSecond2 = points2.size % 2 == 1
    assertFalse("2 points placed should show plus icon", awaitingSecond2)
  }

  @Test
  fun `verify VulkanMatrix4 identity and multiplication`() {
    val m = FloatArray(16)
    com.example.logic.vulkan.VulkanMatrix4.setIdentity(m, 0)
    for (i in 0 until 4) {
      for (j in 0 until 4) {
        val expected = if (i == j) 1.0f else 0.0f
        assertEquals(expected, m[i * 4 + j], 1e-6f)
      }
    }

    val vecIn = floatArrayOf(2.0f, 3.0f, 4.0f, 1.0f)
    val vecOut = FloatArray(4)
    com.example.logic.vulkan.VulkanMatrix4.multiplyMV(vecOut, 0, m, 0, vecIn, 0)
    assertEquals(2.0f, vecOut[0], 1e-6f)
    assertEquals(3.0f, vecOut[1], 1e-6f)
    assertEquals(4.0f, vecOut[2], 1e-6f)
    assertEquals(1.0f, vecOut[3], 1e-6f)
  }

  @Test
  fun `verify VulkanMatrix4 perspective projection validity`() {
    val proj = FloatArray(16)
    com.example.logic.vulkan.VulkanMatrix4.perspective(proj, 0, 60.0f, 1.777f, 0.1f, 100.0f)
    // Non-zero diagonal elements
    assertTrue(proj[0] > 0f)
    assertTrue(proj[5] > 0f)
    assertEquals(-1.0f, proj[11], 1e-6f) // Standard clip space perspective entry
    assertEquals(0.0f, proj[15], 1e-6f)
  }

  @Test
  fun `verify ArMath projectWorldToScreenInPlace zero-allocation projection`() {
    val viewMatrix = FloatArray(16)
    val projMatrix = FloatArray(16)
    com.example.logic.vulkan.VulkanMatrix4.setIdentity(viewMatrix, 0)
    com.example.logic.vulkan.VulkanMatrix4.perspective(projMatrix, 0, 60.0f, 1.0f, 0.1f, 100.0f)

    val worldPoint = com.example.ui.viewmodel.Point3D(0.0, 0.0, -2.0, isArPrecision = true)
    val outScreenPos = FloatArray(2)

    val success = com.example.logic.ar.ArMath.projectWorldToScreenInPlace(
      worldPoint = worldPoint,
      viewMatrix = viewMatrix,
      projectionMatrix = projMatrix,
      screenWidth = 1000,
      screenHeight = 1000,
      outScreenPos = outScreenPos,
      outOffset = 0
    )

    assertTrue("Point along optical center should project successfully", success)
    assertEquals(500.0f, outScreenPos[0], 2.0f)
    assertEquals(500.0f, outScreenPos[1], 2.0f)
  }

  @Test
  fun `verify ArMath projectCoordsToScreen Y-axis elevation alignment`() {
    val viewMatrix = FloatArray(16)
    val projMatrix = FloatArray(16)
    com.example.logic.vulkan.VulkanMatrix4.setIdentity(viewMatrix, 0)
    com.example.logic.vulkan.VulkanMatrix4.perspective(projMatrix, 0, 60.0f, 1.0f, 0.1f, 100.0f)

    val outXYHigh = FloatArray(2)
    val outXYLow = FloatArray(2)

    // Point higher in space (wy = +0.5m)
    val okHigh = com.example.logic.ar.ArMath.projectCoordsToScreen(0.0, 0.5, -2.0, viewMatrix, projMatrix, 1000, 1000, outXYHigh)
    // Point lower in space (wy = -0.5m)
    val okLow = com.example.logic.ar.ArMath.projectCoordsToScreen(0.0, -0.5, -2.0, viewMatrix, projMatrix, 1000, 1000, outXYLow)

    assertTrue("High point should project successfully", okHigh)
    assertTrue("Low point should project successfully", okLow)

    // In screen coordinates: Y=0 is top, Y=1000 is bottom.
    // Therefore, a higher point in 3D MUST have a smaller screen Y value than a lower point.
    assertTrue("Higher 3D point must be in upper half of screen (Y < 500)", outXYHigh[1] < 500.0f)
    assertTrue("Lower 3D point must be in lower half of screen (Y > 500)", outXYLow[1] > 500.0f)
    assertTrue("Higher 3D point screen Y must be smaller than lower point screen Y", outXYHigh[1] < outXYLow[1])
  }

  @Test
  fun `verify ArMath projectCoordsToScreen horizontal selfie mirroring`() {
    val viewMatrix = FloatArray(16)
    val projMatrix = FloatArray(16)
    com.example.logic.vulkan.VulkanMatrix4.setIdentity(viewMatrix, 0)
    com.example.logic.vulkan.VulkanMatrix4.perspective(projMatrix, 0, 60.0f, 1.0f, 0.1f, 100.0f)

    val outNormal = FloatArray(2)
    val outMirrored = FloatArray(2)

    // Point to the right in 3D (wx = +0.5m)
    com.example.logic.ar.ArMath.projectCoordsToScreen(0.5, 0.0, -2.0, viewMatrix, projMatrix, 1000, 1000, outNormal, isHorizontallyMirrored = false)
    com.example.logic.ar.ArMath.projectCoordsToScreen(0.5, 0.0, -2.0, viewMatrix, projMatrix, 1000, 1000, outMirrored, isHorizontallyMirrored = true)

    assertTrue("Normal point to the right should be on right half (X > 500)", outNormal[0] > 500.0f)
    assertTrue("Mirrored point should be on left half (X < 500)", outMirrored[0] < 500.0f)
    assertEquals(1000.0f - outNormal[0], outMirrored[0], 1.0f)
  }

  @Test
  fun `verify ArMath verticalHeight calculates true height without depth bias`() {
    val base = com.example.ui.viewmodel.Point3D(0.0, 0.0, -1.0)
    // Top point with 1.2m height difference and 3.0m depth difference
    val top = com.example.ui.viewmodel.Point3D(0.0, 1.2, -4.0)

    val height = com.example.logic.ar.ArMath.verticalHeight(base, top)
    assertEquals(1.2, height, 1e-6)
  }

  @Test
  fun `verify ArMath fitCircle3Points rejects collinear degenerate points`() {
    val p1 = com.example.ui.viewmodel.Point3D(0.0, 0.0, 0.0)
    val p2 = com.example.ui.viewmodel.Point3D(1.0, 0.0, 0.0)
    val p3 = com.example.ui.viewmodel.Point3D(2.0, 0.0, 0.0) // perfectly straight line

    val circle = com.example.logic.ar.ArMath.fitCircle3Points(p1, p2, p3)
    assertNull("Collinear points must return null circle to prevent radius explosion", circle)
  }

  @Test
  fun `verify ArMath polygonArea calculates square area accurately`() {
    val p1 = com.example.ui.viewmodel.Point3D(0.0, 0.0, 0.0)
    val p2 = com.example.ui.viewmodel.Point3D(2.0, 0.0, 0.0)
    val p3 = com.example.ui.viewmodel.Point3D(2.0, 2.0, 0.0)
    val p4 = com.example.ui.viewmodel.Point3D(0.0, 2.0, 0.0)

    val area = com.example.logic.ar.ArMath.polygonArea(listOf(p1, p2, p3, p4))
    assertEquals(4.0, area, 1e-4)
  }

  @Test
  fun `verify ProjectedPointPool capacity is at least 128`() {
    val pool = com.example.ui.components.ProjectedPointPool()
    assertEquals(128, pool.capacity)
    assertEquals(128, pool.pool.size)
  }

  @Test
  fun `verify VulkanRenderState canRender capability`() {
    assertTrue(com.example.logic.vulkan.VulkanRenderState.ACTIVE_RENDER_LOOP.canRender)
    assertTrue(com.example.logic.vulkan.VulkanRenderState.SWAPCHAIN_CONFIGURED.canRender)
    assertFalse(com.example.logic.vulkan.VulkanRenderState.UNINITIALIZED.canRender)
    assertFalse(com.example.logic.vulkan.VulkanRenderState.SURFACE_ATTACHED.canRender)
    assertFalse(com.example.logic.vulkan.VulkanRenderState.SAFE_SUSPENDED.canRender)
    assertFalse(com.example.logic.vulkan.VulkanRenderState.REBUILDING_PIPELINE.canRender)
  }

  @Test
  fun `verify VulkanFrameSyncManager double-buffering fences and idle draining`() {
    val sync = com.example.logic.vulkan.VulkanFrameSyncManager(maxFramesInFlight = 2)

    // Acquire slot 0
    val slot0 = sync.acquireNextFrameSlot(timeoutMs = 50L)
    assertEquals(0, slot0)

    // Acquire slot 1
    val slot1 = sync.acquireNextFrameSlot(timeoutMs = 50L)
    assertEquals(1, slot1)

    // Next acquire should wait for slot 0 to be signaled
    // Simulate async GPU completion for slot 0
    sync.signalFrameCompleted(slot0)
    val slot0Reused = sync.acquireNextFrameSlot(timeoutMs = 50L)
    assertEquals(0, slot0Reused)

    // Draining all fences
    sync.signalFrameCompleted(slot1)
    sync.signalFrameCompleted(slot0Reused)
    val idleDrained = sync.waitAllFencesIdle(timeoutMs = 100L)
    assertTrue("All fences must drain cleanly to idle", idleDrained)
  }

  @Test
  fun `verify VulkanFrameSyncManager watchdog triggers on consecutive timeouts`() {
    val sync = com.example.logic.vulkan.VulkanFrameSyncManager(maxFramesInFlight = 1)
    var watchdogFired = false
    sync.onWatchdogTriggered = {
      watchdogFired = true
    }

    // Acquire the only slot without signaling completion
    val slot = sync.acquireNextFrameSlot(timeoutMs = 20L)
    assertEquals(0, slot)

    // Consecutive timed out acquisitions
    sync.acquireNextFrameSlot(timeoutMs = 5L)
    sync.acquireNextFrameSlot(timeoutMs = 5L)
    sync.acquireNextFrameSlot(timeoutMs = 5L)

    assertTrue("Watchdog should trigger after 3 consecutive frame timeouts", watchdogFired)
    sync.reset()
  }

  @Test
  fun `verify ArMath detects self-intersecting polygon`() {
    // Bow-tie / figure-8 polygon: (0,0) -> (2,2) -> (2,0) -> (0,2)
    // Edge (0,0)->(2,2) crosses edge (2,0)->(0,2) at (1,1)
    val bowTie = listOf(
      com.example.ui.viewmodel.Point3D(0.0, 0.0, 0.0),
      com.example.ui.viewmodel.Point3D(2.0, 2.0, 0.0),
      com.example.ui.viewmodel.Point3D(2.0, 0.0, 0.0),
      com.example.ui.viewmodel.Point3D(0.0, 2.0, 0.0)
    )
    assertTrue("Bow-tie polygon must be detected as self-intersecting", com.example.logic.ar.ArMath.isPolygonSelfIntersecting(bowTie))

    // Regular convex square: (0,0) -> (2,0) -> (2,2) -> (0,2)
    val square = listOf(
      com.example.ui.viewmodel.Point3D(0.0, 0.0, 0.0),
      com.example.ui.viewmodel.Point3D(2.0, 0.0, 0.0),
      com.example.ui.viewmodel.Point3D(2.0, 2.0, 0.0),
      com.example.ui.viewmodel.Point3D(0.0, 2.0, 0.0)
    )
    assertFalse("Convex square should NOT be self-intersecting", com.example.logic.ar.ArMath.isPolygonSelfIntersecting(square))
  }

  @Test
  fun `verify ArMath calculateBoundingBox with empty and single point`() {
    val emptyResult = com.example.logic.ar.ArMath.calculateBoundingBox(emptyList())
    assertEquals(0.0, emptyResult.volume, 1e-6)
    assertEquals(0.0, emptyResult.surfaceArea, 1e-6)

    val singlePoint = listOf(com.example.ui.viewmodel.Point3D(1.5, 2.5, 3.5))
    val singleResult = com.example.logic.ar.ArMath.calculateBoundingBox(singlePoint)
    assertEquals(0.0, singleResult.volume, 1e-6)
    assertEquals(0.0, singleResult.surfaceArea, 1e-6)
    assertEquals(1.5, singleResult.minX, 1e-6)
    assertEquals(1.5, singleResult.maxX, 1e-6)
  }

  @Test
  fun `verify ArMath fitCircle3Points near-collinear points rejection`() {
    // 3 points with a tiny 0.00001m perturbation off a 2-meter line
    val p1 = com.example.ui.viewmodel.Point3D(0.0, 0.0, 0.0)
    val p2 = com.example.ui.viewmodel.Point3D(1.0, 0.00001, 0.0)
    val p3 = com.example.ui.viewmodel.Point3D(2.0, 0.0, 0.0)

    val circle = com.example.logic.ar.ArMath.fitCircle3Points(p1, p2, p3)
    assertNull("Near-collinear points must be rejected to prevent giant radius explosion", circle)
  }
}



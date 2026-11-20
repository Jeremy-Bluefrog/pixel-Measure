package com.example

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.MeasureRecord
import com.example.ui.components.RulerComponent
import com.example.ui.screens.HistorySheetContent
import com.example.ui.screens.RecordDetailDialog
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.GoogleSansRounded
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Typography
import com.example.ui.theme.loadRoundedNativeTypeface
import com.example.ui.viewmodel.MeasureViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class Phase2PolishAndBehaviorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `rounded typography and native rounded typeface load cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val typeface = loadRoundedNativeTypeface(context, isBold = true)
        assertNotNull(typeface)
        assertEquals(GoogleSansRounded, Typography.bodyLarge.fontFamily)
        assertEquals(GoogleSansRounded, Typography.titleLarge.fontFamily)
    }

    @Test
    fun `MeasureViewModel rulerFontScale clamps persists and resets cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)

        vm.updateRulerFontScale(1.45f)
        assertEquals(1.45f, vm.rulerFontScale.value, 0.001f)

        vm.updateRulerFontScale(5.0f)
        assertEquals(1.8f, vm.rulerFontScale.value, 0.001f)

        vm.updateRulerFontScale(0.2f)
        assertEquals(0.8f, vm.rulerFontScale.value, 0.001f)

        vm.resetAllSettingsToDefault()
        assertEquals(1.0f, vm.rulerFontScale.value, 0.001f)
    }

    @Test
    fun `MeasureViewModel saveMeasurementRecord saves media-only video record when value is zero`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        vm.clearActivePoints()

        val tempVideo = File(app.cacheDir, "test_ar_video.mp4").apply { writeText("mp4") }
        var savedRecord: MeasureRecord? = null

        vm.saveMeasurementRecord(
            imagePath = null,
            videoPath = tempVideo.absolutePath,
            customTitle = "測試 AR 錄影紀錄"
        ) { rec ->
            savedRecord = rec
        }

        val deadline = System.currentTimeMillis() + 5000L
        while (savedRecord == null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        assertNotNull(savedRecord)
        assertEquals(tempVideo.absolutePath, savedRecord?.videoPath)
        assertEquals("測試 AR 錄影紀錄", savedRecord?.title)
        tempVideo.delete()
    }

    @Test
    fun `HistorySheetContent displays unified connected filters, excludes ruler and level, and shows video badge`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        val records = listOf(
            MeasureRecord(
                id = 1,
                title = "圓桌直徑",
                value = 1.2,
                unit = "m",
                type = "CIRCLE",
                videoPath = "/tmp/circle_clip.mp4"
            ),
            MeasureRecord(
                id = 2,
                title = "客廳牆長",
                value = 3.4,
                unit = "m",
                type = "DISTANCE"
            ),
            MeasureRecord(
                id = 3,
                title = "隱藏尺規歷史",
                value = 0.08,
                unit = "m",
                type = "RULER"
            ),
            MeasureRecord(
                id = 4,
                title = "隱藏水平儀歷史",
                value = 0.1,
                unit = "m",
                type = "LEVEL"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                HistorySheetContent(
                    records = records,
                    viewModel = vm,
                    onSelectRecord = {},
                    onClose = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_connected_filter_group").assertIsDisplayed()
        composeTestRule.onNodeWithText("AR 歷史測量紀錄 (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("圓桌直徑").assertIsDisplayed()
        composeTestRule.onNodeWithText("客廳牆長").assertIsDisplayed()
        // Both thumbnail overlay badge and type badge display "影片"
        composeTestRule.onAllNodesWithText("影片", useUnmergedTree = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText("隱藏尺規歷史").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("隱藏水平儀歷史").assertCountEquals(0)

        composeTestRule.onNodeWithTag("history_filter_circle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("圓桌直徑").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("客廳牆長").assertCountEquals(0)
    }

    @Test
    fun `RulerComponent uses edit button opening font size and calibration dialog without history`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                RulerComponent(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ruler_edit_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ruler_edit_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ruler_font_scale_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("ruler_calibration_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("ruler_history_button").assertCountEquals(0)
    }

    @Test
    fun `ModernArTopBar omits roll and pitch horizon indicators and shows detected planes pill`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                com.example.ui.components.ModernArTopBar(
                    viewModel = vm,
                    context = app,
                    subMode = 0,
                    autoDetectedType = "DISTANCE",
                    selectedUnit = "cm",
                    isTorchOn = false,
                    torchBrightness = 1f,
                    cameraAspectRatio = "FULL",
                    planesCount = 3,
                    isRecordingVideo = false,
                    recordingSeconds = 0,
                    onShowHistoryClick = {},
                    onShowSettingsClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ar_detected_planes_pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("已偵測表面").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("基準水平對齊 (0°)").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("水準對齊調整中").assertCountEquals(0)
    }

    @Test
    fun `SettingsScreen renders clean categories, opens sub-pages, and hides developer clutter`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_row_theme").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_row_unit").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_row_visual_style").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_row_camera_quality").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_row_ruler_edit").performScrollTo().assertIsDisplayed()

        // Ensure developer clutter was removed
        composeTestRule.onAllNodesWithTag("auto_battery_profile_switch").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("switch_vulkan_graphics").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("switch_barometer_fusion").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("switch_multisample_averaging").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("switch_coplanar_projection").assertCountEquals(0)

        // Open Theme Sub-Page and return via TopAppBar Back button
        composeTestRule.onNodeWithTag("settings_search_field").performScrollTo()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_row_theme").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_theme_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_option_dark").performClick()
        composeTestRule.waitForIdle()
        assertEquals("DARK", vm.themeMode.value)
        composeTestRule.onNodeWithTag("settings_subpage_back_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Unit Sub-Page and return via bottom Done button
        composeTestRule.onNodeWithTag("settings_row_unit").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_unit_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unit_chip_m").performClick()
        composeTestRule.waitForIdle()
        assertEquals("m", vm.selectedUnit.value)
        composeTestRule.onNodeWithTag("settings_subpage_done_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Language Sub-Page cleanly
        composeTestRule.onNodeWithTag("settings_language_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_language_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_subpage_back_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun `WelcomeScreen renders redesigned 3-step onboarding without jargon and launches ruler or AR`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        var launchedMode = -1

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                WelcomeScreen(
                    viewModel = vm,
                    onStartMeasuring = { launchedMode = it }
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_pager").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Vulkan", substring = true).assertCountEquals(0)

        // Advance Step 1 -> Step 2
        composeTestRule.onNodeWithTag("welcome_next_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("拍下測量畫面，影像數據帶著走").assertIsDisplayed()

        // Advance Step 2 -> Step 3 (final launch options)
        composeTestRule.onNodeWithTag("welcome_next_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("高精度螢幕直尺與雙線卡尺").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_btn_ruler").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_btn_start_ar").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, launchedMode)
    }

    @Test
    fun `RecordDetailDialog renders safely and exposes video preview player`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        val tempVideo = File(app.cacheDir, "detail_test.mp4").apply { writeText("mp4") }
        val record = MeasureRecord(
            id = 42,
            title = "陽台寬度",
            value = 2.5,
            unit = "m",
            type = "DISTANCE",
            videoPath = tempVideo.absolutePath,
            notes = "初測數據"
        )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                RecordDetailDialog(
                    record = record,
                    viewModel = vm,
                    onDismiss = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_video_player").assertIsDisplayed()
        composeTestRule.onNodeWithText("陽台寬度").assertIsDisplayed()
        composeTestRule.onNodeWithText("初測數據").assertIsDisplayed()
        tempVideo.delete()
    }

    @Test
    fun `SettingsScreen supports custom reticle, AR palette, grid, interface, and tool customization`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        vm.resetAllSettingsToDefault()

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Visual Style Page: Reticle, Accent Palette, and 3-mode Spatial Grid
        composeTestRule.onNodeWithTag("settings_row_visual_style").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_visual_style_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reticle_option_crosshair").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("palette_option_NEON_LIME").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("grid_option_dot_matrix").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("PRECISION_CROSSHAIR", vm.reticleStyle.value)
        assertEquals("NEON_LIME", vm.arAccentPalette.value)
        assertEquals("DOT_MATRIX", vm.gridOverlayStyle.value)
        composeTestRule.onNodeWithTag("settings_subpage_done_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 2. Interface Customization Page: Button Scale, Keep Screen On, Auto Copy
        composeTestRule.onNodeWithTag("settings_row_interface_custom").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_interface_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_scale_115").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("switch_keep_screen_on").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("switch_auto_copy").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1.15f, vm.uiButtonScale.value, 0.01f)
        assertFalse(vm.keepScreenOn.value)
        assertTrue(vm.autoCopyOnComplete.value)
        composeTestRule.onNodeWithTag("settings_subpage_done_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 3. Tools Customization Page: Ruler Theme & Dual Unit (Spirit Level options omitted)
        composeTestRule.onNodeWithTag("settings_row_tools_custom").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_tools_custom_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ruler_theme_cyan").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("switch_ruler_dual_unit").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("NEON_CYAN", vm.rulerTheme.value)
        assertTrue(vm.rulerDualUnit.value)
        composeTestRule.onAllNodesWithTag("level_tol_02").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("switch_level_audio").assertCountEquals(0)
        composeTestRule.onNodeWithTag("settings_subpage_back_btn").performClick()
        composeTestRule.waitForIdle()

        // 4. Ruler Edit Page inside Settings
        composeTestRule.onNodeWithTag("settings_row_ruler_edit").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_ruler_edit_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_subpage_back_btn").performClick()
        composeTestRule.waitForIdle()

        // 5. Default Unit Page includes millimeter (mm)
        composeTestRule.onNodeWithTag("settings_row_unit").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_unit_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("unit_chip_mm").performClick()
        composeTestRule.waitForIdle()
        assertEquals("mm", vm.selectedUnit.value)
        composeTestRule.onNodeWithTag("settings_subpage_done_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // 6. Reset restores defaults cleanly
        vm.resetAllSettingsToDefault()
        assertEquals("DOUBLE_RING", vm.reticleStyle.value)
        assertEquals("DYNAMIC", vm.arAccentPalette.value)
        assertEquals("MATERIAL_YOU", vm.rulerTheme.value)
        assertEquals(1.0f, vm.uiButtonScale.value, 0.01f)
        assertTrue(vm.keepScreenOn.value)
        assertFalse(vm.autoCopyOnComplete.value)
    }

    @Test
    fun `SettingsScreen opens Camera Quality page and enables 120Hz Smooth Display`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                SettingsScreen(
                    viewModel = vm,
                    onNavigateBack = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_row_camera_quality").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_camera_quality_page").assertIsDisplayed()
        composeTestRule.onNodeWithTag("refresh_rate_120hz").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("120Hz", vm.refreshRateMode.value)
        assertEquals(120, vm.effectiveTargetFps.value)
        composeTestRule.onNodeWithTag("settings_subpage_done_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }

    @Test
    fun `ModernArTopBar exposes 3D Volume mode and millimeter quick unit selection`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                com.example.ui.components.ModernArTopBar(
                    viewModel = vm,
                    context = app,
                    subMode = vm.cameraSubMode.value,
                    autoDetectedType = "DISTANCE",
                    selectedUnit = vm.selectedUnit.value,
                    isTorchOn = false,
                    torchBrightness = 1f,
                    cameraAspectRatio = "FULL",
                    planesCount = 2,
                    isRecordingVideo = false,
                    recordingSeconds = 0,
                    onShowHistoryClick = {},
                    onShowSettingsClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Open submode dropdown and select 3D Volume (mode 3)
        composeTestRule.onNodeWithTag("submode_dropdown_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submode_menu_item_3").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(3, vm.cameraSubMode.value)

        // Open unit dropdown and select millimeter (mm)
        composeTestRule.onNodeWithTag("unit_selector_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unit_menu_item_mm").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals("mm", vm.selectedUnit.value)
    }

    @Test
    fun `RulerComponent toggles interactive dual-line caliper card and closes cleanly`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)

        assertEquals(40.0, com.example.ui.components.computeCaliperDeltaMm(10.0, 50.0), 1e-6)
        assertEquals("40.0 mm · 4.00 cm · 1.57 in", com.example.ui.components.formatCaliperDeltaSummary(40.0))

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                RulerComponent(viewModel = vm)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ruler_caliper_toggle_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ruler_caliper_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("4.00 cm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ruler_caliper_zero_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ruler_caliper_copy_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ruler_caliper_close_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ruler_caliper_toggle_button").assertIsDisplayed()
    }

    @Test
    fun `HistorySheetContent shows filter reset button when filtered empty and restores list`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        val records = listOf(
            MeasureRecord(id = 10, title = "書桌寬度", value = 1.4, unit = "m", type = "DISTANCE")
        )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                HistorySheetContent(
                    records = records,
                    viewModel = vm,
                    onSelectRecord = {},
                    onClose = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("書桌寬度").assertIsDisplayed()
        // Switch to Volume filter -> empty results -> Reset button appears
        composeTestRule.onNodeWithTag("history_filter_volume").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithText("書桌寬度").assertCountEquals(0)
        composeTestRule.onNodeWithTag("history_reset_filters_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("書桌寬度").assertIsDisplayed()
    }

    @Test
    fun `ModernArBottomDeck shows clear-all button with 2+ points and gates Angle summary to 3 points`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MeasureViewModel(app)
        vm.setCameraSubMode(5) // Angle mode
        vm.capturedPoints.add(com.example.ui.viewmodel.Point3D(0.0, 0.0, -1.0))
        vm.capturedPoints.add(com.example.ui.viewmodel.Point3D(1.0, 0.0, -1.0))
        vm.updatePointLabel(0, "P1")

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                com.example.ui.components.ModernArBottomDeck(
                    viewModel = vm,
                    subMode = 5,
                    selectedUnit = "cm",
                    hasCapturedPoints = vm.capturedPoints.isNotEmpty(),
                    isWaitingForSecondPoint = true,
                    capturedPointsCount = vm.capturedPoints.size,
                    isMeasurementAvailable = true,
                    uiButtonScale = 1f,
                    isRecordingVideo = false,
                    bottomPadding = androidx.compose.ui.unit.Dp(0f),
                    textureViewRef = { null },
                    cameraAspectRatio = "FULL",
                    useDisplayP3ColorSpace = true,
                    videoRecorder = com.example.logic.camera.ArVideoRecorder(app),
                    onShowHistoryClick = {},
                    onTriggerShutterFlash = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        // Angle mode with only 2 points should NOT show premature 0° summary card
        composeTestRule.onAllNodesWithTag("measurement_live_summary_card").assertCountEquals(0)
        // Quick Clear-All button IS available and clears points cleanly
        composeTestRule.onNodeWithTag("clear_all_points_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(vm.capturedPoints.isEmpty())
    }
}

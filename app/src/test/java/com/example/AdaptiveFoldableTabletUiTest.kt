package com.example

import android.Manifest
import android.app.Application
import android.graphics.Rect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.window.layout.FoldingFeature
import com.example.data.model.MeasureRecord
import com.example.ui.adaptive.AdaptiveHeightClass
import com.example.ui.adaptive.AdaptiveWidthClass
import com.example.ui.adaptive.AdaptiveWindowState
import com.example.ui.adaptive.FoldPosture
import com.example.ui.adaptive.HingeOcclusion
import com.example.ui.adaptive.LocalAdaptiveWindowState
import com.example.ui.adaptive.resolveAdaptiveWindowState
import com.example.ui.components.AdaptiveNavigationRail
import com.example.ui.components.ModernArCameraView
import com.example.ui.components.RulerComponent
import com.example.ui.screens.HistoryTwoPaneWorkspace
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MeasureViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h720dp")
class AdaptiveFoldableTabletUiTest {

    private val vmRule = ManagedViewModelRule()
    val composeTestRule = createComposeRule()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(vmRule).around(composeTestRule)

    private fun fakeFoldingFeature(
        bounds: Rect,
        state: FoldingFeature.State,
        orientation: FoldingFeature.Orientation,
        isSeparating: Boolean = state == FoldingFeature.State.HALF_OPENED,
        occlusion: FoldingFeature.OcclusionType = FoldingFeature.OcclusionType.NONE
    ): FoldingFeature = object : FoldingFeature {
        override val bounds: Rect = bounds
        override val isSeparating: Boolean = isSeparating
        override val occlusionType: FoldingFeature.OcclusionType = occlusion
        override val state: FoldingFeature.State = state
        override val orientation: FoldingFeature.Orientation = orientation
    }

    @Test
    fun `resolveAdaptiveWindowState maps FoldingFeature postures accurately`() {
        val flatState = resolveAdaptiveWindowState(
            widthClass = AdaptiveWidthClass.COMPACT,
            heightClass = AdaptiveHeightClass.MEDIUM,
            foldingFeature = null
        )
        assertEquals(FoldPosture.FLAT, flatState.posture)
        assertFalse(flatState.hasActiveFoldSplit)
        assertFalse(flatState.useNavigationRail)

        val tabletopFeature = fakeFoldingFeature(
            bounds = Rect(0, 900, 2208, 920),
            state = FoldingFeature.State.HALF_OPENED,
            orientation = FoldingFeature.Orientation.HORIZONTAL
        )
        val tabletopState = resolveAdaptiveWindowState(
            widthClass = AdaptiveWidthClass.MEDIUM,
            heightClass = AdaptiveHeightClass.MEDIUM,
            foldingFeature = tabletopFeature
        )
        assertEquals(FoldPosture.TABLETOP, tabletopState.posture)
        assertTrue(tabletopState.isTabletop)
        assertTrue(tabletopState.useTwoPaneLayout)

        val bookFeature = fakeFoldingFeature(
            bounds = Rect(1090, 0, 1118, 1840),
            state = FoldingFeature.State.HALF_OPENED,
            orientation = FoldingFeature.Orientation.VERTICAL,
            occlusion = FoldingFeature.OcclusionType.FULL
        )
        val bookState = resolveAdaptiveWindowState(
            widthClass = AdaptiveWidthClass.EXPANDED,
            heightClass = AdaptiveHeightClass.MEDIUM,
            foldingFeature = bookFeature
        )
        assertEquals(FoldPosture.BOOK, bookState.posture)
        assertTrue(bookState.isBook)
        assertEquals(HingeOcclusion.FULL, bookState.occlusion)
        assertTrue(bookState.useNavigationRail)
    }

    @Test
    fun `AdaptiveNavigationRail displays AR measurement label and handles mode switching`() {
        var selectedMode = 0
        var historyClicks = 0
        var settingsClicks = 0

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                var mode by remember { mutableIntStateOf(0) }
                AdaptiveNavigationRail(
                    currentMode = mode,
                    onModeSelected = {
                        mode = it
                        selectedMode = it
                    },
                    cameraLabel = "AR 測量",
                    rulerLabel = "螢幕尺",
                    selectedUnit = "cm",
                    onSelectUnit = {},
                    isTorchOn = false,
                    onToggleTorch = {},
                    recordCount = 3,
                    isHistoryOpen = false,
                    onToggleHistory = { historyClicks++ },
                    onOpenSettings = { settingsClicks++ }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("adaptive_navigation_rail").assertIsDisplayed()
        composeTestRule.onNodeWithText("AR 測量").assertIsDisplayed()
        composeTestRule.onNodeWithText("螢幕尺").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("rail_item_level").assertCountEquals(0)
        // History button is visible while AR measurement mode (0) is active
        composeTestRule.onNodeWithTag("rail_history_button").performClick()
        composeTestRule.onNodeWithTag("rail_item_ruler").performClick()
        composeTestRule.onNodeWithTag("rail_settings_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, selectedMode)
        assertEquals(1, historyClicks)
        assertEquals(1, settingsClicks)
    }

    @Test
    fun `HistoryTwoPaneWorkspace renders list and detail side by side in Book posture`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vmRule.create(app)
        val sampleRecords = listOf(
            MeasureRecord(
                id = 101,
                title = "客廳茶几寬度",
                value = 1.25,
                unit = "m",
                type = "CAM",
                notes = "實木桌面"
            ),
            MeasureRecord(
                id = 102,
                title = "玄關門框高度",
                value = 2.10,
                unit = "m",
                type = "HEIGHT",
                notes = "標準高度"
            )
        )
        val bookTabletState = AdaptiveWindowState(
            widthClass = AdaptiveWidthClass.EXPANDED,
            heightClass = AdaptiveHeightClass.EXPANDED,
            posture = FoldPosture.BOOK,
            hingeBoundsInWindow = Rect(1200, 0, 1240, 1440),
            isSeparating = true
        )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                var selectedId by remember { mutableStateOf<Int?>(101) }
                HistoryTwoPaneWorkspace(
                    records = sampleRecords,
                    selectedRecordId = selectedId,
                    onSelectRecord = { selectedId = it.id },
                    onClearSelection = { selectedId = null },
                    onClose = {},
                    viewModel = vm,
                    adaptiveState = bookTabletState
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_two_pane_workspace").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_list_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_detail_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_hinge_gap").assertIsDisplayed()

        // Clicking second record updates inline detail pane without opening modal dialog
        composeTestRule.onNodeWithTag("history_record_item_102").performClick()
        composeTestRule.waitForIdle()
        assertTrue(composeTestRule.onAllNodesWithText("玄關門框高度").fetchSemanticsNodes().size >= 2)
    }

    @Test
    fun `SettingsScreen renders side by side list and embedded subpage on unfolded tablet`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vmRule.create(app)
        val tabletBookState = AdaptiveWindowState(
            widthClass = AdaptiveWidthClass.EXPANDED,
            heightClass = AdaptiveHeightClass.EXPANDED,
            posture = FoldPosture.BOOK,
            hingeBoundsInWindow = Rect(1200, 0, 1240, 1440),
            isSeparating = true
        )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalAdaptiveWindowState provides tabletBookState) {
                    SettingsScreen(
                        viewModel = vm,
                        onNavigateBack = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_two_pane_workspace").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_list_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_detail_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_hinge_gap").assertIsDisplayed()
        // Embedded subpage should hide redundant back button in two-pane mode
        composeTestRule.onAllNodesWithTag("settings_subpage_back_btn").assertCountEquals(0)
    }

    @Test
    fun `ModernArCameraView splits viewport and controls cleanly in Tabletop posture without horizon tilt UI`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val vm = vmRule.create(app)
        vm.setCameraSimulationActive(true)
        val tabletopState = AdaptiveWindowState(
            widthClass = AdaptiveWidthClass.MEDIUM,
            heightClass = AdaptiveHeightClass.MEDIUM,
            posture = FoldPosture.TABLETOP,
            hingeBoundsInWindow = Rect(0, 700, 1920, 740),
            isSeparating = true
        )

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalAdaptiveWindowState provides tabletopState) {
                    ModernArCameraView(
                        viewModel = vm,
                        onShowHistoryClick = {},
                        onShowSettingsClick = {}
                    )
                }
            }
        }

        repeat(4) {
            composeTestRule.mainClock.advanceTimeByFrame()
        }
        composeTestRule.onNodeWithTag("ar_tabletop_layout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ar_tabletop_viewport_pane").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ar_tabletop_hinge_gap").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ar_tabletop_controls_pane").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("基準水平對齊 (0°)").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("水準對齊調整中").assertCountEquals(0)
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun `RulerComponent renders on Book posture and Expanded tablet with hinge-aware controls`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = vmRule.create(app)
        val bookTabletState = AdaptiveWindowState(
            widthClass = AdaptiveWidthClass.EXPANDED,
            heightClass = AdaptiveHeightClass.EXPANDED,
            posture = FoldPosture.BOOK,
            hingeBoundsInWindow = Rect(1200, 0, 1240, 1440),
            isSeparating = true
        )

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalAdaptiveWindowState provides bookTabletState) {
                    RulerComponent(viewModel = vm)
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ruler_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ruler_edit_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ruler_caliper_toggle_button").assertIsDisplayed()
    }
}

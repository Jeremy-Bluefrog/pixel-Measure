package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.ExpressiveButtonOption
import com.example.ui.components.ExpressiveConnectedButtonGroup
import com.example.ui.components.ExpressiveContainedLoadingIndicator
import com.example.ui.components.ExpressiveFloatingToolbar
import com.example.ui.components.ExpressiveSplitButton
import com.example.ui.components.FloatingPillNavItem
import com.example.ui.components.FloatingPillNavigationBar
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Material3ExpressiveUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `FloatingPillNavigationBar switches active mode on click`() {
        var selectedMode = 0
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                var current by remember { mutableIntStateOf(0) }
                FloatingPillNavigationBar(
                    selectedIndex = current,
                    items = listOf(
                        FloatingPillNavItem(0, "AR 測距", Icons.Rounded.CameraAlt, "segmented_button_camera"),
                        FloatingPillNavItem(1, "尺規", Icons.Rounded.Straighten, "segmented_button_ruler"),
                        FloatingPillNavItem(2, "水平儀", Icons.Rounded.Explore, "segmented_button_level")
                    ),
                    onItemSelected = {
                        current = it
                        selectedMode = it
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("segmented_button_camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("segmented_button_ruler").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, selectedMode)
        composeTestRule.onNodeWithText("尺規").assertIsDisplayed()
    }

    @Test
    fun `ExpressiveSplitButton triggers primary action and trailing menu action separately`() {
        var primaryClicks = 0
        var trailingClicks = 0

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                var expanded by remember { mutableStateOf(false) }
                ExpressiveSplitButton(
                    label = "距離",
                    leadingIcon = Icons.Rounded.Straighten,
                    onPrimaryClick = { primaryClicks++ },
                    onTrailingClick = {
                        expanded = !expanded
                        trailingClicks++
                    },
                    expanded = expanded,
                    leadingTestTag = "submode_selector_button",
                    trailingTestTag = "submode_menu_button"
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submode_selector_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("submode_menu_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, primaryClicks)
        assertEquals(1, trailingClicks)
    }

    @Test
    fun `ExpressiveConnectedButtonGroup updates selection on option click`() {
        var selectedUnit = "cm"

        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                var unit by remember { mutableStateOf("cm") }
                ExpressiveConnectedButtonGroup(
                    options = listOf(
                        ExpressiveButtonOption("cm", "cm", testTag = "unit_chip_cm"),
                        ExpressiveButtonOption("m", "m", testTag = "unit_chip_m"),
                        ExpressiveButtonOption("in", "in", testTag = "unit_chip_in")
                    ),
                    selectedValue = unit,
                    onValueSelected = {
                        unit = it
                        selectedUnit = it
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("unit_chip_m").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals("m", selectedUnit)
    }

    @Test
    fun `ExpressiveFloatingToolbar and ExpressiveContainedLoadingIndicator compose cleanly`() {
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                ExpressiveFloatingToolbar(vibrant = true) {
                    ExpressiveContainedLoadingIndicator()
                    Text(
                        text = "12.4 cm",
                        style = ExpressiveTypography.displayNumeralMedium
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("12.4 cm").assertIsDisplayed()
        assertTrue(ExpressiveTypography.displayNumeralHero.fontFeatureSettings?.contains("tnum") == true)
    }
}

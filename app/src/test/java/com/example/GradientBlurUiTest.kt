package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.ui.components.BlurDirection
import com.example.ui.components.GradientBlurBottomBar
import com.example.ui.components.GradientBlurScrim
import com.example.ui.components.GradientBlurTopBar
import com.example.ui.components.gradientBlurSource
import com.example.ui.components.hardwareBackdropBlur
import com.example.ui.components.progressiveBlur
import com.example.ui.components.progressiveGlass
import com.example.ui.components.rememberGradientBlurBackdropState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GradientBlurUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `BlurDirection exposes all optical progression axes`() {
        assertEquals(5, BlurDirection.entries.size)
    }

    @Test
    fun `progressiveBlur and hardwareBackdropBlur compose cleanly under API 35`() {
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .hardwareBackdropBlur(enabled = true, blurRadius = 24f)
                        .progressiveBlur(
                            direction = BlurDirection.TOP_TO_BOTTOM,
                            maxBlurRadius = 20.dp,
                            startFraction = 0.2f,
                            endFraction = 1.0f
                        )
                        .testTag("progressive_blur_target")
                ) {
                    Text("Optical Blur Surface")
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("progressive_blur_target").assertIsDisplayed()
        composeTestRule.onNodeWithText("Optical Blur Surface").assertIsDisplayed()
    }

    @Test
    fun `true backdrop gradient blur source and scrims compose without recursion`() {
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                val backdropState = rememberGradientBlurBackdropState()
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .gradientBlurSource(backdropState)
                            .background(Color(0xFF0F172A))
                            .testTag("blur_source_box")
                    )

                    GradientBlurTopBar(
                        backdropState = backdropState,
                        blurRadius = 26.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .testTag("blur_top_bar")
                    ) {
                        Text("Top Frosted HUD")
                    }

                    GradientBlurScrim(
                        isTop = false,
                        backdropState = backdropState,
                        blurRadius = 28.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("blur_bottom_scrim")
                    )

                    GradientBlurBottomBar(
                        backdropState = backdropState,
                        blurRadius = 24.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .testTag("blur_bottom_bar")
                    ) {
                        Text("Bottom Frosted Controls")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("blur_source_box").assertIsDisplayed()
        composeTestRule.onNodeWithTag("blur_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("blur_bottom_scrim").assertIsDisplayed()
        composeTestRule.onNodeWithTag("blur_bottom_bar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Top Frosted HUD").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bottom Frosted Controls").assertIsDisplayed()
    }

    @Test
    fun `progressiveGlass surface renders crisp child content`() {
        composeTestRule.setContent {
            MyApplicationTheme(dynamicColor = false) {
                val backdropState = rememberGradientBlurBackdropState()
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .gradientBlurSource(backdropState)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 240.dp, height = 80.dp)
                            .progressiveGlass(
                                backdropState = backdropState,
                                direction = BlurDirection.RADIAL_EDGE,
                                maxBlurRadius = 18.dp
                            )
                            .testTag("progressive_glass_card"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Glass Card")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("progressive_glass_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Glass Card").assertIsDisplayed()
    }
}

package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive Grouped List Item Positions
 * Used for connected card/list item shapes in M3 settings, dialogs, and records.
 */
enum class GroupedPosition {
    TOP,
    MIDDLE,
    BOTTOM,
    SINGLE
}

/**
 * Computes the item's grouped position based on its index and total count in the group.
 */
fun getGroupedPosition(index: Int, totalCount: Int): GroupedPosition {
    return when {
        totalCount <= 1 -> GroupedPosition.SINGLE
        index == 0 -> GroupedPosition.TOP
        index == totalCount - 1 -> GroupedPosition.BOTTOM
        else -> GroupedPosition.MIDDLE
    }
}

/**
 * Generates Material 3 Expressive Grouped List Shapes:
 * - TOP: Large outer curvature at top start/end, subtle inner curvature at bottom.
 * - MIDDLE: Subtle inner curvature on all corners for cohesive connectivity.
 * - BOTTOM: Subtle inner curvature at top, large outer curvature at bottom start/end.
 * - SINGLE: Large outer curvature on all four corners.
 */
fun groupedItemShape(
    position: GroupedPosition,
    outerCorner: Dp = 24.dp,
    innerCorner: Dp = 4.dp
): RoundedCornerShape {
    return when (position) {
        GroupedPosition.TOP -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner
        )
        GroupedPosition.MIDDLE -> RoundedCornerShape(innerCorner)
        GroupedPosition.BOTTOM -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
        GroupedPosition.SINGLE -> RoundedCornerShape(outerCorner)
    }
}

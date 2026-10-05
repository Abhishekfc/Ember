package com.emigo.app.ui.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFoldMetricsTest {

    private val phoneWidth = 411.dp

    @Test
    fun aTallScreenGetsTheRoomyScale() {
        assertEquals(HomeFoldRoomy, homeFoldMetricsFor(availableHeight = 2000.dp, screenWidth = phoneWidth))
    }

    @Test
    fun aVeryShortScreenGetsTheCompactScale() {
        assertEquals(HomeFoldCompact, homeFoldMetricsFor(availableHeight = 100.dp, screenWidth = phoneWidth))
    }

    @Test
    fun theChoiceSwitchesOnceAndNeverFlipsBack() {
        // As the available height grows, the scale must go compact -> roomy exactly once. A layout
        // that flips back and forth never settles.
        var sawRoomy = false
        for (height in 0..2000) {
            val isRoomy = homeFoldMetricsFor(height.dp, phoneWidth) == HomeFoldRoomy
            if (sawRoomy) assertTrue("flipped back to compact at ${height}dp", isRoomy)
            if (isRoomy) sawRoomy = true
        }
        assertTrue(sawRoomy)
    }

    @Test
    fun aWiderScreenNeedsMoreHeightForTheRoomyScale() {
        fun firstRoomyHeight(width: Int): Int =
            (0..4000).first { homeFoldMetricsFor(it.dp, width.dp) == HomeFoldRoomy }
        assertTrue(firstRoomyHeight(480) > firstRoomyHeight(360))
    }

    @Test
    fun theCompactScaleIsNeverLargerThanTheRoomyOne() {
        // Compact must be the same design at a smaller scale, not a different one.
        assertTrue(HomeFoldCompact.toggleTopGap <= HomeFoldRoomy.toggleTopGap)
        assertTrue(HomeFoldCompact.pillWidth <= HomeFoldRoomy.pillWidth)
        assertTrue(HomeFoldCompact.pillVerticalPadding <= HomeFoldRoomy.pillVerticalPadding)
        assertTrue(HomeFoldCompact.cardTopGap <= HomeFoldRoomy.cardTopGap)
        assertTrue(HomeFoldCompact.avatarRowTopGap <= HomeFoldRoomy.avatarRowTopGap)
        assertTrue(HomeFoldCompact.avatarRowBottomGap <= HomeFoldRoomy.avatarRowBottomGap)
        assertTrue(HomeFoldCompact.avatarDiameter <= HomeFoldRoomy.avatarDiameter)
        assertTrue(HomeFoldCompact.avatarInactiveDiameter <= HomeFoldRoomy.avatarInactiveDiameter)
        assertTrue(HomeFoldCompact.avatarItemWidth <= HomeFoldRoomy.avatarItemWidth)
    }

    @Test
    fun anInactiveAvatarIsSmallerThanTheActiveOneAndFitsItsColumn() {
        for (m in listOf(HomeFoldRoomy, HomeFoldCompact)) {
            assertTrue(m.avatarInactiveDiameter < m.avatarDiameter)
            assertTrue(m.avatarDiameter <= m.avatarItemWidth)
        }
    }

    @Test
    fun theCardSidePaddingIsFivePercentOfTheScreenWidth() {
        assertEquals(20.dp, featuredCardSidePaddingFor(400.dp))
    }
}

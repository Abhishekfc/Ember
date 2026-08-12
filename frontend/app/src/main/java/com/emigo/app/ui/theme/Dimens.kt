package com.emigo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** The corner-radius scale every surface in the app should read from, rather than each screen
 * picking its own number — the difference between a card that reads as "considered" and one that
 * just happens to have rounded corners is usually whether that radius is shared with everything
 * else at its same tier. */
object EmberRadii {
    val card = 22.dp
    val button = 20.dp
    val dialog = 24.dp
    val bottomSheet = 30.dp
    val image = 20.dp

    val cardShape = RoundedCornerShape(card)
    val buttonShape = RoundedCornerShape(button)
    val dialogShape = RoundedCornerShape(dialog)
    val bottomSheetShape = RoundedCornerShape(topStart = bottomSheet, topEnd = bottomSheet)
}

/** The spacing scale every screen should read from — generous enough that nothing reads as
 * cramped, consistent enough that rhythm carries across screens instead of each one inventing
 * its own gutter. */
object EmberSpacing {
    val horizontal = 24.dp
    val vertical = 28.dp
    val betweenSections = 32.dp
}

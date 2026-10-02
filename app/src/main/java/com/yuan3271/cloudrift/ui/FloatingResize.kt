package com.yuan3271.cloudrift.ui

/**
 * The arithmetic behind resizing the floating keyboard, kept out of the composable so it can be
 * tested as a *meaning* rather than as pixels: the edge the finger is holding has to move with the
 * finger, the same way a window edge does under a window manager.
 *
 * Why it is not just "one dp of drag is one dp of key height": the card is not all keys. Above and
 * below the keys sit the toolbar, the candidate strip and the grip, and none of them change with the
 * key height, so a key-height change of `dk` moves the card edge by `rows * dk` - not by `dk`. The
 * old code fed the finger's own dy in as `dk`, which made the card grow more than four times faster
 * than the finger (four letter rows) and is exactly what "不跟手" looked like.
 */

/**
 * How many key rows the measured key area holds.
 *
 * A key area is `rows * keyHeight + (rows - 1) * gap` tall (the symbol page's function row and the
 * nine key pad follow the same shape), so the row count can be recovered from the measured height
 * without knowing which page is on screen.
 */
internal fun floatingResizeRows(keyAreaHeightPx: Float, keyHeightPx: Float, gapPx: Float): Float {
    if (keyAreaHeightPx <= 0f || keyHeightPx <= 0f) return 1f
    val rows = (keyAreaHeightPx + gapPx) / (keyHeightPx + gapPx)
    // A measurement from a panel that is not made of keys (the voice panel, a settings sheet) can
    // land anywhere; keeping it near "a few rows" stops one bad frame from turning a drag into a
    // jump.
    return rows.coerceIn(1f, 12f)
}

/**
 * One step of a corner drag, as the two settings the card is built from.
 *
 * @param dragXPx horizontal drag in pixels, screen-right positive.
 * @param dragYPx vertical drag in pixels, screen-down positive.
 * @param density pixels per dp ([androidx.compose.ui.unit.Density.density]).
 * @param screenWidthDp screen width in dp - the width setting is a percentage of it.
 * @param resizeRows rows reported by [floatingResizeRows].
 * @return width delta in percent of the screen, and key height delta in dp.
 */
internal fun floatingResizeDeltas(
    dragXPx: Float,
    dragYPx: Float,
    density: Float,
    screenWidthDp: Float,
    resizeRows: Float,
): Pair<Float, Float> {
    val dp = density.coerceAtLeast(0.1f)
    val widthPercent = (dragXPx / dp) / screenWidthDp.coerceAtLeast(1f) * 100f
    // Up (negative y) grows the card; the card edge moves a row's worth per dp of key height.
    val keyHeightDp = (-dragYPx / dp) / resizeRows.coerceAtLeast(1f)
    return widthPercent to keyHeightDp
}

package com.yuan3271.cloudrift.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * GENERATED FILE - edit `tools/icons/build_icons.py` instead.
 *
 * 云隙 icon set, drawn to the HyperOS / HarmonyOS grid rules: a 24 unit board with a
 * 2 unit optical margin, 2 unit round capped strokes, and solid shapes for the glyphs
 * that need to read at a glance (mic state, moon, cloud).
 *
 * Nothing here is derived from a third party icon library: Xiaomi publishes no open
 * UI icon set and HarmonyOS Symbol is proprietary, so the geometry is authored in the
 * generator and reviewed through `tools/design/icons-preview.svg`.
 */
object CloudriftIcons {

    /** Mic */
    val Mic: ImageVector = build(
        name = "Mic",
        shapes = listOf(
            stroked("M12,2.6 H12 A3,3 0 0,1 15,5.6 V10.8 A3,3 0 0,1 12,13.8 H12 A3,3 0 0,1 9,10.8 V5.6 A3,3 0 0,1 12,2.6 Z", 2.0f),
            stroked("M18.01,13.59 A6.4,6.4 0 0,1 5.99,13.59", 2.0f),
            stroked("M12,17.8 L12,21.2", 2.0f),
            stroked("M8.6,21.2 L15.4,21.2", 2.0f),
        ),
    )

    /** Stop */
    val Stop: ImageVector = build(
        name = "Stop",
        shapes = listOf(
            filled("M10.2,7 H13.8 A3.2,3.2 0 0,1 17,10.2 V13.8 A3.2,3.2 0 0,1 13.8,17 H10.2 A3.2,3.2 0 0,1 7,13.8 V10.2 A3.2,3.2 0 0,1 10.2,7 Z"),
        ),
    )

    /** Waveform */
    val Waveform: ImageVector = build(
        name = "Waveform",
        shapes = listOf(
            stroked("M4,10 L4,14", 2.0f),
            stroked("M8,6.5 L8,17.5", 2.0f),
            stroked("M12,8.8 L12,15.2", 2.0f),
            stroked("M16,5 L16,19", 2.0f),
            stroked("M20,10 L20,14", 2.0f),
        ),
    )

    /** Backspace */
    val Backspace: ImageVector = build(
        name = "Backspace",
        shapes = listOf(
            stroked("M9.13,5.07 A1.6,1.6 0 0,1 10.26,4.6 L18.74,4.6 A1.6,1.6 0 0,1 19.87,5.07 L21.13,6.33 A1.6,1.6 0 0,1 21.6,7.46 L21.6,16.54 A1.6,1.6 0 0,1 21.13,17.67 L19.87,18.93 A1.6,1.6 0 0,1 18.74,19.4 L10.26,19.4 A1.6,1.6 0 0,1 9.13,18.93 L7.4,17.2 L3.6,13.09 A1.6,1.6 0 0,1 3.6,10.91 L7.4,6.8 Z", 2.0f),
            stroked("M11.2,9.6 L16.8,14.4", 2.0f),
            stroked("M16.8,9.6 L11.2,14.4", 2.0f),
        ),
    )

    /** Shift */
    val Shift: ImageVector = build(
        name = "Shift",
        shapes = listOf(
            stroked("M10.73,4.87 A1.8,1.8 0 0,1 13.27,4.87 L18.37,9.97 A1.8,1.8 0 0,1 17.7,11.6 L17.2,11.6 A1.8,1.8 0 0,0 15.4,13.4 L15.4,18.6 A1.8,1.8 0 0,1 13.6,20.4 L10.4,20.4 A1.8,1.8 0 0,1 8.6,18.6 L8.6,13.4 A1.8,1.8 0 0,0 6.8,11.6 L6.3,11.6 A1.8,1.8 0 0,1 5.63,9.97 Z", 2.0f),
        ),
    )

    /** Return */
    val Return: ImageVector = build(
        name = "Return",
        shapes = listOf(
            stroked("M20,5.6 V13 A1.6,1.6 0 0 1 18.4,14.6 H4.6", 2.0f),
            stroked("M9,10 L4.4,14.6", 2.0f),
            stroked("M9,19.2 L4.4,14.6", 2.0f),
        ),
    )

    /** Close */
    val Close: ImageVector = build(
        name = "Close",
        shapes = listOf(
            stroked("M6.8,6.8 L17.2,17.2", 2.0f),
            stroked("M17.2,6.8 L6.8,17.2", 2.0f),
        ),
    )

    /** Check */
    val Check: ImageVector = build(
        name = "Check",
        shapes = listOf(
            stroked("M5.6,12.6 L10.2,17.2 L18.4,6.8", 2.0f),
        ),
    )

    /** Refresh */
    val Refresh: ImageVector = build(
        name = "Refresh",
        shapes = listOf(
            stroked("M9.47,5.05 A7.4,7.4 0 1,1 5.05,9.47", 2.0f),
            stroked("M5.05,9.47 L2.3,11.47 M5.05,9.47 L5.87,12.77", 2.0f),
        ),
    )

    /** Send */
    val Send: ImageVector = build(
        name = "Send",
        shapes = listOf(
            filled("M20.6,3.4 L3.6,10.6 L10.8,13.2 L13.4,20.4 Z"),
        ),
    )

    /** ArrowBack */
    val ArrowBack: ImageVector = build(
        name = "ArrowBack",
        shapes = listOf(
            stroked("M20,12 L4.4,12", 2.0f),
            stroked("M10.4,5.6 L4,12", 2.0f),
            stroked("M10.4,18.4 L4,12", 2.0f),
        ),
    )

    /** ExpandMore */
    val ExpandMore: ImageVector = build(
        name = "ExpandMore",
        shapes = listOf(
            stroked("M5.6,9.2 L12,15.6", 2.0f),
            stroked("M18.4,9.2 L12,15.6", 2.0f),
        ),
    )

    /** More */
    val More: ImageVector = build(
        name = "More",
        shapes = listOf(
            filled("M3.7,12 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 Z"),
            filled("M10.1,12 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 Z"),
            filled("M16.5,12 a1.9,1.9 0 1,0 3.8,0 a1.9,1.9 0 1,0 -3.8,0 Z"),
        ),
    )

    /** KeyboardHide */
    val KeyboardHide: ImageVector = build(
        name = "KeyboardHide",
        shapes = listOf(
            stroked("M5.6,7.6 L12,14", 2.0f),
            stroked("M18.4,7.6 L12,14", 2.0f),
            stroked("M4.8,19 L19.2,19", 2.0f),
        ),
    )

    /** Keyboard */
    val Keyboard: ImageVector = build(
        name = "Keyboard",
        shapes = listOf(
            stroked("M5.7,6 H18.3 A3,3 0 0,1 21.3,9 V15 A3,3 0 0,1 18.3,18 H5.7 A3,3 0 0,1 2.7,15 V9 A3,3 0 0,1 5.7,6 Z", 1.9f),
            filled("M5.85,10 a1.15,1.15 0 1,0 2.3,0 a1.15,1.15 0 1,0 -2.3,0 Z"),
            filled("M10.85,10 a1.15,1.15 0 1,0 2.3,0 a1.15,1.15 0 1,0 -2.3,0 Z"),
            filled("M15.85,10 a1.15,1.15 0 1,0 2.3,0 a1.15,1.15 0 1,0 -2.3,0 Z"),
            stroked("M9,14.6 L15,14.6", 1.9f),
        ),
    )

    /** Globe */
    val Globe: ImageVector = build(
        name = "Globe",
        shapes = listOf(
            stroked("M3.4,12 a8.6,8.6 0 1,0 17.2,0 a8.6,8.6 0 1,0 -17.2,0", 2.0f),
            stroked("M12,3.4 C8.6,6.6 8.6,17.4 12,20.6 C15.4,17.4 15.4,6.6 12,3.4 Z", 2.0f),
            stroked("M3.6,12 L20.4,12", 2.0f),
        ),
    )

    /** Settings */
    val Settings: ImageVector = build(
        name = "Settings",
        shapes = listOf(
            stroked("M3.6,7.4 L20.4,7.4", 2.0f),
            stroked("M3.6,12 L20.4,12", 2.0f),
            stroked("M3.6,16.6 L20.4,16.6", 2.0f),
            filled("M6.7,7.4 a2.5,2.5 0 1,0 5,0 a2.5,2.5 0 1,0 -5,0 Z"),
            filled("M12.9,12 a2.5,2.5 0 1,0 5,0 a2.5,2.5 0 1,0 -5,0 Z"),
            filled("M6.7,16.6 a2.5,2.5 0 1,0 5,0 a2.5,2.5 0 1,0 -5,0 Z"),
        ),
    )

    /** Sun */
    val Sun: ImageVector = build(
        name = "Sun",
        shapes = listOf(
            stroked("M7.6,12 a4.4,4.4 0 1,0 8.8,0 a4.4,4.4 0 1,0 -8.8,0", 2.0f),
            stroked("M12,4.6 L12,2.2 M17.23,6.77 L18.93,5.07 M19.4,12 L21.8,12 M17.23,17.23 L18.93,18.93 M12,19.4 L12,21.8 M6.77,17.23 L5.07,18.93 M4.6,12 L2.2,12 M6.77,6.77 L5.07,5.07", 2.0f),
        ),
    )

    /** Moon */
    val Moon: ImageVector = build(
        name = "Moon",
        shapes = listOf(
            filled("M20.4,14.6 A8.6,8.6 0 1,1 9.4,3.6 A6.9,6.9 0 0,0 20.4,14.6 Z"),
        ),
    )

    /** AutoMode */
    val AutoMode: ImageVector = build(
        name = "AutoMode",
        shapes = listOf(
            stroked("M3.6,12 a8.4,8.4 0 1,0 16.8,0 a8.4,8.4 0 1,0 -16.8,0", 2.0f),
            filled("M12,3.6 A8.4,8.4 0 0,1 12,20.4 L12,12 Z"),
        ),
    )

    /** Cloud */
    val Cloud: ImageVector = build(
        name = "Cloud",
        shapes = listOf(
            filled("M7.4,19.4 A4.6,4.6 0 0 1 7.0,10.3 A5.4,5.4 0 0 1 17.2,9.4 A4.0,4.0 0 0 1 16.8,19.4 Z"),
        ),
    )

    /** Info */
    val Info: ImageVector = build(
        name = "Info",
        shapes = listOf(
            stroked("M3.4,12 a8.6,8.6 0 1,0 17.2,0 a8.6,8.6 0 1,0 -17.2,0", 2.0f),
            stroked("M12,10.8 L12,17", 2.0f),
            filled("M10.65,7.4 a1.35,1.35 0 1,0 2.7,0 a1.35,1.35 0 1,0 -2.7,0 Z"),
        ),
    )

    /** Key */
    val Key: ImageVector = build(
        name = "Key",
        shapes = listOf(
            stroked("M11,8.6 a4.4,4.4 0 1,0 8.8,0 a4.4,4.4 0 1,0 -8.8,0", 2.0f),
            stroked("M12.4,11.6 L4.4,19.6", 2.0f),
            stroked("M6.8,17.2 L9.6,20", 2.0f),
        ),
    )

    /** Palette */
    val Palette: ImageVector = build(
        name = "Palette",
        shapes = listOf(
            filled("M12,3.2 A8.8,8.8 0 0 0 3.2,12 A8.8,8.8 0 0 0 12,20.8 C14.0,20.8 14.8,19.4 14.0,18.2 C13.2,17.0 14.2,15.6 15.8,15.6 H18.0 A2.8,2.8 0 0 0 20.8,12.8 C20.8,7.4 16.9,3.2 12,3.2 Z"),
            stroked("M6.9,9.2 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 Z", 2.0f),
            stroked("M6.5,14.2 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 Z", 2.0f),
            stroked("M11.1,7 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 Z", 2.0f),
            stroked("M14.9,10.6 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0 Z", 2.0f),
        ),
    )

    /** Spellcheck */
    val Spellcheck: ImageVector = build(
        name = "Spellcheck",
        shapes = listOf(
            stroked("M3.2,14.8 L7.6,4.6 L12.0,14.8", 2.0f),
            stroked("M5,11.2 L10.2,11.2", 2.0f),
            stroked("M13.6,18.6 L16.4,21.4 L21.8,13.8", 2.0f),
        ),
    )

    /** Clipboard */
    val Clipboard: ImageVector = build(
        name = "Clipboard",
        shapes = listOf(
            stroked("M7.4,4.4 H16.6 A2.8,2.8 0 0,1 19.4,7.2 V18.2 A2.8,2.8 0 0,1 16.6,21 H7.4 A2.8,2.8 0 0,1 4.6,18.2 V7.2 A2.8,2.8 0 0,1 7.4,4.4 Z", 2.0f),
            filled("M10.5,2.6 H13.5 A1.5,1.5 0 0,1 15,4.1 V4.7 A1.5,1.5 0 0,1 13.5,6.2 H10.5 A1.5,1.5 0 0,1 9,4.7 V4.1 A1.5,1.5 0 0,1 10.5,2.6 Z"),
            stroked("M8.6,11 L15.4,11", 2.0f),
            stroked("M8.6,14.6 L15.4,14.6", 2.0f),
            stroked("M8.6,18.2 L12.6,18.2", 2.0f),
        ),
    )

    private fun build(name: String, shapes: List<Shape>): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = GRID,
            viewportHeight = GRID,
        )
        for (shape in shapes) {
            builder.addPath(
                pathData = addPathNodes(shape.data),
                fill = if (shape.stroke) null else SolidColor(Color.Black),
                stroke = if (shape.stroke) SolidColor(Color.Black) else null,
                strokeLineWidth = shape.width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }

    private data class Shape(val data: String, val stroke: Boolean, val width: Float)

    private fun stroked(data: String, width: Float) = Shape(data, stroke = true, width = width)

    private fun filled(data: String) = Shape(data, stroke = false, width = 0f)

    private const val GRID = 24f
}

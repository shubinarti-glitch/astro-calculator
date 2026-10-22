package ru.astrosmap.app.ui.assistant

import android.graphics.Region
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantCloudShapeTest {
    @Test fun textInsetsStayInsideAllTailOrientationsAtDifferentHeights() {
        for (height in listOf(300, 600, 1000)) {
            for (right in listOf(false, true)) for (top in listOf(false, true)) {
                val outline = cloudBubbleShape(right, top, 144f)
                    .createOutline(Size(464f, height.toFloat()), LayoutDirection.Ltr, Density(2f)) as Outline.Generic
                val region = Region().apply { setPath(outline.path.asAndroidPath(), Region(0, 0, 464, height)) }
                for (x in listOf(58, 232, 406)) for (y in listOf(54, height / 2, height - 54)) {
                    assertTrue("Text clipped: right=$right top=$top height=$height x=$x y=$y", region.contains(x, y))
                }
            }
        }
    }
}

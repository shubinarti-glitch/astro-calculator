package ru.astrosmap.app.ui.assistant

internal data class AssistantBubblePlacement(
    val x: Float,
    val y: Float,
    val maxHeight: Float,
    val tailOnRight: Boolean,
    val tailAtTop: Boolean,
)

/** Pixel coordinates; the caller constrains the bubble width to the viewport. */
internal fun placeAssistantBubble(
    petX: Float,
    petY: Float,
    petSize: Float,
    bubbleWidth: Float,
    bubbleHeight: Float,
    width: Float,
    height: Float,
    gap: Float,
    margin: Float,
): AssistantBubblePlacement {
    val insetX = margin.coerceIn(0f, width.coerceAtLeast(0f) / 2f)
    val insetY = margin.coerceIn(0f, height.coerceAtLeast(0f) / 2f)
    val availableHeight = (height - 2f * insetY).coerceAtLeast(0f)
    val bubbleW = bubbleWidth.coerceIn(0f, (width - 2f * insetX).coerceAtLeast(0f))
    val leftFits = petX - gap - insetX >= bubbleW
    val rightFits = width - insetX - petX - petSize - gap >= bubbleW
    val petCenterX = petX + petSize / 2f
    val petCenterY = petY + petSize / 2f

    if (leftFits || rightFits) {
        val onLeft = leftFits && (!rightFits || petCenterX >= width / 2f)
        val renderedHeight = bubbleHeight.coerceIn(0f, availableHeight)
        val y = (petCenterY - renderedHeight * 0.78f)
            .coerceIn(insetY, (height - insetY - renderedHeight).coerceAtLeast(insetY))
        return AssistantBubblePlacement(
            x = (if (onLeft) petX - gap - bubbleW else petX + petSize + gap)
                .coerceIn(insetX, (width - insetX - bubbleW).coerceAtLeast(insetX)),
            y = y,
            maxHeight = availableHeight,
            tailOnRight = onLeft,
            tailAtTop = petCenterY < y + renderedHeight / 2f,
        )
    }

    val above = (petY - gap - insetY).coerceAtLeast(0f)
    val below = (height - insetY - petY - petSize - gap).coerceAtLeast(0f)
    val onTop = above >= below
    // Keep this choice independent of measured bubble height to avoid layout oscillation.
    val maxHeight = maxOf(above, below).coerceAtMost(availableHeight)
        .takeIf { it > 0f } ?: availableHeight
    val renderedHeight = bubbleHeight.coerceIn(0f, maxHeight)
    val tailOnRight = petCenterX >= width / 2f
    val x = (petCenterX - bubbleW * if (tailOnRight) 0.80f else 0.20f)
        .coerceIn(insetX, (width - insetX - bubbleW).coerceAtLeast(insetX))
    val y = (if (onTop) petY - gap - renderedHeight else petY + petSize + gap)
        .coerceIn(insetY, (height - insetY - renderedHeight).coerceAtLeast(insetY))
    return AssistantBubblePlacement(x, y, maxHeight, tailOnRight, tailAtTop = !onTop)
}

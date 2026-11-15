package com.kirill.glassnotes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** «Телеграмный» fling-бихейвиор: нативный физический сплайн (как у OverScroller). */
@Composable
fun rememberTelegramFlingBehavior(): FlingBehavior {
    return ScrollableDefaults.flingBehavior()
}

/** Мягкий пружинный вертикальный overscroll (возвращает connection и modifier). */
@Composable
fun rememberSpringOverscroll(
    maxOverscroll: Dp = 44.dp
): Pair<NestedScrollConnection, Modifier> {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val overscroll = remember { Animatable(0f, visibilityThreshold = 0.1f) }
    val maxOverscrollPx = maxOverscroll.value * density.density
    val connection = remember(scope, overscroll, maxOverscrollPx) {
        SpringOverscrollConnection(scope, overscroll, maxOverscrollPx)
    }
    val modifier = Modifier.graphicsLayer { translationY = overscroll.value }
    return connection to modifier
}

private class SpringOverscrollConnection(
    private val scope: CoroutineScope,
    private val overscroll: Animatable<Float, AnimationVector1D>,
    private val maxOverscrollPx: Float
) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val current = overscroll.value
        if (current != 0f && available.y != 0f) {
            val reducing = (current > 0f && available.y < 0f) || (current < 0f && available.y > 0f)
            if (reducing) {
                val amount = minOf(abs(available.y), abs(current))
                val sign = if (current > 0f) -1f else 1f
                scope.launch { overscroll.snapTo(current + amount * sign) }
                return Offset(0f, amount * sign)
            }
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y != 0f && source == NestedScrollSource.UserInput) {
            val current = overscroll.value
            val damped = available.y * 0.48f
            val newOffset = (current + damped).coerceIn(-maxOverscrollPx, maxOverscrollPx)
            scope.launch { overscroll.snapTo(newOffset) }
            return Offset(0f, available.y)
        }
        if (abs(overscroll.value) > 0.5f && source != NestedScrollSource.UserInput) {
            scope.launch {
                overscroll.animateTo(0f, spring(dampingRatio = 0.64f, stiffness = 2200f))
            }
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (overscroll.value != 0f) {
            overscroll.animateTo(0f, spring(dampingRatio = 0.64f, stiffness = 2200f))
        }
        return Velocity.Zero
    }
}

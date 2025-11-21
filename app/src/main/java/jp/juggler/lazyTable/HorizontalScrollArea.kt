package jp.juggler.lazyTable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import jp.juggler.lazyTable.util.logcat

@Suppress("MayBeConstant", "RedundantSuppression")
private val DEBUG = false

/**
 * 大きなUIを包み、横スクロール表示する領域
 * Modifier.horizontalScrollと異なり、ドラッグ操作などには一切対応しない。
 * スクロール量をFloatStateで監視してcontentをオフセット表示するだけだ。
 * その性質ゆえに斜めスクロールに対応しやすい。
 *
 * @param modifier Modifier（paddingなどを指定可能）
 * @param stateScrollX 横スクロール量
 * @param stateScrollXMax (out)横スクロールの最大値
 * @param stateViewportWidth (out)可視範囲のピクセル数
 * @param content スクロール範囲内部をComposeするラムダ
 */
@Composable
fun HorizontalScrollArea(
    modifier: Modifier = Modifier,
    stateScrollX: FloatState,
    stateScrollXMax: MutableIntState = mutableIntStateOf(0),
    stateViewportWidth: MutableIntState = mutableIntStateOf(0),
    content: @Composable (visibleRangeX: State<IntRange>) -> Unit
) {
    val visibleRangeXState = remember {
        derivedStateOf {
            val scrollXRaw = stateScrollX.value
            val scrollXMax = stateScrollXMax.value
            val viewportWidth = stateViewportWidth.value
            val scrollXFixed = scrollXRaw.toInt().coerceIn(0, scrollXMax)
            val result = if (viewportWidth <= 0) {
                0..0
            } else {
                scrollXFixed until (scrollXFixed + viewportWidth)
            }
            if (DEBUG) {
                logcat.i("visibleRangeXState: scrollX=$scrollXRaw->$scrollXFixed, max=$scrollXMax, viewport=$viewportWidth, range=$result")
            }
            result
        }
    }
    SubcomposeLayout(
        modifier = modifier.clipToBounds()
    ) { constraints ->
        if (DEBUG) {
            logcat.i("measurePolicy start.")
        }
        val contentPlaceables = subcompose("content") {
            content(visibleRangeXState)
        }.map { measurable ->
            // コンテンツは制約なしで自然なサイズで計測
            measurable.measure(Constraints())
        }
        val contentWidth = contentPlaceables.maxOfOrNull { it.width } ?: 0
        val contentHeight = contentPlaceables.maxOfOrNull { it.height } ?: 0

        // ビューポートの幅（利用可能な幅）
        val newViewportWidth = constraints.maxWidth.coerceAtLeast(0)
        // スクロール可能な最大値を計算
        val newMaxScrollX = (contentWidth - newViewportWidth).coerceAtLeast(0)

        // 状態を更新（値が実際に変わった場合のみ）
        if (stateViewportWidth.value != newViewportWidth) {
            stateViewportWidth.value = newViewportWidth
        }
        if (stateScrollXMax.value != newMaxScrollX) {
            stateScrollXMax.value = newMaxScrollX
        }
        layout(constraints.maxWidth, contentHeight) {
            val visibleRange = visibleRangeXState.value
            val x = -visibleRange.first
            if (DEBUG) {
                logcat.i("layout: visibleRange=$visibleRange, x=$x, stateScrollX=${stateScrollX.value}")
            }
            for (it in contentPlaceables) {
                it.placeRelative(x = x, y = 0)
            }
        }
    }
}

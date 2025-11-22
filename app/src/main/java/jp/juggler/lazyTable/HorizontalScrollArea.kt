package jp.juggler.lazyTable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
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
    stateContentWidth: MutableIntState = mutableIntStateOf(0),
    content: @Composable (visibleRangeX: State<IntRange>) -> Unit
) {
    // レイアウト変化を追跡して stateScrollXMax を更新する
    LaunchedEffect(stateContentWidth.intValue, stateViewportWidth.intValue) {
        stateScrollXMax.intValue = (stateContentWidth.intValue - stateViewportWidth.intValue)
            .coerceAtLeast(0)
    }
    // Floatのスクロール位置をIntの可視範囲に変換する
    // 1px未満のスクロール量変化では発火しない
    val visibleRangeXState = remember(
        stateScrollX,
        stateScrollXMax,
        stateViewportWidth,
    ) {
        derivedStateOf {
            val scrollXRaw = stateScrollX.floatValue
            val scrollXMax = stateScrollXMax.intValue
            val viewportWidth = stateViewportWidth.intValue
            val scrollXFixed = scrollXRaw.toInt().coerceIn(0, scrollXMax)
            (scrollXFixed until (scrollXFixed + viewportWidth)).also {
                if (DEBUG) logcat.i("visibleRangeXState=$it")
            }
        }
    }
    SubcomposeLayout(
        modifier = modifier.clipToBounds()
    ) { constraints ->
        // measureパス: スクロール位置を一切参照しない
        val contentPlaceables = subcompose("content") {
            content(visibleRangeXState)
        }.map { measurable ->
            // コンテンツは制約なしで自然なサイズで計測
            measurable.measure(Constraints())
        }

        val contentWidth = contentPlaceables.maxOfOrNull { it.width } ?: 0
        val contentHeight = contentPlaceables.maxOfOrNull { it.height } ?: 0
        val newViewportWidth = constraints.maxWidth.coerceAtLeast(0)
        stateViewportWidth.intValue = newViewportWidth
        stateContentWidth.intValue = contentWidth
        layout(constraints.maxWidth, contentHeight) {
            // layoutパス: スクロール位置(可視範囲)にアクセスする
            val x = -visibleRangeXState.value.first
            if (DEBUG) logcat.i("layout: x=$x")
            for (it in contentPlaceables) {
                it.placeRelative(x = x, y = 0)
            }
        }
    }
}

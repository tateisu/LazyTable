package jp.juggler.lazyTable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope

fun LazyListScope.bigTable(
    padHorizontal: Dp,
    horizontalScrollables: MutableMap<String, HorizontalScrollable>,
    coroutineScope: CoroutineScope,
    name: String,
    data: List<List<String>>,
    lazyListState: LazyListState,
    stateSizes: State<LazyTableSizes?>,
) {
    item {
        Text(
            text = "[$name]見出し",
            modifier = Modifier.padding(padHorizontal)
        )
    }
    item("table_$name") {
        stateSizes.value?.let { sizes ->
            val horizontalScrollable = horizontalScrollables
                .getOrPut(name) { HorizontalScrollable(coroutineScope) }
            HorizontalScrollArea(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = padHorizontal) // 始端はスクロール範囲外にpadding。終端は異なる
                    .onPlaced { layoutCoordinates ->
                        val rect = layoutCoordinates.boundsInParent()
                        horizontalScrollable.boundsInParent.value = rect
                    },
                stateScrollX = horizontalScrollable.stateScrollX,
                stateScrollXMax = horizontalScrollable.stateScrollXMax,
            ) { visibleRangeXState ->
                LazyTable(
                    // 終端はスクロール範囲内にパディング
                    modifier = Modifier.padding(end = padHorizontal),
                    sizes = sizes,
                    dataKey = name,
                    cellContent = { iRow, iCol, _, _ ->
                        TableCell(iRow, iCol, data[iRow][iCol])
                    },
                    visibleRangeX = visibleRangeXState,
                    visibleRangeY = rememberVisibleRangeY(
                        lazyListState = lazyListState,
                        stateTableBounds = horizontalScrollable.boundsInParent,
                    ),
                    stickyLeft = true,
                    stickyTop = true,
                )
            }
        }
    }
    item {
        Text(
            text = "テーブルの後のアイテム",
            modifier = Modifier.padding(padHorizontal)
        )
    }
}


@Composable
private fun rememberVisibleRangeY(
    lazyListState: LazyListState,
    // 親から見たLazyTableの領域
    stateTableBounds: State<Rect>,
): State<IntRange> = remember(lazyListState, stateTableBounds) {
    derivedStateOf {
        val tableBounds = stateTableBounds.value
        val tableTop = tableBounds.top.toInt()
        val tableHeight = tableBounds.bottom.toInt() - tableTop
        val viewportHeight = lazyListState.layoutInfo.viewportSize.height
        // 親の表示高さ
        // 親ビューの表示高さとboundInParentを使って縦方向の可視範囲を計算する
        // 親のビューポートをテーブル座標系に変換して、テーブル高さでクリップ
        var range = (0 - tableTop).coerceIn(0, tableHeight)..
                (viewportHeight - tableTop).coerceIn(0, tableHeight)
        // LazyColumnの可視範囲から領域が一致するものを探す
        val itemInfo = lazyListState.layoutInfo.visibleItemsInfo.find {
            tableTop in it.offset until it.offset + it.size
        }
        if (itemInfo?.index == lazyListState.firstVisibleItemIndex) {
            // 見つかって、そのindexがfirstVisibleItemIndexと同じなら、
            // より高頻度に更新される firstVisibleItemScrollOffset を使うことでsticky表示がズレにくくなる
            range = lazyListState.firstVisibleItemScrollOffset..range.last
        }
        if (range.isEmpty()) 0..0 else range
    }
}

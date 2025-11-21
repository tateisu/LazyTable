package jp.juggler.lazyTable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeLayoutState
import androidx.compose.ui.layout.SubcomposeSlotReusePolicy
import androidx.compose.ui.unit.Constraints
import jp.juggler.lazyTable.util.logcat

@Suppress("MayBeConstant", "RedundantSuppression")
private val DEBUG = false

/**
 * 2次元の表を遅延 Composeする。
 * - dataKeyが変化したら全体のサイズ計測が再び行われる。
 * - 各セルの幅や高さの計測は単純にラムダ式を呼び出す。実装部分はViewフレームワークで計測することになるだろう。
 * - visibleRangeX, visibleRangeY が変化したら subcomposeのmeasurePolicyが再実行される。
 */
@Composable
fun LazyTable(
    modifier: Modifier = Modifier,
    // 列や行のサイズ情報。
    sizes: LazyTableSizes,
    // セルの内容をComposeするラムダ。
    cellContent: @Composable (iRow: Int, iCol: Int, width: Int, height: Int) -> Unit,
    // 再計測のトリガとなるキー。ロード時刻と表のnameなどを組み合わせた文字列にするとよい。
    dataKey: Any,
    // 表の左上のピクセル座標を基準にした可視範囲。
    visibleRangeX: State<IntRange>,
    visibleRangeY: State<IntRange>,
    // 左端の列をsticky表示するなら真。
    stickyLeft: Boolean,
    // 上端の行をsticky表示するなら真。
    stickyTop: Boolean,
    // セルのsubcomposeのキャッシュ最大数
    maxSlotsToRetainForReuse: Int = 1000,
) {
    val state = remember(dataKey, maxSlotsToRetainForReuse) {
        SubcomposeLayoutState(SubcomposeSlotReusePolicy(maxSlotsToRetainForReuse))
    }
    SubcomposeLayout(
        state = state,
        modifier = modifier,
    ) {
        val totalWidth = sizes.totalWidth
        val totalHeight = sizes.totalHeight
        val colLefts = sizes.colLefts
        val colWidths = sizes.colWidths
        val rowHeights = sizes.rowHeights
        val rowTops = sizes.rowTops
        if (DEBUG) {
            logcat.i("visibleRange y=${visibleRangeY.value}, x=${visibleRangeX.value}")
        }
        val visibleRangeCols = with(visibleRangeX.value) {
            colLefts.valueToIndex(first)..colLefts.valueToIndex(last)
        }
        val visibleRangeRows = with(visibleRangeY.value) {
            rowTops.valueToIndex(first)..rowTops.valueToIndex(last)
        }
        if (DEBUG) {
            logcat.i("visibleRange rows=$visibleRangeRows, cols=$visibleRangeCols")
        }

        // 計測パス：可視範囲のセルをsubcomposeしてplaceableのリストを作成
        val placeables = buildList {
            fun addPlaceables(x: Int, y: Int, placeables: List<Placeable>) =
                add(Triple(x, y, placeables))

            fun prepareCell(
                iRow: Int,
                iCol: Int,
                height: Int,
                overrideX: Int? = null,
                overrideY: Int? = null,
            ) {
                val width = colWidths[iCol]
                if (width <= 0) return
                addPlaceables(
                    x = overrideX ?: colLefts[iCol],
                    y = overrideY ?: rowTops[iRow],
                    placeables = subcompose("cell-$iRow-$iCol") {
                        cellContent(iRow, iCol, width, height)
                    }.map { it.measure(Constraints.fixed(width, height)) }
                )
            }

            // 最大値は最後の列の手前の位置。
            // つまりテーブル全体の幅-最後の列の幅-sticky列自体の幅。
            val stickyLeftX = visibleRangeX.value.first
                .coerceAtMost(totalWidth - colWidths.first() - colWidths.last())
                .coerceAtLeast(0)

            fun prepareRow(
                iRow: Int,
                overrideY: Int? = null,
            ) {
                val height = rowHeights[iRow]
                if (height <= 0) return
                for (iCol in visibleRangeCols) {
                    if (stickyLeft && iCol == 0) continue
                    prepareCell(iRow, iCol, height = height, overrideY = overrideY)
                }
                if (stickyLeft) {
                    prepareCell(
                        iRow,
                        0,
                        height = height,
                        overrideX = stickyLeftX,
                        overrideY = overrideY
                    )
                }
            }
            if (totalWidth > 0 && totalHeight > 0) {
                for (iRow in visibleRangeRows) {
                    if (stickyTop && iRow == 0) continue
                    prepareRow(iRow)
                }
                if (stickyTop) {
                    // 最大値は最後の行の手前の位置。
                    // つまりテーブル全体の高さ-最後の行の高さ-sticky列自体の高さ。
                    val stickyTopY = visibleRangeY.value.first
                        .coerceAtMost(totalHeight - rowHeights.first() - rowHeights.last())
                        .coerceAtLeast(0)
                    prepareRow(0, overrideY = stickyTopY)
                }
            }
        }
        layout(totalWidth, totalHeight) {
            for ((x, y, p) in placeables) {
                for (it in p) {
                    it.placeRelative(x, y)
                }
            }
        }
    }
}

/**
 * Int配列を二分探索してインデクスを返す。
 * valueが範囲外でも、インデクスは範囲内にクリップする。
 * 配列がカラの場合は単純に0を返す。
 *
 * @receiver 区間ごとに開始値を格納した配列
 * @param value 探索したい値
 * @return 配列がカラなら0。ほかは最も近い区間のインデクスを返す。
 */
private fun IntArray.valueToIndex(value: Int): Int {
    return when {
        isEmpty() || value <= first() -> 0
        value >= last() -> size - 1
        else -> {
            var iStart = 0
            var iEnd = size - 1
            while (iEnd > iStart) {
                // iEnd > iStart ならiMidは必ずiEndより小さい
                // つまりsize-1より小さいのでiMid+1が範囲外になることはない
                val iMid = (iEnd + iStart) shr 1
                when {
                    value < this[iMid] -> iEnd = iMid - 1
                    value >= this[iMid + 1] -> iStart = iMid + 1
                    else -> return iMid
                }
            }
            iStart
        }
    }
}

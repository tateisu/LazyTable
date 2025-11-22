package jp.juggler.lazyTable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
    val totalWidth = sizes.totalWidth
    val totalHeight = sizes.totalHeight
    val colLefts = sizes.colLefts
    val colWidths = sizes.colWidths
    val rowHeights = sizes.rowHeights
    val rowTops = sizes.rowTops
    val state = remember(dataKey, maxSlotsToRetainForReuse) {
        SubcomposeLayoutState(SubcomposeSlotReusePolicy(maxSlotsToRetainForReuse))
    }
    // ピクセル単位の可視範囲を行/列単位の範囲に変換することで measure パスの頻度を下げる
    val stateVisibleCols = remember(colLefts, visibleRangeX) {
        derivedStateOf {
            with(visibleRangeX.value) {
                if (DEBUG) logcat.i("visibleRangeX=$this")
                colLefts.valueToIndex(first)..colLefts.valueToIndex(last)
            }
        }
    }
    val stateVisibleRows = remember(rowTops, visibleRangeY) {
        derivedStateOf {
            with(visibleRangeY.value) {
                if (DEBUG) logcat.i("visibleRangeY=$this")
                rowTops.valueToIndex(first)..rowTops.valueToIndex(last)
            }
        }
    }
    SubcomposeLayout(
        state = state,
        modifier = modifier,
    ) {
        // measureパス：可視範囲のセルをsubcomposeしてplaceableのリストを作成
        // Note: measureパスでは可視範囲のピクセル位置を一切参照しない。
        // これによりピクセル単位のスクロールではmeasureは発生しなくなり、セル境界をまたぐ場合のみmeasureパスが実行される。
        val cellPlaceables = buildList {
            val visibleCols = stateVisibleCols.value
            val visibleRows = stateVisibleRows.value
            if (DEBUG) logcat.i("visibleRange rows=$visibleRows, cols=$visibleCols")

            fun prepareCell(
                iRow: Int,
                iCol: Int,
                height: Int,
                stickyY: Boolean,
                stickyX: Boolean,
            ) {
                val width = colWidths[iCol]
                if (width <= 0) return
                val x = if (stickyX) null else colLefts[iCol]
                val y = if (stickyY) null else rowTops[iRow]
                for (m in subcompose("cell-$iRow-$iCol") {
                    cellContent(iRow, iCol, width, height)
                }.map { it.measure(Constraints.fixed(width, height)) }) {
                    add(CellPlaceable(x = x, y = y, measured = m))
                }
            }

            fun prepareRow(iRow: Int, stickyY: Boolean) {
                val height = rowHeights[iRow]
                if (height <= 0) return
                for (iCol in visibleCols) {
                    if (stickyLeft && iCol == 0) continue
                    prepareCell(iRow, iCol, height = height, stickyY = stickyY, stickyX = false)
                }
                if (stickyLeft) {
                    prepareCell(iRow, 0, height = height, stickyY = stickyY, stickyX = true)
                }
            }
            if (totalWidth > 0 && totalHeight > 0) {
                for (iRow in visibleRows) {
                    if (stickyTop && iRow == 0) continue
                    prepareRow(iRow, stickyY = false)
                }
                if (stickyTop) prepareRow(0, stickyY = true)
            }
        }
        layout(totalWidth, totalHeight) {
            // layoutパス
            // 親Composableがスクロールした際も可視範囲のセル全てplaceし直すので軽量でなければならない。
            // 可視範囲のピクセル位置を参照してstickyセルを移動するのもlayoutパスで行う。
            // 最大値は最後の列の手前の位置。
            // つまりテーブル全体の幅-最後の列の幅-sticky列自体の幅。
            val stickyLeftX = visibleRangeX.value.first
                .coerceAtMost(totalWidth - colWidths.first() - colWidths.last())
                .coerceAtLeast(0)
            // 最大値は最後の行の手前の位置。
            // つまりテーブル全体の高さ-最後の行の高さ-sticky列自体の高さ。
            val stickyTopY = visibleRangeY.value.first
                .coerceAtMost(totalHeight - rowHeights.first() - rowHeights.last())
                .coerceAtLeast(0)
            for (cp in cellPlaceables) {
                with(cp) {
                    measured.placeRelative(x ?: stickyLeftX, y ?: stickyTopY)
                }
            }
        }
    }
}

/**
 * LazyTableのmeasureパスで列挙される、可視範囲のセルの配置情報
 */
private class CellPlaceable(
    val x: Int?, // null means sticky
    val y: Int?, // null means sticky
    val measured: Placeable,
)

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

package jp.juggler.lazyTable

import android.os.SystemClock
import jp.juggler.lazyTable.util.logcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * LazyTableのセル幅/高さの情報
 */
data class LazyTableSizes(
    val dataKey: Any,
    val createdAt: Long,
    // 各列の幅
    val colWidths: IntArray,
    // 各行の高さ
    val rowHeights: IntArray,
    // 各列のX位置
    val colLefts: IntArray,
    // 各行のY位置
    val rowTops: IntArray,
    // 表のトータルサイズ(外部余白を含まない
    val totalWidth: Int,
    val totalHeight: Int,
) {
    override fun equals(other: Any?) = when {
        this === other -> true
        other !is LazyTableSizes -> false
        else -> dataKey == other.dataKey && createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = createdAt.hashCode()
        result = 31 * result + dataKey.hashCode()
        return result
    }

    init{
        if(rowHeights.isNotEmpty()){
            logcat.i("rowHeights min=${rowHeights.min()}, max=${rowHeights.max()}, avg=${rowHeights.average()}")
        }
        if(colWidths.isNotEmpty()){
            logcat.i("colWidths min=${colWidths.min()}, max=${colWidths.max()}, avg=${colWidths.average()}")
        }
    }
}

suspend fun computeLazyTableSizes(
    dataKey: Any,
    createdAt: Long,
    colSize: Int,
    rowSize: Int,
    // セルの幅を計測するラムダ。おそらくViewフレームワークで実装される
    measureCellWidth: (iRow: Int, iCol: Int) -> Int,
    // セルの高さを計測するラムダ。おそらくViewフレームワークで実装される
    measureCellHeight: (iRow: Int, iCol: Int, width: Int) -> Int,
): LazyTableSizes {
    logcat.i("computeLazyTableSizes start!")

    var lastDelayStart = SystemClock.elapsedRealtime()

    // 定期的にdelay(1L) を呼び出してMainLooperに処理を委譲する
    suspend fun delayIfBlocked() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDelayStart >= 333L) {
            @Suppress("AssignedValueIsNeverRead")
            lastDelayStart = now
            delay(1L)
        }
    }

    val colWidths = IntArray(colSize) { iCol ->
        (0 until rowSize).maxOfOrNull { iRow ->
            delayIfBlocked()
            measureCellWidth(iRow, iCol)
        } ?: 0
    }
    val rowHeights = IntArray(rowSize) { iRow ->
        (0 until colSize).maxOfOrNull { iCol ->
            delayIfBlocked()
            measureCellHeight(iRow, iCol, colWidths[iCol])
        } ?: 0
    }
    return withContext(Dispatchers.Default) {
        val colLefts = IntArray(colWidths.size)
        for (i in 1 until colLefts.size) {
            colLefts[i] = colLefts[i - 1] + colWidths[i - 1]
        }
        val rowTops = IntArray(rowHeights.size)
        for (i in 1 until rowTops.size) {
            rowTops[i] = rowTops[i - 1] + rowHeights[i - 1]
        }
        val totalWidth = if (colWidths.isEmpty()) 0 else colLefts.last() + colWidths.last()
        val totalHeight = if (rowHeights.isEmpty()) 0 else rowTops.last() + rowHeights.last()
        LazyTableSizes(
            dataKey = dataKey,
            createdAt = createdAt,
            colWidths = colWidths,
            rowHeights = rowHeights,
            colLefts = colLefts,
            rowTops = rowTops,
            totalWidth = totalWidth,
            totalHeight = totalHeight,
        )
    }
}

/**
 * computeLazyTableSizes とほぼ同じだが、
 * Compose Previewようなので全部同期的に処理する。
 */
fun computeLazyTableSizesForPreview(
    dataKey: Any,
    createdAt: Long,
    colSize: Int,
    rowSize: Int,
    measureCellWidth: (iRow: Int, iCol: Int) -> Int,
    measureCellHeight: (iRow: Int, iCol: Int, width: Int) -> Int,
): LazyTableSizes {
    logcat.i("computeLazyTableSizesForPreview start!")

    val colWidths = IntArray(colSize) { iCol ->
        (0 until rowSize).maxOfOrNull { iRow ->
            measureCellWidth(iRow, iCol)
        } ?: 0
    }
    val rowHeights = IntArray(rowSize) { iRow ->
        (0 until colSize).maxOfOrNull { iCol ->
            measureCellHeight(iRow, iCol, colWidths[iCol])
        } ?: 0
    }

    val colLefts = IntArray(colWidths.size)
    for (i in 1 until colLefts.size) {
        colLefts[i] = colLefts[i - 1] + colWidths[i - 1]
    }
    val rowTops = IntArray(rowHeights.size)
    for (i in 1 until rowTops.size) {
        rowTops[i] = rowTops[i - 1] + rowHeights[i - 1]
    }
    val totalWidth = if (colWidths.isEmpty()) 0 else colLefts.last() + colWidths.last()
    val totalHeight = if (rowHeights.isEmpty()) 0 else rowTops.last() + rowHeights.last()
    return LazyTableSizes(
        dataKey = dataKey,
        createdAt = createdAt,
        colWidths = colWidths,
        rowHeights = rowHeights,
        colLefts = colLefts,
        rowTops = rowTops,
        totalWidth = totalWidth,
        totalHeight = totalHeight,
    )
}

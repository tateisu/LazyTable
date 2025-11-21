package jp.juggler.lazyTable

import android.view.View
import android.view.View.MeasureSpec.makeMeasureSpec
import android.view.ViewGroup
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import jp.juggler.lazyTable.databinding.MeasureCellBinding

abstract class CellMeasurer<T : Any> {
    // 派生クラスが実装すること
    protected abstract fun measureWidthImpl(iRow: Int, iCol: Int, item: T): Int
    protected abstract fun measureHeightImpl(iRow: Int, iCol: Int, item: T, width: Int): Int

    // LRUキャッシュ（最大1000エントリ）
    private val widthCache = object : LinkedHashMap<T, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<T, Int>?) = size > 1000
    }
    private val heightCache = object : LinkedHashMap<Pair<T, Int>, Int>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<T, Int>, Int>?) =
            size > 1000
    }

    // itemにより値をキャッシュする
    // 今回はセル位置によりキャッシュキーを変える機能はない
    fun measureWidth(iRow: Int, iCol: Int, item: T): Int =
        widthCache.getOrPut(item) { measureWidthImpl(iRow, iCol, item) }

    // itemにより値をキャッシュする
    // 今回はセル位置によりキャッシュキーを変える機能はない
    fun measureHeight(iRow: Int, iCol: Int, item: T, width: Int): Int =
        heightCache.getOrPut(item to width) { measureHeightImpl(iRow, iCol, item, width) }
}

class CellMeasurerViewBinding(
    val measureCellBinding: MeasureCellBinding,
) : CellMeasurer<String>() {
    private fun bind(text: String) {
        measureCellBinding.tvValue.text = text
    }

    private fun View.updateLayoutWidth(w: Int) {
        layoutParams = when (val lp = layoutParams) {
            null -> ViewGroup.LayoutParams(
                w,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )

            else -> lp.apply { this.width = w }
        }
    }

    override fun measureWidthImpl(
        iRow: Int,
        iCol: Int,
        item: String,
    ): Int = with(measureCellBinding) {
        bind(item)
        root.updateLayoutWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        tvValue.updateLayoutWidth(ViewGroup.LayoutParams.WRAP_CONTENT)
        root.measure(
            makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root.measuredWidth
    }

    override fun measureHeightImpl(
        iRow: Int,
        iCol: Int,
        item: String,
        width: Int,
    ): Int = with(measureCellBinding) {
        bind(item)
        root.updateLayoutWidth(width)
        tvValue.updateLayoutWidth(ViewGroup.LayoutParams.MATCH_PARENT)
        root.measure(
            makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        root.measuredHeight
    }
}

class CellMeasurerPreview<T : Any>(
    val density: Density,
) : CellMeasurer<T>() {
    override fun measureWidthImpl(
        iRow: Int,
        iCol: Int,
        item: T,
    ): Int = with(density) { 60.dp.roundToPx() }

    override fun measureHeightImpl(
        iRow: Int,
        iCol: Int,
        item: T,
        width: Int
    ): Int = with(density) { 48.dp.roundToPx() }
}

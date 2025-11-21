package jp.juggler.lazyTable

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import jp.juggler.lazyTable.ComposeCompatibleTextView.ComposeInfo
import jp.juggler.lazyTable.databinding.MeasureCellBinding
import jp.juggler.lazyTable.ui.theme.AppTheme
import jp.juggler.lazyTable.util.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.math.abs

@Suppress("MayBeConstant", "RedundantSuppression")
private val DEBUG = false

class MainActivity : ComponentActivity() {
    private var lastTouchX = 0f
    private var isDragging = false
    private val activeHorizontalScroll = mutableStateOf<HorizontalScrollable?>(null)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = ev.x
                isDragging = false
                if (DEBUG) {
                    logcat.i("dispatchTouchEvent DOWN: x=${ev.x}")
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = ev.x - lastTouchX
                if (!isDragging && abs(deltaX) > 10) { // 10px threshold
                    isDragging = true
                    if (DEBUG) {
                        logcat.i("dispatchTouchEvent: drag started")
                    }
                }
                if (isDragging) {
                    // activeHorizontalScrollがセットされていれば横移動を送信
                    activeHorizontalScroll.value?.let { horizontalScrollable ->
                        val consumed = horizontalScrollable.consumeScrollX(deltaX)
                        if (DEBUG) {
                            logcat.i("dispatchTouchEvent MOVE: deltaX=$deltaX, consumed=$consumed")
                        }
                    }
                }
                lastTouchX = ev.x
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    activeHorizontalScroll.value?.onPointerUp()
                }
                isDragging = false
                activeHorizontalScroll.value = null
                if (DEBUG) {
                    logcat.i("dispatchTouchEvent UP/CANCEL")
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private val cellMeasurer by lazy {
        CellMeasurerViewBinding(MeasureCellBinding.inflate(layoutInflater))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                val textStyle = LocalTextStyle.current
                val density = LocalDensity.current
                val fontFamilyResolver = LocalFontFamilyResolver.current

                logcat.i(
                    "ComposeInfo: fontSize=${textStyle.fontSize}, lineHeight=${textStyle.lineHeight}, " +
                            "letterSpacing=${textStyle.letterSpacing}, fontWeight=${textStyle.fontWeight}, " +
                            "fontStyle=${textStyle.fontStyle}, fontFamily=${textStyle.fontFamily}, " +
                            "textAlign=${textStyle.textAlign}, lineHeightStyle=${textStyle.lineHeightStyle}, " +
                            "platformStyle=${textStyle.platformStyle}"
                )

                cellMeasurer.measureCellBinding.tvValue.composeInfo = ComposeInfo(
                    textStyle = textStyle,
                    density = density,
                    fontFamilyResolver = fontFamilyResolver,
                )

                ScreenContent(
                    tables = tables,
                    activeHorizontalScroll = activeHorizontalScroll,
                )
            }
        }
        lifecycleScope.launch {
            // composeInfoが設定されるのを待つ
            withTimeout(5000L) {
                while (cellMeasurer.measureCellBinding.tvValue.composeInfo == null) {
                    delay(10L)
                }
            }
            logcat.i("composeInfo is set, starting table size computation")

            for (t in tables) {
                t.stateSizes.value = computeTableSizes(
                    name = t.name,
                    data = t.data,
                    cellMeasurer = cellMeasurer,
                    computer = { dataKey, createdAt, colSize, rowSize, measureCellWidth, measureCellHeight ->
                        computeLazyTableSizes(
                            dataKey = dataKey,
                            createdAt = createdAt,
                            colSize = colSize,
                            rowSize = rowSize,
                            measureCellWidth = measureCellWidth,
                            measureCellHeight = measureCellHeight,
                        )
                    },
                )
            }
        }
    }
}

private val tables = listOf(
    createTableData("A", rows = 5, cols = 80),
    createTableData("B", rows = 100, cols = 100),
    createTableData("C", rows = 2000, cols = 8)
)

private inline fun <T:Any> computeTableSizes(
    name: String,
    data: List<List<T>>,
    cellMeasurer: CellMeasurer<T>,
    computer: (
        dataKey: Any,
        createdAt: Long,
        colSize: Int,
        rowSize: Int,
        // セルの幅を計測するラムダ。おそらくViewフレームワークで実装される
        measureCellWidth: (iRow: Int, iCol: Int) -> Int,
        // セルの高さを計測するラムダ。おそらくViewフレームワークで実装される
        measureCellHeight: (iRow: Int, iCol: Int, width: Int) -> Int,
    ) -> LazyTableSizes,
) = computer(
    name,
    System.currentTimeMillis(),
    data.first().size,
    data.size,
    { iRow, iCol -> cellMeasurer.measureWidth( iRow, iCol,data[iRow][iCol]) },
    { iRow, iCol, width -> cellMeasurer.measureHeight( iRow, iCol,data[iRow][iCol], width) },
)


@Preview(showBackground = true)
@Composable
private fun Preview() {
    val cellMeasurer = CellMeasurerPreview<String>( LocalDensity.current)
    for (t in tables) {
        t.stateSizes.value = computeTableSizes(
            name = t.name,
            data = t.data.take(3),
            cellMeasurer = cellMeasurer,
            computer = { dataKey, createdAt, colSize, rowSize, measureCellWidth, measureCellHeight ->
                computeLazyTableSizesForPreview(
                    dataKey = dataKey,
                    createdAt = createdAt,
                    colSize = colSize,
                    rowSize = rowSize,
                    measureCellWidth = measureCellWidth,
                    measureCellHeight = measureCellHeight,
                )
            },
        )
    }
    AppTheme {
        ScreenContent(
            tables = tables,
            activeHorizontalScroll = null,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenContent(
    @Suppress("SameParameterValue")
    tables: List<TableData>,
    activeHorizontalScroll: MutableState<HorizontalScrollable?>?,
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        val coroutineScope = rememberCoroutineScope()
        val horizontalScrollables = remember {
            mutableMapOf<String, HorizontalScrollable>()
        }
        val padHorizontal = 16.dp
        val lazyListState = rememberLazyListState()
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .padding(innerPadding)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitFirstDown(requireUnconsumed = false)
                            val touchPos = event.position
                            activeHorizontalScroll?.value = horizontalScrollables.values
                                .find { it.boundsInParent.value.contains(touchPos) }
                            if (DEBUG) logcat.i("find horizontalScrollable. pos=$touchPos, hit=${activeHorizontalScroll?.value != null}")
                        }
                    }
                }
        ) {
            for (t in tables) {
                bigTable(
                    padHorizontal,
                    horizontalScrollables = horizontalScrollables,
                    coroutineScope = coroutineScope,
                    name = t.name,
                    data = t.data,
                    lazyListState = lazyListState,
                    stateSizes = t.stateSizes,
                )
            }
        }
    }
}


private fun LazyListScope.bigTable(
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
                    visibleRangeY = remember {
                        derivedStateOf {
                            computeVisibleRangeY(
                                lazyListState = lazyListState,
                                viewportHeight = lazyListState.layoutInfo.viewportSize.height,
                                tableBounds = horizontalScrollable.boundsInParent.value,
                            ) ?: 0..0
                        }
                    },
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

fun computeVisibleRangeY(
    // 親の表示高さ
    viewportHeight: Int,
    // 親から見たLazyTableの領域
    tableBounds: Rect?,
    lazyListState: LazyListState,
): IntRange? {
    val tableTop = tableBounds?.top?.toInt() ?: return null
    val tableHeight = tableBounds.bottom.toInt() - tableTop
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

    return if (range.isEmpty()) null else range
}



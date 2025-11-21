package jp.juggler.lazyTable

import androidx.compose.runtime.mutableStateOf
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

class TableData(
    val name: String,
    val data: List<List<String>>,
) {
    val stateSizes = mutableStateOf<LazyTableSizes?>(null)
}

private val commaFormatter by lazy { DecimalFormat("#,###") }
private fun colValue(idx: Int): String {
    val divisor = BigDecimal(10).pow(idx % 10)
    val value = BigDecimal(123456789).divide(divisor, 0, RoundingMode.FLOOR)
    return commaFormatter.format(value) + "\n改行\nabc!/gy()"
}

fun createTableData(
    name: String,
    rows: Int,
    cols: Int
) = TableData(
    name = name,
    data = (0..rows).map { iRow ->
        when {
            iRow == 0 -> (0 until cols).map { "見出し\n$name $it" }
            else -> (0 until cols).map { iCol ->
                when (iCol % 5) {
                    0 -> iRow.toString()
                    1 -> colValue(iCol)
                    2 -> name
                    3 -> iCol.toString()
                    /* 4 */ else -> "-"
                }
            }
        }
    }
)

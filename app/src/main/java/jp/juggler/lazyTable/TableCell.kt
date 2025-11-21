package jp.juggler.lazyTable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import jp.juggler.lazyTable.util.logcat

// logcat出力するなら真
@Suppress("MayBeConstant", "RedundantSuppression")
private val DEBUG = false

@Composable
fun TableCell(iRow: Int, iCol: Int, text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(
                (if (iRow == 0) 2f else 0f) +
                        (if (iCol == 0) 1f else 0f)
            )
            .drawBehind {
                val size = this.size
                val borderWidth = 1.dp.toPx()
                when {
                    iRow == 0 -> drawRect(color = Color.Gray)
                    else -> {
                        drawRect(color = Color.White)
                        drawRect(
                            color = Color.Gray,
                            topLeft = Offset(x = 0f, y = size.height - borderWidth),
                            size = Size(size.width, borderWidth),
                        )
                    }
                }
                if (iCol == 0) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x = size.width - borderWidth, y = 0f),
                        size = Size(borderWidth, size.height),
                    )
                }
            }
            .padding(12.dp),
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            text = text,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = Color.Black,
            onTextLayout = { textLayoutResult ->
                // データ行（iRow >= 1）の最初のセルでログ出力
                if (DEBUG && iRow == 1 && iCol == 0) {
                    val mp = textLayoutResult.multiParagraph
                    @Suppress("KotlinConstantConditions")
                    logcat.i("TableCell[$iRow,$iCol]: text='${text.take(20)}', size=${textLayoutResult.size}, lineCount=${textLayoutResult.lineCount}")
                    logcat.i("TableCell multiParagraph: height=${mp.height}, firstBaseline=${mp.firstBaseline}, lastBaseline=${mp.lastBaseline}")
                    for (line in 0 until textLayoutResult.lineCount) {
                        logcat.i(
                            "TableCell line[$line]: top=${textLayoutResult.getLineTop(line)}, bottom=${
                                textLayoutResult.getLineBottom(
                                    line
                                )
                            }, baseline=${textLayoutResult.getLineBaseline(line)}, start=${
                                textLayoutResult.getLineStart(
                                    line
                                )
                            }, end=${textLayoutResult.getLineEnd(line)}"
                        )
                    }
                }
            },
        )
    }
}

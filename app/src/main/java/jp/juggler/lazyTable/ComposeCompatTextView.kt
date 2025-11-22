package jp.juggler.lazyTable

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.text.Layout
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withSave
import jp.juggler.lazyTable.util.logcat
import kotlin.math.ceil

/**
 * Compose の Text と同じレンダリングエンジンを使用してテキストを描画・計測するカスタムView。
 * ViewフレームワークでComposeと完全に一致した計測を行うために使用する。
 */
class ComposeCompatTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object {
        private val DEBUG = "false".toBoolean()

        /**
         * Composeテーマから取得した情報をセットすること。
         */
        var composeInfo: ComposeInfo? = null

        @Suppress("unused")
        private fun measureModeString(spec: Int): String =
            when (MeasureSpec.getMode(spec)) {
                MeasureSpec.EXACTLY -> "EXACTLY"
                MeasureSpec.UNSPECIFIED -> "UNSPECIFIED"
                MeasureSpec.AT_MOST -> "AT_MOST"
                else -> "%x".format(this)
            }

        /**
         * TypedArray から SPサイズを読む。
         * - getDimensionPixelSize()でpx値を読む
         * - 0ならnullを返す
         * - 非nullならSPに逆変換する
         */
        private fun TypedArray.getDimensionSpSize(index: Int): Float? =
            getDimensionPixelSize(index, 0)
                .takeIf { it != 0 }?.toFloat()?.let { px ->
                    when {
                        android.os.Build.VERSION.SDK_INT >= 34 -> TypedValue.deriveDimension(
                            TypedValue.COMPLEX_UNIT_SP,
                            px,
                            resources.displayMetrics
                        )

                        else -> {
                            @Suppress("DEPRECATION")
                            px / resources.displayMetrics.scaledDensity
                        }
                    }
                }
    }

    data class ComposeInfo(
        val textStyle: TextStyle,
        val density: Density,
        val fontFamilyResolver: FontFamily.Resolver,
    )

    var text = ""
        set(value) {
            if (field != value) {
                field = value
                paragraphIntrinsics = null
                paragraph = null
                requestLayout()
            }
        }

    var textSizeSp = 14f
        set(value) {
            if (field != value) {
                field = value
                paragraphIntrinsics = null
                paragraph = null
                requestLayout()
            }
        }

    var textAlignment = Layout.Alignment.ALIGN_NORMAL
        set(value) {
            if (field != value) {
                field = value
                paragraph = null
                requestLayout()
            }
        }

    private var layoutWidth: Int = 0
    private var paragraphIntrinsics: ParagraphIntrinsics? = null
    private var paragraph: Paragraph? = null

    /**
     * レイアウトXMLプレビュー用のフォールバックComposeInfo。
     * 実行時にcomposeInfoがnullの場合や、isInEditMode()時に使用される。
     */
    private val fallbackComposeInfo by lazy {
        ComposeInfo(
            textStyle = TextStyle.Default,
            density = Density(context),
            fontFamilyResolver = createFontFamilyResolver(context),
        )
    }

    init {
        // XML属性の読み取り
        context.obtainStyledAttributes(
            attrs,
            R.styleable.ComposeCompatibleTextView,
            defStyleAttr,
            0
        ).apply {
            try {
                text = getString(R.styleable.ComposeCompatibleTextView_android_text) ?: ""
                getDimensionSpSize(R.styleable.ComposeCompatibleTextView_android_textSize)
                    ?.let { textSizeSp = it }
                textAlignment =
                    when (getInt(R.styleable.ComposeCompatibleTextView_android_textAlignment, 0)) {
                        1 -> Layout.Alignment.ALIGN_CENTER
                        2 -> Layout.Alignment.ALIGN_OPPOSITE
                        else -> Layout.Alignment.ALIGN_NORMAL
                    }
            } finally {
                recycle()
            }
        }
    }


    @SuppressLint("DrawAllocation")
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // XMLプレビューまたはcomposeInfoがnullの場合はフォールバックを使用
        val info = composeInfo ?: fallbackComposeInfo

        val preferredWidth = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(widthMeasureSpec)
            else -> ceil(ensureParagraphIntrinsics(info).maxIntrinsicWidth).toInt() +
                    (paddingLeft + paddingRight)
        }
        val newWidth = resolveSize(preferredWidth, widthMeasureSpec)
        val contentWidth = (newWidth - (paddingLeft + paddingRight)).coerceAtLeast(0)
        val p = ensureParagraph(info, contentWidth)
        val contentHeight = ceil(p.height).toInt()
        val newHeight = resolveSize(contentHeight + (paddingTop + paddingBottom), heightMeasureSpec)
        setMeasuredDimension(newWidth, newHeight)
    }

    private fun ensureParagraphIntrinsics(
        composeInfo: ComposeInfo,
    ): ParagraphIntrinsics = paragraphIntrinsics ?: ParagraphIntrinsics(
        text = text,
        style = composeInfo.textStyle.copy(
            fontSize = textSizeSp.sp,
            textAlign = when (textAlignment) {
                Layout.Alignment.ALIGN_CENTER -> TextAlign.Center
                Layout.Alignment.ALIGN_OPPOSITE -> TextAlign.End
                else -> TextAlign.Start
            }
        ),
        density = composeInfo.density,
        fontFamilyResolver = composeInfo.fontFamilyResolver,
    ).also { paragraphIntrinsics = it }

    private fun ensureParagraph(
        composeInfo: ComposeInfo,
        newWidth: Int,
    ): Paragraph = paragraph?.takeIf { layoutWidth == newWidth } ?: run {
        layoutWidth = newWidth
        Paragraph(
            paragraphIntrinsics = ensureParagraphIntrinsics(composeInfo),
            constraints = Constraints(maxWidth = newWidth),
            maxLines = Int.MAX_VALUE,
            ellipsis = false
        ).also { paragraph = it }.apply {
            if (DEBUG) {
                logcat.i(
                    "Paragraph: text='${text.take(20)}', height=${
                        height
                    }, firstBaseline=${
                        firstBaseline
                    }, lastBaseline=${
                        lastBaseline
                    }, lineCount=${
                        lineCount
                    }"
                )
                if (lineCount > 0) {
                    val lastLine = lineCount - 1
                    val lineBottom = getLineBottom(lastLine)
                    val lineBaseline = getLineBaseline(lastLine)
                    logcat.i(
                        "LastLine: top=${
                            getLineTop(lastLine)
                        }, bottom=${
                            lineBottom
                        }, baseline=${
                            lineBaseline
                        }, descent=${
                            lineBottom - lineBaseline
                        }"
                    )
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withSave {
            translate(paddingLeft.toFloat(), paddingTop.toFloat())
            paragraph?.paint(
                canvas = androidx.compose.ui.graphics.Canvas(canvas),
                color = androidx.compose.ui.graphics.Color.Black
            )
        }
    }
}

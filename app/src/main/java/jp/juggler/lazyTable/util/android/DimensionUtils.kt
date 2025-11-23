package jp.juggler.lazyTable.util.android

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

fun DisplayMetrics.dpToPx(dp: Float): Float = dp * density
fun DisplayMetrics.pxToDp(px: Float): Float = px / density
fun DisplayMetrics.spToPx(sp: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, this)

fun DisplayMetrics.pxToSp(px: Float): Float = when {
    // Android 14 (API 34) 以上: 非線形スケーリングを考慮した変換
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> TypedValue.deriveDimension(
        TypedValue.COMPLEX_UNIT_SP,
        px,
        this
    )
    // OSが古いなら従来の線形変換
    else -> {
        @Suppress("DEPRECATION")
        px / scaledDensity
    }
}

// DisplayMetricまでのアクセスのショートカット
fun Resources.dpToPx(dp: Float) = displayMetrics.dpToPx(dp)
fun Resources.pxToDp(px: Float) = displayMetrics.pxToDp(px)
fun Resources.spToPx(sp: Float) = displayMetrics.spToPx(sp)
fun Resources.pxToSp(px: Float) = displayMetrics.pxToSp(px)
fun Context.dpToPx(dp: Float) = resources.dpToPx(dp)
fun Context.pxToDp(px: Float) = resources.pxToDp(px)
fun Context.spToPx(sp: Float) = resources.spToPx(sp)
fun Context.pxToSp(px: Float) = resources.pxToSp(px)
fun View.dpToPx(dp: Float) = context.dpToPx(dp)
fun View.pxToDp(px: Float) = context.pxToDp(px)
fun View.spToPx(sp: Float) = context.spToPx(sp)
fun View.pxToSp(px: Float) = context.pxToSp(px)

// Intを返すバリエーション
fun Resources.dpToPxIntSize(dp: Float) = displayMetrics.dpToPx(dp).roundSize(dp)
fun Resources.spToPxIntSize(sp: Float) = displayMetrics.spToPx(sp).roundSize(sp)
fun Context.dpToPxIntSize(dp: Float) = resources.dpToPx(dp).roundSize(dp)
fun Context.spToPxIntSize(sp: Float) = resources.spToPx(sp).roundSize(sp)
fun View.dpToPxIntSize(dp: Float) = context.dpToPx(dp).roundSize(dp)
fun View.spToPxIntSize(sp: Float) = context.spToPx(sp).roundSize(sp)

/**
 * レシーバの値をintに丸める。
 * ただしsrcが0以外なら戻り値を絶対に0にしない。
 */
fun Float.roundSize(src: Float): Int = when {
    src == 0f -> 0
    !isFinite() -> error("roundSize: not a finite value. $this")
    src > 0f -> max(1, roundToInt())
    else -> min(-1, roundToInt())
}

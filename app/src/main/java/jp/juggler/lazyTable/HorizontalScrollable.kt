package jp.juggler.lazyTable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import jp.juggler.lazyTable.util.logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sign

@Suppress("MayBeConstant", "RedundantSuppression")
private val DEBUG = false

// LazyColumn中の横スクロール可能なエリアと、その横スクロール情報を管理する
class HorizontalScrollable(
    // 慣性スクロールの管理に使うCoroutineScope
    private val coroutineScope: CoroutineScope
) {
    companion object {
        val emptyRect = Rect(0f, 0f, 0f, 0f)
    }

    // 親(LazyColumn)座標系での横スクロール可能領域の矩形
    val boundsInParent: MutableState<Rect> = mutableStateOf(emptyRect)

    // 現在の横スクロール位置(read/write)
    val stateScrollX: MutableFloatState = mutableFloatStateOf(0f)

    // 横スクロール量の最大値。HorizontalScrollAreaなどのComposable関数から更新される
    val stateScrollXMax: MutableIntState = mutableIntStateOf(0)

    // 慣性スクロールの管理に使う    
    private val animatable = Animatable(0f)
    private var lastMoveTime = 0L
    private var lastMoveAmount = 0f

    // Activity.dispatchTouchEventから呼ばれる。
    // ドラッグ操作の横移動量を分配する。
    fun consumeScrollX(availableX: Float): Float {
        val currentTime = System.currentTimeMillis()
        lastMoveAmount = availableX
        lastMoveTime = currentTime

        // 慣性スクロール中の場合は停止
        if (animatable.isRunning) {
            coroutineScope.launch {
                animatable.stop()
            }
        }

        val oldScrollX = stateScrollX.floatValue
        val maxScrollX = stateScrollXMax.intValue.toFloat()
        val newScrollX = (oldScrollX - availableX).coerceIn(0f, maxScrollX)
        val moved = newScrollX - oldScrollX
        stateScrollX.floatValue = newScrollX
        if (DEBUG) {
            logcat.i("consumeScrollX: availableX=$availableX, old=$oldScrollX, new=$newScrollX, moved=$moved, max=$maxScrollX, wasAnimating=${animatable.isRunning}")
        }
        return if (sign(availableX) != sign(moved)) 0f else -moved
    }

    // Activity.dispatchTouchEventから呼ばれる。
    // ドラッグ操作の終了により、慣性スクロールを開始するかもしれない
    fun onPointerUp() {
        val currentTime = System.currentTimeMillis()
        val timeDelta = currentTime - lastMoveTime

        // 最近の移動があった場合のみ慣性スクロールを開始
        if (timeDelta < 100 && abs(lastMoveAmount) > 1f) {
            val velocity = lastMoveAmount * 50f // 速度を大幅に増加
            if (DEBUG) {
                logcat.i("onPointerUp: starting fling with velocity=$velocity")
            }
            startFling(velocity)
        } else {
            if (DEBUG) {
                logcat.i("onPointerUp: no fling - timeDelta=$timeDelta, lastMove=$lastMoveAmount")
            }
        }
    }

    private fun startFling(velocity: Float) {
        coroutineScope.launch {
            try {
                val maxScrollX = stateScrollXMax.intValue.toFloat()
                if (DEBUG) {
                    logcat.i("startFling: velocity=$velocity, currentScroll=${stateScrollX.floatValue}, maxScroll=$maxScrollX")
                }

                animatable.snapTo(stateScrollX.floatValue)
                animatable.animateDecay(
                    initialVelocity = -velocity,
                    animationSpec = exponentialDecay(frictionMultiplier = 1f) // 摩擦を減らす
                ) {
                    stateScrollX.floatValue = value
                        .coerceIn(0f, maxScrollX)
                }
                if (DEBUG) {
                    logcat.i("fling animation completed")
                }
            } catch (ex: Throwable) {
                when (ex) {
                    is CancellationException -> Unit
                    else -> logcat.e(ex, "animation failed.")
                }
            }
        }
    }
}

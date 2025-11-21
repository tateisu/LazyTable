package jp.juggler.lazyTable.util

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Logcatのプロパティ。
 * 将来的にDIするかもしれない
 */
val logcat: Logcat by lazy { LogcatImpl() }

/**
 * Logcat出力ユーティリティ
 */
@Suppress("unused")
interface Logcat {
    companion object {
        /**
         * Logcatのtag部分の文字列。利用者が好きに変更できる
         */
        var appTag = "AppLog"
    }

    fun log(lv: Int, message: String? = null, ex: Throwable? = null)
    fun e(ex: Throwable, message: String?) = log(lv = Log.ERROR, message = message, ex = ex)
    fun w(ex: Throwable, message: String?) = log(lv = Log.WARN, message = message, ex = ex)
    fun e(message: String) = log(lv = Log.ERROR, message = message)
    fun w(message: String) = log(lv = Log.WARN, message = message)
    fun i(message: String) = log(lv = Log.INFO, message = message)
    fun d(message: String) = log(lv = Log.DEBUG, message = message)
    fun v(message: String) = log(lv = Log.VERBOSE, message = message)
}

/**
 * レシーバの例外を文字列にダンプする
 */
private fun Throwable.stackTraceString(): String {
    // Don't replace this with Log.getStackTraceString() - it hides
    // UnknownHostException, which is not what we want.
    val sw = StringWriter(256)
    val pw = PrintWriter(sw, false)
    printStackTrace(pw)
    pw.flush()
    return sw.toString()
}

// スキップすべきクラス名のFQCN prefix
private val ignoreClassNames = arrayOf(
    "dalvik.system.VMStack",
    "java.lang.Thread",
    "jp.juggler.lazyTable.util.Logcat",
)

/**
 * 現在のスタックトレースから不要なものをスキップして、location(クラス、ファイル、行番号)を抽出した文字列を返す。
 * - Note: スキップした結果stackTraceElementがなかったら、代わりに "(unknown location)" を返す
 * - Note: R8難読化は考慮しない。そもそも呼び出されないはずだ
 */
private fun Array<StackTraceElement>.stackTraceToLocation(): String =
    find { entry -> ignoreClassNames.none { entry.className.startsWith(it) } }?.run {
        // FQCNの無名クラス部分（$以降）を除去してから最後の有効な名称を抽出
        className
            .substringBefore('$')
            .substringAfterLast('.')
    } ?: "(unknown location)"

/**
 * Android Logcatのメッセージ長制限（約4000バイト）
 */
private const val LOGCAT_MESSAGE_MAX = 4000

/**
 * メッセージ長制限に合わせてmessageを分割してlogcat出力する
 */
private fun logSplit(lv: Int, tag: String, message: String) {
    if (message.length <= LOGCAT_MESSAGE_MAX) {
        Log.println(lv, tag, message)
        return
    }
    // メッセージを一定長さで分割する
    val list = buildList {
        var start = 0
        while (start < message.length) {
            val end = (start + LOGCAT_MESSAGE_MAX).coerceAtMost(message.length)
            add(message.substring(start, end))
            start = end
        }
    }
    when (list.size) {
        // messageがカラになることは通常ないが、first()を呼ぶ前にチェックする
        0 -> Unit
        1 -> Log.println(lv, tag, list.first())
        else -> for (i in list.indices) {
            Log.println(lv, tag, "[${i + 1}/${list.size}] ${list[i]}")
        }
    }
}

private class LogcatImpl : Logcat {
    override fun log(lv: Int, message: String?, ex: Throwable?) {
        try{
            // リリースビルドならlogcat出力しない
            if (appBuild.isRelease) return
            logSplit(
                lv,
                Logcat.appTag,
                buildString {
                    // logcatのタグ名はOSバージョンによって長さ制限があるため、locationはメッセージに含める
                    append("${Thread.currentThread().stackTrace.stackTraceToLocation()}: ")
                    when {
                        ex == null ->
                            append(message.notBlank() ?: "(missing message)")

                        else -> {
                            message.notBlank()?.let {
                                append(message)
                                append(" ")
                            }
                            append(ex.stackTraceString())
                        }
                    }
                },
            )
        }catch(ex: Throwable){
            ex.printStackTrace()
        }
    }
}

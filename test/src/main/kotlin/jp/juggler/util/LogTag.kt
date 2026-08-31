package jp.juggler.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class LogTag(val tag: String) {
    companion object {
        // LogTag の出力先をオーバライドするプロパティ
        // アプリのMain.kt からセットされる
        var printLine: ((String) -> Unit)? = null

        private val defaultPrintLine: (String) -> Unit = {
            println(it)
        }

        private val logTimeFormat = LocalDateTime.Format {
            year()
            monthNumber()
            day()
            char('_')
            hour()
            minute()
            second()
            char('.')
            secondFraction(3)
        }

        // ローカル時刻の YYYYMMDD_HHMMSS.MMM を返す
        val logTimeStr: String
            get() {
                // コンテナの /etc/localtime は zoneinfo DB の外部ファイルを指すことがある。
                // Kotlin/Native の currentSystemDefault はその場合例外になるため UTC に退避する。

                val zone = runCatching { TimeZone.currentSystemDefault() }.getOrDefault(TimeZone.UTC)
                return logTimeFormat.format(Clock.System.now().toLocalDateTime(zone))
            }

        // 0: e 以上を表示
        // 1: w 以上を表示(default)
        // 2: i 以上を表示
        // 3: d 以上を表示
        // 4: v 以上を表示
        var level = 1
    }

    enum class LogLevel(val lvNum: Int) {
        E(0),
        W(1),
        I(2),
        D(3),
        V(4),
    }

    fun log(
        lv: LogLevel,
        msg: String,
    ) {
        if (lv.lvNum > level) return
        (printLine ?: defaultPrintLine)("$logTimeStr $tag/${lv.name} $msg")
    }

    fun logThrowable(
        lv: LogLevel,
        ex: Throwable,
        msg: String? = null,
    ) = log(
        lv = lv,
        msg = when {
            level > 1 -> when (msg) {
                null -> ex.stackTraceToString()
                else -> "$msg ${ex.stackTraceToString()}"
            }

            else -> when (msg) {
                null -> "${ex::class.simpleName} ${ex.message}"
                else -> "$msg ${ex::class.simpleName} ${ex.message}"
            }
        }
    )

    fun e(msg: String) = log(LogLevel.E, msg)
    fun w(msg: String) = log(LogLevel.W, msg)
    fun i(msg: String) = log(LogLevel.I, msg)
    fun d(msg: String) = log(LogLevel.D, msg)
    fun v(msg: String) = log(LogLevel.V, msg)

    fun e(ex: Throwable, msg: String? = null) = logThrowable(LogLevel.E, ex, msg)
    fun w(ex: Throwable, msg: String? = null) = logThrowable(LogLevel.W, ex, msg)
}

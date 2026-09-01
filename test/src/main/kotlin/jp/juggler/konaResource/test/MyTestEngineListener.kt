package jp.juggler.konaResource.test

import io.kotest.common.KotestInternal
import io.kotest.core.spec.SpecRef
import io.kotest.core.test.TestCase
import io.kotest.engine.listener.AbstractTestEngineListener
import io.kotest.engine.listener.TestEngineInitializedContext
import io.kotest.engine.test.TestResult
import jp.juggler.util.LogTag
import jp.juggler.util.getMachineTime
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.Instant

/**
 * テスト結果の出力を自由にカスタマイズするためのリスナー。
 * 未実装のメソッドは [AbstractTestEngineListener] の no-op を引き継ぐため、
 * 必要なメソッドだけを override して出力形式を調整できる。
 */
@OptIn(KotestInternal::class)
class MyTestEngineListener(
    scope: CoroutineScope,
) : AbstractTestEngineListener(), AutoCloseable {
    companion object {
        private val log = LogTag("TestReport")
    }

    private var passed = 0
    private var failed = 0
    private var ignored = 0
    private val activeTests = mutableMapOf<String, Instant>()
    private val lock = reentrantLock()
    private var watchJob: Job? = null

    init {
        if (LogTag.level < 2) LogTag.level = 2
        watchJob = scope.launch {
            while (true) {
                delay(2.minutes)
                lock.withLock {
                    val now = getMachineTime()
                    activeTests.map { (path, timeStart) ->
                        val elapsed: Duration = now - timeStart
                        if (elapsed >= 2.minutes) {
                            log.w(
                                "test long runs: ${
                                    elapsed.toString(DurationUnit.MINUTES, 1)
                                }ms $path ",
                            )
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        watchJob?.cancel()
    }

    // --------------------------------------

    private fun TestCase.path(): String =
        descriptor.testParts().joinToString("/")

    private fun TestCase.dumpTestCase() = buildString {
        fun addNotNull(name: String, value: Any?) {
            value ?: return
            if (isNotEmpty()) append(", ")
            append("$name=$value")
        }
        // descriptor,parent はネストするので適当に端折る
        // spec はログ出力から自明なので省略
        // test はNon-Nullのラムダ式なので省略
        // name はpath()とかぶるので省略
        addNotNull("type", type)
        addNotNull("xmethod", xmethod)
        addNotNull("factoryId", factoryId)
        addNotNull("config", config)
        addNotNull("source", source.takeIf { it != io.kotest.core.source.SourceRef.None })
    }

    // テストが開始前にIgnoreされた場合に呼ばれる
    override suspend fun testIgnored(testCase: TestCase, reason: String?) {
        ignored++
        log.d("testIgnored ${testCase.path()} reason=$reason")
    }

    override suspend fun testStarted(testCase: TestCase) {
        val path = testCase.path()
        log.i("testStarted $path ${testCase.dumpTestCase()}")
        lock.withLock {
            activeTests[path] = getMachineTime()
        }
    }

    override suspend fun testFinished(testCase: TestCase, result: TestResult) {
        val path = testCase.path()
        lock.withLock {
            activeTests.remove(path)
        }
        when {
            result.isIgnored -> {
                ignored++
                log.i("testFinished IGNORED $path")
            }

            result.isSuccess -> {
                passed++
                log.i("testFinished ✅ $path")
            }

            else -> {
                failed++
                log.i("testFinished ❌ $path")
            }
        }
    }

    // --------------------------------------

    override suspend fun specStarted(ref: SpecRef) {
        val specName = ref.kclass.qualifiedName ?: "(anonymous)"
        log.i("specStarted $specName")
    }

    override suspend fun specIgnored(kclass: KClass<*>, reason: String?) {
        val specName = kclass.qualifiedName ?: "(anonymous)"
        log.i("specIgnored $specName :$reason")
    }

    override suspend fun specFinished(ref: SpecRef, result: TestResult) {
        val specName = ref.kclass.qualifiedName ?: "(anonymous)"
        log.i("specFinished $specName $result")
    }

    // --------------------------------------

    override suspend fun engineStarted() {
        log.d("engineStarted")
    }

    override suspend fun engineInitialized(context: TestEngineInitializedContext) {
        log.d("engineInitialized $context")
    }

    override suspend fun engineFinished(t: List<Throwable>) {
        log.w("Summary: failed=$failed, ignored=$ignored, passed=$passed")
        for (it in t) {
            log.e(it)
        }
    }
}

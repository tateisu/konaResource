package jp.juggler.konaResource.testksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

private const val GENERATED_PACKAGE = "jp.juggler.konaResource.test.generated"
private const val GENERATED_FILE = "KotestSpecs"

/**
 * kotest の Spec テストクラス派生クラスを列挙してgeneratedソースを生成する
 * 生成結果は CLI(Main.kt / RunTest.kt)から参照して `TestEngineLauncher` で実行する。
 */
class TestClassListProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // KSP は deferred symbol が無くても process を複数回呼ぶため、二重生成を防ぐ。
        if (!generated) {
            generated = true
            generate(resolver)
        }
        return emptyList()
    }

    private fun generate(resolver: Resolver) {
        val specs = resolver.getAllFiles()
            .flatMap { it.declarations }
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.isKotestSpec() }
            .mapNotNull { it.qualifiedName?.asString() }
            .distinct()
            .sorted()
            .toList()

        logger.info("Kona test-ksp: found ${specs.size} test classes")

        val content = buildString {
            appendLine("package $GENERATED_PACKAGE")
            appendLine()
            appendLine("import io.kotest.common.KotestInternal")
            appendLine("import io.kotest.core.spec.SpecRef")
            appendLine()
            appendLine("@OptIn(KotestInternal::class)")
            appendLine("val kotestSpecs: List<SpecRef> = listOf(")
            for (it in specs) {
                // SpecRef.Function は JVM/Native 双方で動く(リフレクション不要)。
                appendLine("    SpecRef.Function({ $it() }, $it::class),")
            }
            appendLine(")")
        }

        codeGenerator.createNewFile(
            // 出力(テストクラス一覧)は全ソースに依存するため aggregating にする。
            // false( isolating )だと変更なしラウンドで getAllFiles() が空になり、空リストを生成してしまう。
            dependencies = Dependencies(aggregating = true),
            packageName = GENERATED_PACKAGE,
            fileName = GENERATED_FILE,
        ).use { output ->
            output.write(content.toByteArray())
        }
    }

    private fun KSClassDeclaration.isKotestSpec(): Boolean {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<KSClassDeclaration>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val declaration = queue.removeFirst()
            val name = declaration.qualifiedName?.asString()
            if (name != null && visited.add(name)) {
                if (name == SPEC_FQ_NAME) return true
                declaration.superTypes.forEach { typeReference ->
                    runCatching {
                        (typeReference.resolve().declaration as? KSClassDeclaration)?.let { queue.add(it) }
                    }
                }
            }
        }
        return false
    }

    private companion object {
        const val SPEC_FQ_NAME = "io.kotest.core.spec.Spec"
    }
}

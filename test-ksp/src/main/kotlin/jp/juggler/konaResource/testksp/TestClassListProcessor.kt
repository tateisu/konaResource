package jp.juggler.konaResource.testksp

import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

private const val GENERATED_PACKAGE = "jp.juggler.konaResource.test.generated"
private const val GENERATED_FILE = "TestClassList"

/**
 * kotest の Spec サブクラス(テストクラス)を列挙し、`TestClassList` オブジェクトを生成する。
 * 生成結果は CLI(Main.kt / RunTest.kt)から参照して `TestEngineLauncher` で実行する。
 */
class TestClassListProcessor(
    environment: SymbolProcessorEnvironment,
) : SymbolProcessor {
    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger
    private var generated = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()
        generated = true

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
            appendLine("object TestClassList {")
            appendLine("    val all: List<SpecRef> = listOf(")
            specs.forEach {
                // SpecRef.Function は JVM/Native 双方で動く(リフレクション不要)。
                appendLine("        SpecRef.Function({ $it() }, $it::class),")
            }
            appendLine("    )")
            appendLine("}")
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName = GENERATED_PACKAGE,
            fileName = GENERATED_FILE,
        ).use { output ->
            output.write(content.toByteArray())
        }
        return emptyList()
    }

    private fun KSClassDeclaration.isKotestSpec(): Boolean {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<KSClassDeclaration>()
        queue.add(this)
        while (queue.isNotEmpty()) {
            val declaration = queue.removeFirst()
            val name = declaration.qualifiedName?.asString() ?: continue
            if (!visited.add(name)) continue
            if (name == SPEC_FQ_NAME) return true
            declaration.superTypes.forEach { typeReference ->
                runCatching {
                    (typeReference.resolve().declaration as? KSClassDeclaration)?.let { queue.add(it) }
                }
            }
        }
        return false
    }

    private companion object {
        const val SPEC_FQ_NAME = "io.kotest.core.spec.Spec"
    }
}

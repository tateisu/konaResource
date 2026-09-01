package jp.juggler.util

import kotlin.text.iterator

/**
 * usage文字列のインデント
 */
private const val INDENT = "    "

/**
 * オプション１つのメタ情報
 *
 * パーサはスペックをコピーせず同じインスタンスをキーに使うため、
 * 等価性は identity で良い(Rust の `dyn Any` + 値等価は不要)。
 * data class にすると setter ラムダの参照比較が等価に入ってしまい不適切。
 */
class OptionSpec<T>(
    /**
     * Short option name, without the leading dash (e.g. `"v"` for `-v`).
     * null means the option has no short form.
     */
    val shortName: Char? = null,
    /**
     * Long option name, without the leading dashes
     * (e.g. `"verbose"` for `--verbose`).
     * Empty string means the option has no long form.
     */
    val fullName: String = "",
    /**
     * Generic name of the value specified as an argument
     * (e.g. `--outDir {path}`).
     */
    val valueName: String? = null,
    /**
     * オプションが値をどのように消費するか
     */
    val valueMode: ValueMode,
    /**
     * 説明文
     */
    val desc: String,
    /**
     * 指定が必須なら真
     */
    val required: Boolean = false,
    /**
     * 複数回指定できるなら真
     * 例: -P n1=v1 -P n2=v2
     */
    val multiple: Boolean = false,
    /**
     * 値をオブジェクトにセットするラムダ式
     */
    val setter: T.(String?) -> Unit,
) {
    enum class ValueMode {
        // オプションは値を全く消費しない。
        None,

        // オプションは常に値を消費する。
        Required,

        // オプション指定の後に空白を開けず =value を指定した場合はその値を消費する。
        InlineOnly,
    }

    fun appendOptionLine(dst: StringBuilder) {
        with(dst) {
            val names = listOfNotNull(
                shortName?.let { "-$it" },
                fullName.notEmpty()?.let { "--$it" },
            ).joinToString(", ")
            append("$INDENT$names")
            if (!valueName.isNullOrBlank()) {
                when (valueMode) {
                    // フラグ(値なし)は valueName を表示しない
                    ValueMode.None -> Unit
                    ValueMode.Required -> append(" $valueName")
                    ValueMode.InlineOnly -> append("[=$valueName]")
                }
            }
            if (required) append(" (必須)")
            if (multiple) append(" (複数指定可能)")
            append("\n")
            append("$INDENT$INDENT$desc\n")
        }
    }

    fun validateCount(count: Int) {
        when {
            count == 0 && required -> error("オプション --$fullName は必須だが指定されなかった")
            count > 1 && !multiple -> error("オプション --$fullName は複数回指定できない")
        }
    }
}

/**
 * 引数１つのメタ情報
 *
 * [OptionSpec] と同じく identity 等価。
 */
class ArgSpec<T>(
    /**
     * 引数の名前
     */
    val name: String,
    /**
     * 説明文
     */
    val desc: String,
    /**
     * 必須なら真
     */
    val required: Boolean = false,
    /**
     * 複数回指定可能なら真
     */
    val multiple: Boolean = false,
    /**
     * 値をセットするラムダ
     */
    val setter: T.(String) -> Unit,
) {
    /**
     * usage のUsageセクションに引数名を横一列に並べる
     */
    fun appendUsageLine(dst: StringBuilder) {
        with(dst) {
            append(" ")
            append(if (required) "<" else "[")
            append(name)
            if (multiple) append(" …")
            append(if (required) ">" else "]")
        }
    }

    /**
     * usage のUsageセクションに引数毎の説明を書く
     */
    fun appendArgumentsLine(dst: StringBuilder) {
        with(dst) {
            append("$INDENT$name")
            if (required) append(" (必須)")
            if (multiple) append(" (複数指定可能)")
            append("\n")
            append("$INDENT$INDENT$desc\n")
        }
    }

    /**
     * 出現回数の検証
     */
    fun validateCount(count: Int) {
        when {
            count == 0 && required -> error("引数${name}は必須だが指定されなかった")
            count > 1 && !multiple -> error("引数${name}は複数回指定に対応していない")
        }
    }
}

/**
 * トップレベルやサブコマンドのメタ情報
 */
data class CommandSpec<T>(
    /**
     * 説明文
     */
    val desc: String,
    /**
     * オブジェクトを作成するラムダ
     */
    val creator: () -> T,
    /**
     * ArgSpecのリスト
     */
    val argSpecs: List<ArgSpec<T>>,
    /**
     * OptionSpecのリスト
     */
    val optionSpecs: List<OptionSpec<T>>,
)

// 単体テスト用のダミーデータ
val dummyCommandSpec = CommandSpec(
    desc = "dummy",
    creator = {}, // returns Unit
    argSpecs = emptyList(),
    optionSpecs = emptyList(),
)

class CommandSpecInitializer<T> {
    val listArgs = mutableListOf<ArgSpec<T>>()
    val listOptions = mutableListOf<OptionSpec<T>>()
    fun arg(
        desc: String,
        name: String,
        required: Boolean = false,
        multiple: Boolean = false,
        setter: T.(String) -> Unit,
    ) = listArgs.add(
        ArgSpec(
            name = name,
            desc = desc,
            required = required,
            multiple = multiple,
            setter = setter,
        ),
    )

    fun option(
        desc: String,
        fullName: String,
        shortName: Char? = null,
        valueMode: OptionSpec.ValueMode,
        valueName: String? = null,
        required: Boolean = false,
        multiple: Boolean = false,
        setter: T.(String?) -> Unit,
    ) = listOptions.add(
        OptionSpec(
            shortName = shortName,
            fullName = fullName,
            valueName = valueName,
            valueMode = valueMode,
            desc = desc,
            required = required,
            multiple = multiple,
            setter = setter,
        ),
    )

    fun stringOption(
        desc: String,
        fullName: String,
        shortName: Char? = null,
        valueName: String? = null,
        required: Boolean = false,
        multiple: Boolean = false,
        setter: T.(String) -> Unit,
    ) = listOptions.add(
        OptionSpec(
            desc = desc,
            fullName = fullName,
            shortName = shortName,
            valueName = valueName,
            valueMode = OptionSpec.ValueMode.Required,
            required = required,
            multiple = multiple,
            setter = { setter(it!!) },
        ),
    )

    fun incrementalOption(
        desc: String,
        fullName: String,
        shortName: Char? = null,
        valueName: String? = null,
        setter: T.(Int?) -> Unit,
    ) = listOptions.add(
        OptionSpec(
            desc = desc,
            shortName = shortName,
            fullName = fullName,
            valueName = valueName,
            valueMode = OptionSpec.ValueMode.InlineOnly,
            required = false,
            multiple = true,
            setter = { setter(it?.toIntOrNull()) },
        ),
    )

    fun flagOption(
        desc: String,
        fullName: String,
        shortName: Char? = null,
        valueName: String? = null,
        setter: T.(Boolean) -> Unit,
    ) = listOptions.add(
        OptionSpec(
            desc = desc,
            shortName = shortName,
            fullName = fullName,
            valueMode = OptionSpec.ValueMode.None,
            valueName = valueName,
            required = false,
            multiple = false,
            setter = { setter(it?.truthy() ?: true) },
        ),
    )
}

fun <T> buildCommandSpec(
    desc: String,
    creator: () -> T,
    block: CommandSpecInitializer<T>.() -> Unit,
): CommandSpec<T> {
    val initializer = CommandSpecInitializer<T>()
    block(initializer)
    return CommandSpec(
        desc = desc,
        creator = creator,
        argSpecs = initializer.listArgs.toList(),
        optionSpecs = initializer.listOptions.toList(),
    )
}

/**
 * parseArgsに指定する情報
 */
@Suppress("ArrayInDataClass")
data class ArgParserConfig(
    /**
     * 実行可能ファイルの名前。 $0
     */
    val program: String,
    /**
     * 未解析の引数のリスト
     */
    val args: Array<out String>,
    /**
     * トップレベルのメタ情報
     */
    val topSpec: CommandSpec<*>,
    /**
     * サブコマンドのメタ情報
     */
    val subcommands: Map<String, CommandSpec<*>>,
    /**
     * 最初の引数からサブコマンドを読むなら真
     */
    val subcommandByArg: Boolean = false,
//    /**
//     * $0 からサブコマンドを読むなら真
//     */
//    val subcommandByProg: Boolean = false,
//    /**
//     * 環境変数からサブコマンドを読むならその環境変数の名前
//     */
//    val subcommandByEnv: String? = null,
//    /**
//     * subcommandByArg がfalse でプログラム名にもEnvにもサブコマンドがなかった場合、
//     * このプロパティが非nullならサブコマンド探索に使われる
//     */
//    val subcommandDefault: String? = null,
)

// 単体テスト用のダミーデータ
val dummyArgParserConfig = ArgParserConfig(
    program = "dummy",
    args = emptyArray(),
    topSpec = dummyCommandSpec,
    subcommands = emptyMap(),
)

/**
 * parseArgsが返す値
 */
data class ArgParserResult(
    /**
     * parseArgsに指定された ArgParserConfig
     */
    val config: ArgParserConfig,
    /**
     * トップレベルコマンドに対応するオブジェクト
     */
    val top: Any,
    /**
     * 選択されたサブコマンドの名前
     */
    val subcommandName: String? = null,
    /**
     * 選択されたサブコマンドに対応するオブジェクト
     */
    val subcommand: Any? = null,
    /**
     *  選択されたサブコマンドのCommandSpec
     */
    val subcommandSpec: CommandSpec<*>? = null,
    /**
     * 処理中に発生したエラー
     */
    val error: Throwable? = null,
    /**
     * -h / --help に遭遇したら真
     */
    val help: Boolean = false,
) {
    val topSpec = config.topSpec

    /**
     * usage の文言を作成する。
     * errorが非nullならそれも出力する。
     */
    fun formatUsage(error: String? = null): String = buildString {
        fun header(text: String) = append("\n$text:\n")

        // -------------------------
        header("Usage")
        append(INDENT)
//        if (!config.subcommandByEnv.isNullOrBlank()) {
//            append(
//                "${
//                    config.subcommandByEnv
//                }=${
//                    subcommandName ?: "…"
//                } "
//            )
//        }
        append("${config.program} (options…)")
        if (config.subcommandByArg) {
            when (val name = subcommandName) {
                null -> append(" {subcommand}")
                else -> append(" $name")
            }
        }
        val args = topSpec.argSpecs + (subcommandSpec?.argSpecs ?: emptyList())
        for (it in args) {
            it.appendUsageLine(this)
        }
        append("\n")
        // -------------------------
        if (config.subcommands.isNotEmpty()) {
            header("Subcommand")
            for ((k, v) in config.subcommands.entries.sortedBy { it.key }) {
                append(INDENT)
                append(k)
                append("\n$INDENT$INDENT${v.desc}\n")
            }
        }
        // -------------------------
        if (args.isNotEmpty()) {
            header("Arguments")
            for (it in args) {
                it.appendArgumentsLine(this)
            }
        }
        // -------------------------
        header("Options")
        append("$INDENT-h, --help\n")
        append("$INDENT${INDENT}このヘルプを表示\n")
        val options = (topSpec.optionSpecs + (subcommandSpec?.optionSpecs ?: emptyList()))
            .sortedBy { it.fullName }
        for (it in options) {
            it.appendOptionLine(this)
        }
        // -------------------------
        if (!error.isNullOrBlank()) {
            header("Error")
            append("$INDENT$error")
        }
    }
}

// 単体テスト用のダミーデータ
val dummyArgParserResult = ArgParserResult(
    config = dummyArgParserConfig,
    top = Unit,
)

/**
 * parseArgs の内部状態
 */
@Suppress("TooManyFunctions")
internal class ArgParser(
    // [in] 設定
    val config: ArgParserConfig,
) {
    private class OptionRef(
        val target: Any,
        val spec: OptionSpec<*>,
    )

    private class ArgRef(
        val target: Any,
        val spec: ArgSpec<*>,
    )

    // -----------------------------
    // internal state:

    // [internal] オプションの出現回数
    val presentedOptions = mutableMapOf<OptionSpec<*>, Int>()

    // [internal] 引数の出現回数
    val presentedArgs = mutableMapOf<ArgSpec<*>, Int>()

    // [internal] 処理した引数の数
    var argIndex: Int = 0

    // [internal] top/sub のオプションスペックを長い順に並べたもの
    private var optionsRefs: List<OptionRef> = emptyList()

    // [internal] top/sub の引数スペックを長い順に並べたもの
    private var argsRefs: List<ArgRef> = emptyList()

    // -----------------------------
    // output:

    // [out] setter's receiver
    val topObj: Any = config.topSpec.creator() as Any

    // [out] name of selected subcommand
    var subcommandName: String? = null

    // [out] 選択されたサブコマンドのspec
    var subcommandSpec: CommandSpec<*>? = null

    // [out] setter's receiver
    var subcommandObj: Any? = null

    // [out] true if -h or --help was detected.
    var help = false
    // -----------------------------

    private fun updateOptionRefs() {
        optionsRefs = buildList {
            config.topSpec.optionSpecs.forEach {
                add(OptionRef(topObj, it))
            }
            subcommandObj?.let { obj ->
                subcommandSpec?.optionSpecs?.forEach {
                    add(OptionRef(obj, it))
                }
            }
        }.sortedByDescending { it.spec.fullName.length }
        argsRefs = buildList {
            config.topSpec.argSpecs.forEach {
                add(ArgRef(topObj, it))
            }
            subcommandObj?.let { obj ->
                subcommandSpec?.argSpecs?.forEach {
                    add(ArgRef(obj, it))
                }
            }
        }
    }

    /**
     * サブコマンドが未設定なら入力値を元にサブコマンドを決められるか試す
     * @return 状態が変化したら真
     */
    private fun setSubcommand(text: String?): Boolean {
        if (subcommandName != null || text.isNullOrBlank()) return false
        val name = config.subcommands.keys.sortedByDescending { it.length }.find { name ->
            text.contains(name, ignoreCase = true)
        } ?: return false
        // found.
        subcommandName = name
        val spec = config.subcommands[name]!!
        subcommandSpec = spec
        subcommandObj = spec.creator()
        updateOptionRefs()
        return true
    }

    init {
        updateOptionRefs()
    }

    private fun findOptionLong(name: String, errorPrefix: String): OptionRef {
        for (r in optionsRefs) {
            if (name == r.spec.fullName) return r
        }
        error("$errorPrefix$name")
    }

    private fun findOptionShort(c: Char, errorPrefix: String): OptionRef {
        for (r in optionsRefs) {
            if (c == r.spec.shortName) return r
        }
        error("$errorPrefix$c")
    }

    private fun setOptionValue(
        optionName: String,
        r: OptionRef,
        eatInline: () -> String?,
        eatNextArg: () -> String?,
    ) {
        val value = when (r.spec.valueMode) {
            OptionSpec.ValueMode.None -> null
            OptionSpec.ValueMode.InlineOnly -> eatInline()
            OptionSpec.ValueMode.Required -> eatInline()
                ?: eatNextArg()
                ?: error("missing value for $optionName")
        }
        val spec = r.spec

        @Suppress("UNCHECKED_CAST")
        val setter = spec.setter as Any.(String?) -> Unit
        setter.invoke(r.target, value)
        presentedOptions[spec] = 1 + (presentedOptions[spec] ?: 0)
    }

    private fun handleOptionLong(
        // オプションから先頭の -- を除去した文字列
        arg: String,
        // オプション直後の引数を読むラムダ
        eatNextArg: () -> String?,
    ) {
        val pair = arg.split("=", limit = 2)
        val name = pair[0]
        val inline = pair.getOrNull(1)
        if (name == "help") {
            help = true
        } else {
            setOptionValue(
                optionName = "--$name",
                r = findOptionLong(name, "unknown option: --"),
                eatInline = { inline },
                eatNextArg = eatNextArg,
            )
        }
    }

    private fun handleOptionShort(
        // オプションから先頭の - を除去した文字列
        arg: String,
        // オプション直後の引数を読むラムダ
        eatNextArg: () -> String?,
    ) {
        val pair = arg.split("=", limit = 2)
        val name = pair[0]
        val inline = pair.getOrNull(1)
        var inlineConsumed = false
        for (c in name) {
            if (c == 'h') {
                help = true
            } else {
                setOptionValue(
                    optionName = "-$c",
                    r = findOptionShort(c, "unknown option: -"),
                    eatInline = {
                        when {
                            inlineConsumed -> null
                            else -> {
                                inlineConsumed = true
                                inline
                            }
                        }
                    },
                    eatNextArg = eatNextArg,
                )
            }
        }
    }

    private fun handleArg(value: String) {
        // 引数からサブコマンドを読む？
        if (config.subcommandByArg && subcommandName == null) {
            if (setSubcommand(value)) return
            error("$value に該当するsubcommandがない")
        }
        val argRef = argsRefs.getOrNull(argIndex) ?: error("引数が多すぎる")
        val spec = argRef.spec
        if (!spec.multiple) ++argIndex
        @Suppress("UNCHECKED_CAST")
        val setter = spec.setter as Any.(String?) -> Unit
        setter.invoke(argRef.target, value)
        presentedArgs[spec] = 1 + (presentedArgs[spec] ?: 0)
    }

    private fun validatePresented() {
        for (r in argsRefs) {
            r.spec.validateCount(presentedArgs[r.spec] ?: 0)
        }
        for (r in optionsRefs) {
            r.spec.validateCount(presentedOptions[r.spec] ?: 0)
        }
    }

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
    private fun parseImpl() {
//        // 環境変数からサブコマンドを決める？
//        config.subcommandByEnv?.let { name ->
//            readEnv(name)?.let { text ->
//                setSubcommand(text)
//            }
//        }
//        // 起動プログラム名からサブコマンドを決める？
//        if (config.subcommandByProg) setSubcommand(config.program)
//        // まだ決まっていない かつ subcommandByArgが偽なら デフォルトを参照する
//        if (subcommandName == null && !config.subcommandByArg) {
//            config.subcommandDefault?.let { setSubcommand(it) }
//            if (subcommandName == null) error("サブコマンドが指定されていません")
//        }
        var argConsumed = 0
        val eatNextArg: () -> String? = {
            when {
                argConsumed >= config.args.size -> null
                else -> config.args[argConsumed++]
            }
        }
        // eat_next_arg の借用競合を避けるため、ループ側もクロージャ経由で値を読む
        while (true) {
            val arg = eatNextArg() ?: break
            when {
                arg.startsWith("--") -> when {
                    // "--" 以降の引数はオプション解析の対象外
                    arg == "--" -> {
                        while (true) {
                            handleArg(eatNextArg() ?: break)
                        }
                        break
                    }
                    // --longOption の処理
                    else -> handleOptionLong(
                        arg = arg.substring(2),
                        eatNextArg = eatNextArg,
                    )
                }
                // -sss 短い形式のオプションの処理
                // - 単体だとオプション扱いにならないことに注意
                arg.startsWith("-") &&
                    arg.length > 1 -> handleOptionShort(
                    arg = arg.substring(1),
                    eatNextArg = eatNextArg,
                )

                else -> handleArg(arg)
            }
        }
        if (!help) validatePresented()
    }

    /**
     * パースして例外を捕獲してParseResultを返す
     */
    fun parse(): ArgParserResult {
        val error = try {
            parseImpl()
            null
        } catch (ex: Throwable) {
            ex
        }
        return ArgParserResult(
            config = config,
            top = topObj,
            subcommandName = subcommandName,
            subcommand = subcommandObj,
            subcommandSpec = subcommandSpec,
            error = error,
            help = help,
        )
    }
}

/**
 * ArgParserConfigを見てCLI引数パースを行い、結果を返す。
 * この関数は例外を投げない。
 */
fun ArgParserConfig.parse(): ArgParserResult = ArgParser(this).parse()

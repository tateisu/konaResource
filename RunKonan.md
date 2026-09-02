## plugin は run_konan を使えるか？
https://github.com/JetBrains/kotlin/blob/master/kotlin-native/HACKING.md#running-clang-the-same-way-kotlinnative-compiler-does
```
$KOTLIN_NATIVE_HOME/bin/run_konan clang clang linux_arm64 foo.c
$KOTLIN_NATIVE_HOME/bin/run_konan llvm $tool $arguments
```

フォルダがわからないので探してみる
```
$ find ~/.konan |grep run_konan
/home/tateisu/.konan/kotlin-native-prebuilt-linux-x86_64-2.2.21/bin/run_konan
/home/tateisu/.konan/kotlin-native-prebuilt-linux-x86_64-2.4.10/bin/run_konan
/home/tateisu/.konan/kotlin-native-prebuilt-linux-x86_64-2.2.0/bin/run_konan
/home/tateisu/.konan/kotlin-native-prebuilt-linux-x86_64-2.0.21/bin/run_konan
```

- バージョン部分はプロジェクトのKotlinバージョンと同じ値が使えるらしい
- kotlin-native-prebuilt-linux-x86_64-* の linux-x86_64 はターゲットではなく、
  Kotlin/Native コンパイラ自身が動作するホストを表しています。
- つまり1個の kotlin-native-prebuilt-linux-x86_64-2.4.10 が、
  複数ターゲット向けのクロスコンパイラとして動きます。

… plugin のためだけに KonaBuildTarget用のクロスコンパイラを追加でインストールする必要はなかったのでは？

### ターゲット名
`clang` モードの `<target>` は Kotlin/Native の target 名です。
ホストでビルドできるターゲットの一覧は以下のように取得できます。

```bash
$ /home/tateisu/.konan/kotlin-native-prebuilt-linux-x86_64-2.4.10/bin/kotlinc-native -list-targets
linux_x64 (default)
linux_arm32_hfp (deprecated)
linux_arm64
mingw_x64
android_x86
android_x64
android_arm32
android_arm64
```

macosでは以下も使える
```
macos_x64
macos_arm64
ios_arm64
ios_simulator_arm64
```

`run_konan` の公式な説明は、現状ほぼ Kotlin リポジトリの `kotlin-native/HACKING.md` にある短い記述だけです。
独立した詳細リファレンスはありません。`--help` もありません。

最初の引数がモードで、clang と llvm の2種類があります。

### Clang モード

```bash
run_konan clang <tool> <target> [clang arguments...]

run_konan clang clang linux_arm64 -v -c test.c -o test.o
run_konan clang clang linux_arm64 foo.c -c -o foo.o

run_konan clang clang++ linux_arm64 \
    -std=c++17 \
    -c test.cpp \
    -o test.o
```

- clang           モード
- clang / clang++ 実行するツール
- linux_arm64     Kotlin/Native target
- remaining...    clang に渡す引数

Note: clang を2回指定します。最初のがrun_konanのモードで、2個目がclangの$0に該当します。
Kotlin/Native がそのターゲット用の LLVM/Clang、sysroot、toolchain、target-specific flags を
解決してから実行します。実際に実行するコマンドも表示されます。

### LLVM モード

こちらにはターゲット引数がありません。

```bash
run_konan llvm <tool> [arguments...]

run_konan llvm llvm-ar rcs libfoo.a foo.o
run_konan llvm llvm-nm foo.o
run_konan llvm llvm-objdump -d foo.o
run_konan llvm llvm-ar rcs libfoo.a foo.o

```

Kotlin/Native が管理している LLVM の `llvm-ar` を使います。

### 詳細
`run_konan` 自体はクロスコンパイラではなく、環境に合わせてツールを呼び出しているラッパーです。
呼び出す際のコマンドラインも標準出力に表示されます。

```text
run_konan
↓ Kotlin/Native の target 設定を読む
↓ ~/.konan/dependencies/... の clang を選択
↓ target / sysroot / toolchain flags を追加
↓ clang を実行
```

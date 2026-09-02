# 禁止事項
- CI関連の情報を文書に記載しない
- ./gradlew build はリリースビルドを含むが、Kotlin/Nativeのそれはかなり重いのでなるべく避ける。特にtestモジュールはデバッグバイナリを作成してそれを動かすのが良い

# 推奨事項
- 作業が一段落したらCHANGELOG.md を更新する

# 資料
- `README.md` ユーザ向け説明
- `KonaArchiveFormat.md` KonaArchive file format

# OSS.md 編集スタイル
- Runtime Dependency の確認対象はsample2とempty。
  - sample1は現在の実装ではなく公開済みアーティファクトを参照してしまう。
- セクションごとのソフトウェア一覧はアルファベット順(case insensitive)に並べる。
- ソフトウェアごとの箇条書きへ勝手に見出しを追加しない。

# Kotlin/Native ビルドホスト
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KonaBuildHost.kt
- Kotlin/Native の制約なので、ビルド環境も自然とこのホスト種別に制約される

# Kotlin/Native ビルドターゲット
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KonaBuildTarget.kt
- AndroidやiOSにはそれぞれ既定のリソースシステムがあるので、このプロジェクトの需要はなさそう

# JNI ビルドターゲット
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/JniBuildTarget.kt
- JNIのクロスビルド用に jdk/{arch}/ にJDKを置く場合は https://learn.microsoft.com/ja-jp/java/openjdk/download#openjdk-21 から取得する。
- jdk/{arch}/include/jni.h が存在するならそれを使い、
- またはビルドターゲットとビルドホストのアーキが一致するならGradleが使ってるjavaのホームディレクトリの/include/jni.h を存在確認してそれを使う
- どちらもないならそのビルドターゲットはavailableではないとみなす

# ビルドマトリクス(JNI)
- KonaBuildHost と JniBuildTarget の組み合わせ表
- ホスト毎に Github workflow を使って試験してセルを埋めていく
- ホスト毎にクロスコンパイル環境の準備が必要
- 現在のワークツリーのjdkフォルダをどうにか Github workflowにアップロードできないか？
  - アップロード対象は include/ フォルダだけでも良いか？
- :common をビルドして、DLL入のJarファイルをダウンロードする
  - ダウンロード先は `{rootProject}/workflowResult/{hostArch}/common.jar`
  - ダウンロードしたjarに含まれるtargetアーキ用DLLをビルド成功とみなす。動作確認は別の機会
- 成功しなかったものはセルにマークダウン注釈参照を書き、注釈に失敗状況を説明する

| Host \ Target | LinuxX64 | LinuxArm64 | WindowsX64 | WindowsArm64 | MacosX64 | MacosArm64 |
|---------------|----------|------------|------------|--------------|----------|------------|
| LinuxX64      | ✅       | ✅         | ✅         | ❌[^linux-windows-arm64] | ❌[^linux-macos] | ❌[^linux-macos] |
| MacosArm64    | ❌[^macos-linux] | ❌[^macos-linux] | ❌[^macos-windows] | ❌[^macos-windows] | ✅       | ✅         |
| MacosX64      | ❌[^macos-linux] | ❌[^macos-linux] | ❌[^macos-windows] | ❌[^macos-windows] | ✅       | ✅         |
| WindowsX64    | ❌[^windows-linux] | ❌[^windows-linux] | ✅         | ❌[^windows-arm64] | ❌[^windows-macos] | ❌[^windows-macos] |
- ✅ は比較的容易にビルドできる
- 🔨 は特別な設定が必要
- ❌ は現状では対応できていない

[^linux-windows-arm64]: `WindowsArm64`には`aarch64-w64-mingw32-gcc`が必要だが、Ubuntuの標準パッケージにこのMinGW ARM64クロスコンパイラがないため未対応。ただしprebuilt LLVM-MinGWをWorkflowで取得すれば比較的容易に対応できる見込みで、compiler設定とリンクフラグの調整が必要。
[^linux-macos]: macOS targetにはApple SDKを含むosxcross環境が必要だが、Ubuntuの標準パッケージだけでは用意できないため未対応。Apple SDKの準備が必要なので容易ではない。 or use https://github.com/tpoechtrager/osxcross ?
[^macos-linux]: macOSの`cc`はMach-Oを生成するためLinux ELF用には使えない。Linux target用の`aarch64-linux-gnu-gcc`およびLinux x64用のLinux toolchain/sysrootがmacOS runnerにないため未対応。Zig等で対応できる可能性はあるが、Linux ABIとsysrootの検証が必要で容易ではない。
[^macos-windows]: Windows target用の`x86_64-w64-mingw32-gcc`または`aarch64-w64-mingw32-gcc`とWindows MinGW sysrootがmacOS runnerにないため未対応。macOS用のMinGW/LLVM-MinGW環境または別のLinux build environmentが必要で容易ではない。
[^windows-linux]: Windows runnerにはLinux ELF用compiler/sysrootがなく、`LinuxArm64`用の`aarch64-linux-gnu-gcc`も`LinuxX64`用のLinux compilerとして利用できないため未対応。Zig等を導入すれば対応できる可能性はあるが、Linux sysrootとABIの検証が必要。
[^windows-arm64]: Windows runnerに標準搭載されるMinGW/GCCはx64向けで、`aarch64-w64-mingw32-gcc`とWindows ARM64 MinGW sysrootがないため未対応。ただしWindows用prebuilt LLVM-MinGWは全Windows targetをサポートするため比較的容易に対応できる見込みで、現在のGCC固定設定とリンクフラグの調整が必要。
[^windows-macos]: Windows runnerにはApple SDKおよびmacOS cross compilerがないため未対応。Apple SDKの準備が必要なので容易ではない。

# ビルドマトリクス(cli,test,benchmark,sample2)
- KonaBuildHost と KonaBuildTarget の組み合わせ表
- セルには未実施なら❓, ビルド成功したら ✅, 失敗ならマークダウン注釈参照を書く
- まず表をざっくり書き、セルは未実施の絵文字にする。
- その後、ホスト毎に Github workflow を使って試験してセルを埋めていく
- ホスト毎にcli,test,benchmark,sample2(クロスコンパイル)をビルドして、結果をダウンロードする
  - gradleを2回起動することになる。runSample2.sh を参考
  - 実行はしない。ビルドとダウンロードだけだ
- commonJniのビルドも動くが、ホストOSで動けばいいのでJNIクロスコンパイルの準備は不要なはず
- sample2 のバイナリ複数(クロスコンパイル)をビルドして、結果をダウンロードする
  - ダウンロード先の例 `{rootProject}/workflowResult/{hostArch}/{cli|test|sample2}なんたら`
  - なんたらの部分は複数アーキで異なる？ jar もある？
  - ダウンロードできたsample2のターゲットarchごとにセルを成功とみなす
- 成功しなかったものはセルにマークダウン注釈参照を書き、注釈に失敗状況を説明する

TODO ここに表を書く

TODO ここに注釈で失敗状況を書く

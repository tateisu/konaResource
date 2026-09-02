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

| Host\Target | LinuxX64           | LinuxArm64         | WindowsX64         | WindowsArm64             | MacosX64           | MacosArm64         |
|-------------|--------------------|--------------------|--------------------|--------------------------|--------------------|--------------------|
| LinuxX64    | ✅                 | ✅                 | ✅                 | 🔨[^linux-windows-arm64] | ❌[^linux-macos]   | ❌[^linux-macos]   |
| MacosArm64  | ❌[^macos-linux]   | ❌[^macos-linux]   | ❌[^macos-windows] | ❌[^macos-windows]       | ✅                 | ✅                 |
| MacosX64    | ❌[^macos-linux]   | ❌[^macos-linux]   | ❌[^macos-windows] | ❌[^macos-windows]       | ✅                 | ✅                 |
| WindowsX64  | ❌[^windows-linux] | ❌[^windows-linux] | ✅                 | 🔨[^windows-arm64]       | ❌[^windows-macos] | ❌[^windows-macos] |
- ✅ は比較的容易にビルドできる
- 🔨 は特別な設定が必要
- ❌ は現状では対応できていない

[^linux-windows-arm64]: Ubuntu標準の`gcc-mingw-w64-x86-64`ではWindows ARM64を生成できないため、LLVM-MinGWとWindows ARM64 JDKのheaderを追加してビルドする。以下をLinux x64環境で実行する。
    ```shell
    mkdir -p /tmp/llvm-mingw
    curl -L https://github.com/mstorsjo/llvm-mingw/releases/download/20260826/llvm-mingw-20260826-ucrt-ubuntu-22.04-x86_64.tar.xz | tar -xJ -C /tmp/llvm-mingw
    export PATH="/tmp/llvm-mingw/llvm-mingw-20260826-ucrt-ubuntu-22.04-x86_64/bin:$PATH"
    mkdir -p /tmp/microsoft-jdk-windows-aarch64 jdk/WindowsArm64
    curl -L https://aka.ms/download-jdk/microsoft-jdk-21.0.12.1-windows-aarch64.zip -o /tmp/microsoft-jdk-windows-aarch64.zip
    unzip -q /tmp/microsoft-jdk-windows-aarch64.zip -d /tmp/microsoft-jdk-windows-aarch64
    cp -R /tmp/microsoft-jdk-windows-aarch64/*/include jdk/WindowsArm64/
    ./gradlew --no-daemon --max-workers=1 -PLinuxX64_WindowsArm64_compiler=aarch64-w64-mingw32-clang -PLinuxX64_WindowsArm64_linkOpt=-shared :common:jvmJar
    ```
    成功すると`common/build/libs/common-jvm-*.jar`に`jp/juggler/konaArchive/native/windows-aarch64/kona_common_jni.dll`が含まれる。
[^linux-macos]: macOS targetにはApple SDKを含むosxcross環境が必要だが、Ubuntuの標準パッケージだけでは用意できないため未対応。Apple SDKの準備が必要なので容易ではない。 or use https://github.com/tpoechtrager/osxcross ?
[^macos-linux]: macOSの`cc`はMach-Oを生成するためLinux ELF用には使えない。Linux target用の`aarch64-linux-gnu-gcc`およびLinux x64用のLinux toolchain/sysrootがmacOS runnerにないため未対応。Zig等で対応できる可能性はあるが、Linux ABIとsysrootの検証が必要で容易ではない。
[^macos-windows]: Windows target用の`x86_64-w64-mingw32-gcc`または`aarch64-w64-mingw32-gcc`とWindows MinGW sysrootがmacOS runnerにないため未対応。macOS用のMinGW/LLVM-MinGW環境または別のLinux build environmentが必要で容易ではない。
[^windows-linux]: Windows runnerにはLinux ELF用compiler/sysrootがなく、`LinuxArm64`用の`aarch64-linux-gnu-gcc`も`LinuxX64`用のLinux compilerとして利用できないため未対応。Zig等を導入すれば対応できる可能性はあるが、Linux sysrootとABIの検証が必要。
[^windows-arm64]: Windows標準のMinGW/GCCはx64向けのため、LLVM-MinGWの`20260826-ucrt-x86_64`を展開してPATHへ追加し、Windows ARM64 JDKの`include/`を`jdk/WindowsArm64/include/`へ配置する。Windows x64環境で以下を実行する。
    ```shell
    mkdir -p /tmp/llvm-mingw
    curl -L https://github.com/mstorsjo/llvm-mingw/releases/download/20260826/llvm-mingw-20260826-ucrt-x86_64.zip -o /tmp/llvm-mingw.zip
    7z x -y /tmp/llvm-mingw.zip -o/tmp/llvm-mingw
    export PATH="/tmp/llvm-mingw/llvm-mingw-20260826-ucrt-x86_64/bin:$PATH"
    mkdir -p /tmp/microsoft-jdk-windows-aarch64 jdk/WindowsArm64
    curl -L https://aka.ms/download-jdk/microsoft-jdk-21.0.12.1-windows-aarch64.zip -o /tmp/microsoft-jdk-windows-aarch64.zip
    7z x -y /tmp/microsoft-jdk-windows-aarch64.zip -o/tmp/microsoft-jdk-windows-aarch64
    cp -R /tmp/microsoft-jdk-windows-aarch64/*/include jdk/WindowsArm64/
    ./gradlew --no-daemon --max-workers=1 -PWindowsX64_WindowsArm64_compiler=aarch64-w64-mingw32-clang -PWindowsX64_WindowsArm64_linkOpt=-shared :common:jvmJar
    ```
    成功すると`common/build/libs/common-jvm-*.jar`に`jp/juggler/konaArchive/native/windows-aarch64/kona_common_jni.dll`が含まれる。
[^windows-macos]: Windows runnerにはApple SDKおよびmacOS cross compilerがないため未対応。Apple SDKの準備が必要なので容易ではない。

# 複数ホストでビルドしたDLLの収集
commonJni のビルドスクリプトは workflowResult からプラットフォーム別に生成されたcommon.jarから不足プラットフォームのDLLを収集する機能がある。GitHub Workflow とこれを組み合わせると全プラットフォーム対応の common artifact を生成できる。

- common-jvm-jar-all.ymlをworkflow_call対応。
- publish.ymlで全hostのcommon.jarを先にビルド。
- 集約artifactをworkflowResult/へ展開してから公開。
- 公開前に5種類のJNIリソースを検証。
- 不足時は公開を停止し、不完全なMaven artifactを出さない構成。

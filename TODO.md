# TODO
ひとつずつ順に実行して、終わったら `[x]` をつける

# KonaBuildTarget 利用の拡大
- [x] 可能な箇所全てで fun KotlinMultiplatformExtension.konaTargets() を使う
  - needs `import jp.juggler.konaResource.buildlogic.konaTargets`
  - use `kotlin { konaTargets() ...` instead of many Kotlin/Native targets.

# testモジュールのCLI
- [x] クロスプラットフォーム理由でtest cli を独立させた
  - [x] jvmテスト用のFatJar
  - [x] Kotlin/Native 各アーキの実行バイナリ
  - [x] テスト絞り込みフィルタ
  - [x] テスト実行のロギング
  - [ ] ビルド高速化
    - [ ] sample1 は公開済みプラグイン(0.1.4)が config cache 非互換のため一時的に settings.gradle.kts から除外。version を 0.1.5 に更新済み。
    - [ ] ユーザが v0.1.5 タグを push → CI が Central 公開 → 公開後に settings.gradle.kts の sample1 を再有効化。






# blakeJni DLLのクロスプラットフォームビルド
以下の環境用のDLLを全部生成する
Linux x64 glibc
Linux x64 musl # skip due to https://youtrack.jetbrains.com/issue/KT-38891
Linux arm64 glibc
macOS universal2
Windows x64
Windows arm64

# benchmarkの単体バイナリ出力
- クロスプラットフォーム理由で単体バイナリを出力して、ビルドホスト以外の環境で動くようにしたい
- test モジュールで既にやったことだが、こちらはデバッグビルドではなくリリースビルドを使う
- ベンチマーク機構は再発明することになる
  - 競合製品のソースコードを参考にすること 
  - kotlinx-benchmarkはGradleに重依存しているので使えない。
- deploy タスクで konaBenchmark なんたらをルートプロジェクトにコピーしてchmod する
- それらのバイナリはGitHub workflow で使うのでリポジトリに追加する
  - benchmark:deploy を実行したときだけ更新する

## test, benchmark をクロスプラットフォーム実行するGithub Action
  - アーキ別に書いてそれぞれ問題がないことを確認し、最後に単一ワークフローにまとめる


## Github Actions に環境別テストマトリクスを書く 
- 各ABI個別のworkflowファイルを作成（後で合成可能）
- Linux x64 musl のスコア: `3861.073 ± 115.546 ops/s`
- Kotlin/Native は glibc 前提のため musl では動作せず。JVMテスト/benchmarkのみ実行

対象環境:

| 優先度 | OS      | CPU    | ABI / libc | 成果物例       | Blake3Jni benchmark          |
|--------|---------|--------|------------|----------------|------------------------------|
| 必須   | Linux   | x86_64 | glibc      | `libfoo.so`    | `4877.359 ± 122.289 ops/s`   |
| 追加   | Linux   | x86_64 | musl       | `libfoo.so`    | `3861.073 ± 115.546 ops/s`   |
| 必須   | Linux   | arm64  | glibc      | `libfoo.so`    | 未計測                       |
| 必須   | macOS   | x86_64 | Darwin     | `libfoo.dylib` | 未計測                       |
| 必須   | macOS   | arm64  | Darwin     | `libfoo.dylib` | 未計測                       |
| 必須   | Windows | x86_64 | MSVC       | `foo.dll`      | 未計測                       |
| 追加   | Windows | arm64  | MSVC       | `foo.dll`      | 未計測                       |

次は Linux    arm64   glibc だ。
- github/workflows/blake3-jni-abi-test.yml を参考にする。これはLinux x86_64 glibc 用だ
- プラットフォームごとにワークフローymlを書いて、個別に動作検証
- git commit / push を許可する
- gh 操作でのワークフロー開始を許可する
- ワークフローが正常終了したらこの文書の表に Blake3Jni benchmark スコアを記載する

# 資料

自作した Gradle pluginを複数のABIに対応させたいが、試験環境はない。
リモート環境を貸してくれるサービスはあるか？

ある。いまなら GitHub Actions が一番手軽で、挙げた種類をほぼ1サービスで試せます。

| 対象                | GitHub Actions | 方法                                         |
| ----------------- | -------------- | ------------------------------------------ |
| Windows arm64     | ◎ ネイティブ        | `windows-11-arm` / `windows-11-vs2026-arm` |
| Linux arm64 glibc | ◎ ネイティブ        | `ubuntu-24.04-arm`                         |
| Linux x64 musl    | ○              | x64 Ubuntu runner上で Alpine コンテナ            |
| macOS universal2  | ◎              | Apple Silicon + Intel の2台でそれぞれ検証           |

GitHub は Windows ARM64 の標準ホステッドランナーを提供しており、
2026年8月には VS2026 ARM64 イメージも GA になっています。
Linux ARM64 も標準ランナーがあります。([GitHub Docs][1])

### macOS universal2について

「universal2」というCPU環境が存在するわけではないので、

```text
macos-15        → arm64 (Apple Silicon)
macos-15-intel  → x86_64 (Intel)
```

の両方で同じ universal binary を実行してテストするのが確実です。
GitHub Actions は現在この両方をホストしています。([GitHub Docs][2])

### musl

ここだけ専用マシンは不要です。

```yaml
runs-on: ubuntu-latest
container:
  image: alpine:3.22
```

のように Alpine コンテナ内で Gradle/test を実行すれば、`x86_64 + musl` 環境になります。
GitHub Actions はジョブ全体をコンテナ内で動かす機能を公式にサポートしています。([GitHub Docs][3])

例えばテストマトリクスは概念的には、

```yaml
strategy:
  matrix:
    include:
      - name: windows-arm64
        os: windows-11-arm

      - name: linux-arm64-glibc
        os: ubuntu-24.04-arm

      - name: macos-arm64
        os: macos-15

      - name: macos-x64
        os: macos-15-intel
```

に加えて Alpine のジョブを1つ、という構成になります。

### 費用

public repository なら標準 GitHub-hosted runner は原則無料・無制限です。
仮に各ABIを10分ずつ回しても、無料枠外で約 $0.83/一式 です。大半がmacOS料金です。
なので、実機をレンタルするより「GitHub ActionsにABIテスト用matrixを置く」のがまず最適だと思います。
特に Gradle plugin が JNI/JNA/native executable を同梱するタイプなら、
実際に各runner上で `./gradlew check benchmark:jvmBenchmark` を走らせれば
クロスコンパイルだけでは拾えないロード時の問題まで検出できます。
Gradle 自身も GitHub Actions での matrix build を公式に案内しています。([Gradle][4])

[1]: https://docs.github.com/en/actions/reference/runners/github-hosted-runners?utm_source=chatgpt.com "GitHub-hosted runners reference - GitHub Docs"
[2]: https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job?utm_source=chatgpt.com "Choosing the runner for a job - GitHub Docs"
[3]: https://docs.github.com/ja/actions/how-tos/write-workflows/choose-where-workflows-run/run-jobs-in-a-container?utm_source=chatgpt.com "コンテナ内でのジョブの実行 - GitHubドキュメント"
[4]: https://docs.gradle.org/current/userguide/github-actions.html?utm_source=chatgpt.com "Gradle on GitHub Actions"


## 上記テストで失敗を見つけたら、 JNIビルドのABI追加を行う


結論として、**「Gradle が公式に動作確認している主要環境をほぼ全部」なら 7 ABI を用意**するのが分かりやすいです。現行 Gradle 9.7 は Windows / Linux / macOS の AMD64・AArch64 を中心に、Alpine Linux も公式対象にしています。([Gradle][1])

したがって、まず **5ターゲット**、

`windows-x86_64`, `linux-x86_64-glibc`, `linux-arm64-glibc`, `macos-x86_64`, `macos-arm64`

を揃えればかなり広くカバーできます。そこへ **Windows ARM64 と Linux musl x86_64** を足して7ターゲットにすると、現在の Gradle 公式テスト対象とかなりきれいに一致します。Alpine は glibc ではなく musl なので、Linux x86_64 を1本にまとめない方が安全です。([Gradle][1])

### ビルド側で揃えるもの

JNI 自体について必要なのは基本的に、

* `${JAVA_HOME}/include`
* `${JAVA_HOME}/include/linux` / `darwin` / `win32`
* shared library としてリンク
* JVM と同じ CPU architecture

です。JNI は同一プラットフォーム上で JVM 実装をまたぐバイナリ互換性を目的に設計されています。Gradle 9.7 の実行 JVM は Java 17〜26 なので、特別な理由がなければ **JDK 17 の `jni.h` をビルド基準にする**のが扱いやすいです。([Oracle Docs][2])

ビルドシステムは Gradle の native plugin に強く依存するより、

```text
Gradle
  ↓
CMake
  ↓
clang / gcc / MSVC
```

くらいに分離するのを勧めます。CMake 側で JNI shared library を作り、Gradle は成果物の収集・JARへの梱包だけ担当させる形です。

### Linux が一番注意点が多い

CPU より **libc ABI** が問題になります。

```text
Linux x86_64
 ├── glibc → Ubuntu / CentOS / Debian 等
 └── musl  → Alpine
```

なので、

```text
linux-x86_64-glibc
linux-x86_64-musl
```

は別物として扱います。

glibc 用 `.so` は、できるだけ古い glibc 環境でビルドしておく方が新しいディストリビューションでも動きやすくなります。C++なら `libstdc++` のバージョン依存も発生するので、JNI の薄い部分だけ C にする、または C++ runtime の依存を慎重に管理すると事故が減ります。

Windows も C++ を使うなら MSVC runtime 依存を考える必要があります。JNI shim が小さいなら `/MT` などで runtime を静的リンクする設計も候補です。

### macOS は1本にまとめられる

macOS は Universal Binary にすれば、

```text
x86_64 + arm64
```

を1つの `.dylib` にできます。

CMake なら概念的には、

```cmake
set(CMAKE_OSX_ARCHITECTURES "x86_64;arm64")
```

です。

したがって配布ファイル数だけを見ると、

```text
Windows x64
Windows arm64
Linux x64 glibc
Linux arm64 glibc
Linux x64 musl
macOS universal2
```

の **6ファイル**まで減らせます。

### JARへの入れ方

例えば、

```text
META-INF/native/
  windows-x86_64/foo.dll
  windows-arm64/foo.dll
  linux-x86_64-glibc/libfoo.so
  linux-x86_64-musl/libfoo.so
  linux-arm64-glibc/libfoo.so
  macos-universal/libfoo.dylib
```

のようにしておきます。

実行時に

```text
os.name
os.arch
libc
```

を判定し、該当ファイルを一時ディレクトリへ展開して `System.load(absolutePath)` する構成が扱いやすいです。JNI の標準的なロード名は OS ごとに異なり、例えば Linux は `libfoo.so`、Windows は `foo.dll` になります。([Oracle Docs][3])

なお `os.arch` は文字列揺れがあるので、

```text
amd64 / x86_64 / x64 → x86_64
aarch64 / arm64      → arm64
```

のように正規化した方がよいです。Gradle 自身も x86_64 と AArch64 を主要 architecture として扱っています。([Gradle][4])

もう一点、**JDK 24+ では JNI のロードが native-access 制限の対象**になっています。現在は未許可なら警告が基本ですが、将来 `deny` がデフォルトになる予定なので、Gradle plugin としては `--enable-native-access=ALL-UNNAMED` 等が必要になる可能性を設計上考慮しておいた方がいいです。([Oracle Docs][5])

つまり実装コストとのバランスなら、**最初は5ターゲット、きっちりやるなら7 ABI、macOS Universal2 化で配布物は6本**、という構成が妥当です。

[1]: https://docs.gradle.org/9.7.0/userguide/compatibility.html?utm_source=chatgpt.com "Compatibility Matrix"
[2]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/intro.html?utm_source=chatgpt.com "Java Native Interface Specification: 1 - Introduction"
[3]: https://docs.oracle.com/en/java/javase/25/docs/specs/jni/design.html?utm_source=chatgpt.com "Java Native Interface Specification: 2 - Design Overview"
[4]: https://docs.gradle.org/current/javadoc/org/gradle/platform/Architecture.html?utm_source=chatgpt.com "Architecture (Gradle API 9.6.1)"
[5]: https://docs.oracle.com/en/java/javase/25/migrate/migrating-jdk-8-later-jdk-releases.html?utm_source=chatgpt.com "Migrating from JDK 8 to Later JDK Releases"

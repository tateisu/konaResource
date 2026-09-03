# TODO
ひとつずつ順に実行して、終わったら `[x]` をつける

## クロスコンパイラから run_konan への移行
進行状況: 5/5

状態:
- `[ ]` 未着手
- `[~]` 進行中
- `[x]` 完了

- [x] 1. build-logic の enum を整理
  - `JniBuildTarget.WindowsX64` を `MingwX64` に改名する
  - `JniBuildTarget.WindowsArm64` を削除する
  - `KonaBuildTarget` は変更しない
  - `JniBuildTarget` に Kotlin/Native target 名を対応付ける

- [x] 2. 利用可能ターゲット判定を Kotlin/Native に合わせる
  - `kotlinc-native -list-targets` の結果を取得する
  - `availableKonaBuildTarget()` に結果を反映する
  - `availableJniBuildTargets()` に結果を反映する
  - JNI側は target の存在に加えて `jni.h` も確認する
  - 取得結果は再利用できる形でキャッシュする

- [x] 3. build-logic に run-konan ショートハンドを追加
  - `~/.konan` 以下から `run_konan` を探索する
  - Kotlin version と distribution の version を照合する
  - `run_konan` のパスを lazy 変数にキャッシュする
  - `run_konan clang clang <target>` のコマンド配列を生成するAPIを追加する
  - `kotlinc-native -list-targets` も同じ distribution から実行する

- [x] 4. plugin モジュールを run-konan 化
  - 手動の compiler 選択と `--target` 指定を削除する
  - Kotlin/Native target ごとに run-konan を使用する
  - plugin側から build-logic のヘルパーを利用できるよう共有方法を整理する

- [x] 5. JNIモジュールを run-konan 化
  - `CommonJniBuildTask` を複数引数の compiler command 対応に変更する
  - GCC/MinGW の PATH 検査を削除する
  - Linux、Mingw、macOS の JNIビルドを run-konan に移行する
  - macOS universal2 はターゲット別コマンドでビルドして lipo 結合する
  - Windows ARM64 用の登録・収集・設定を削除する

### 検証方針
- [x] 各項目完了時に該当する状態を `[x]` に更新する
- [x] `./gradlew build` は実行しない
- [x] build-logic、plugin、commonJni、common、cli の対象タスクだけを検証する
- [x] 最後に `CHANGELOG.md` を更新する

## 細かい修正
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KotlinNativeToolchain.kt のいくつかの例外的な状態は例外を投げるべき

## testの改善
- testで日本語ファイル名/ディレクトリ名も確認したい。
  - CreateDirectoryAってANSIコードベージ依存じゃねーの？
  - ホストのコードページを仮定してるのはおかしい。
  - 大半のWindows PCのコードページはUTF-8ではない。

## benchmark CLI の 改善
- benchmark CLI の 定数をArgParserで指定可能にする
- デフォルト値も直すsmoke用になってて、warmupが少ないし試験時間も短すぎる

## AGENTS.md の ビルドマトリクスを作成する

## JniBuildTarget別に生成されたFatJarを試す
- ローカルで生成した common artifact は全アーキのDLLを含むはず
- test(jvm), cli(pack,extract), benchmark

## KonaBuildTarget別に生成された実行バイナリを試す
- empty, sample2, test, benchmark

## emptyとsample2のDLL依存差異をhost-targetペア毎に比較する

## JVMターゲットへのリソース埋め込み

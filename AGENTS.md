# 禁止事項
- CI関連の情報を文書に記載しない
- ./gradlew build はリリースビルドを含むが、Kotlin/Nativeのそれはかなり重いのでなるべく避ける。特にtestモジュールはデバッグバイナリを作成してそれを動かすのが良い
- GitHub Workflow を試す場合、全ホストオーケストレーションするのはやめて。ホスト毎に検証して、最後にオーケストレーションにして。

# 推奨事項
- 作業が一段落したらCHANGELOG.md を更新する

# 資料
- `README.md` ユーザ向け説明
- `KonaArchiveFormat.md` KonaArchive file format
- `BuildMatrixJni.md` Build Matrix for `commonJni` module

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

# ビルドマトリクス(test,benchmark,sample2)
- KonaBuildHost と KonaBuildTarget の組み合わせ表
- セルには未実施なら❓, ビルド成功したら ✅, 失敗ならマークダウン注釈参照を書く
- まず表をざっくり書き、セルは未実施の絵文字にする。
- その後、ホスト毎に Github workflow を使って試験してセルを埋めていく
- ホスト毎に test,benchmark,sample2(クロスコンパイル)をビルドして、結果をダウンロードする
  - gradleを2回起動することになる。runSample2.sh を参考
  - 実行はしない。ビルドとダウンロードだけだ
  - jar は収集対象外。jar中のJNIは別マトリクスの話だ
- commonJniのビルドも動くが、ホストOSで動けばいいのでJNIクロスコンパイルの準備は不要なはず
- sample2 のバイナリ複数(クロスコンパイル)をビルドして、結果をダウンロードする
  - ダウンロード先の例 `{rootProject}/workflowResult/{hostArch}/{cli|test|sample2}なんたら`
  - なんたらの部分は複数アーキで異なる
  - ダウンロードできたsample2のターゲットarchごとにセルを成功とみなす
- 成功しなかったものはセルにマークダウン注釈参照を書き、注釈に失敗状況を説明する

| Host\Target | LinuxX64 | LinuxArm64 | MingwX64 | MacosArm64      |
|-------------|----------|------------|----------|-----------------|
| LinuxX64    | ✅       | ✅         | ✅       | ❌[^need-macos] |
| MacosArm64  | ❓       | ❓         | ❓       | ❓              |
| MacosX64    | ❓       | ❓         | ❓       | ❓              |
| WindowsX64  | ❓       | ❓         | ❓       | ❌[^need-macos] |

[^need-macos]: Kotlin/Nativeの公式サポートでは、Linux/WindowsホストからAppleターゲットの最終バイナリをビルドできない。本プロジェクトの`common`はC interop依存でもある。非公式な`osxcross`環境で動く可能性はあるが、本プロジェクトでは未対応。

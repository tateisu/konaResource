
# プロジェクトの目標
 Kotlin/Native の Linux/x64 の .kexe ファイルにリソースを埋め込む Gradle Plugin と
 アプリからそれを使うライブラリの2つを提供する

# 技術スタック
- Compatibility guide for Kotlin Multiplatform https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html#version-compatibility の新しい方の構成
- Gradle バージョンカタログ
- okio
- Kotest
- lz4 cinterop or lz4-java

# プロジェクト構成
## ルートプロジェクト
- ルートプロジェクトは何もしない。ビルドターゲットもない

## common モジュール
- リソースアーカイブの共通コードと、埋め込まれたリソースを読み出すライブラリ

## plugin モジュール
- Kotlin/Native の Linux/x64 の .kexe ファイルにリソースを埋め込む Gradle Plugin

## sample1 モジュール
- 上記 plugin モジュール, common モジュールを使ってリソースにアクセスするアプリ

## cli モジュール
- リソースアーカイブを操作するCLIアプリ
- JVM


# 困ったこと・制約

- `sample1` はデフォルトで plugin と common に兄弟モジュールを使用する。Maven Central の公開 artifact を使う場合は `-PuseLocalArtifacts=false` を指定する。

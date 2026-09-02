# TODO
ひとつずつ順に実行して、終わったら `[x]` をつける

## plugin と common のマルチプラットフォーム化
- [x] どのようにアーカイブを埋め込むのか（README.mdに仕組みを記載）
- common/src/nativeMain/kotlin/jp/juggler/konaResource/EmbedKonaArchive.kt  でどのようにバイナリを読むのか
- build-logic/src/main/kotlin/jp/juggler/konaResource/buildlogic/KonaBuildTarget.kt で列挙したアーキが対象
- MacOS の Universal2 は Kotlin Nativeではどう扱われるのか？

## KonaBuildHost enum の導入
- build-logic に KonaBuildHost enum と getKonaBuildHost(): を導入する
- getKonaBuildHost() は現在のホストから KonaBuildHost enum を探し、マッチしないならerror("...") を投げる
- エラーメッセージはホストの現在のアーキがわかるようなものにする
- 各モジュールや他のbuildLogicで使っている判定処理を整理して KonaBuildHost内部にまとめる

資料:Kotlin/Nativeコンパイラの動作環境。※ Linux ARM64 や Windows ARM64 はありません
- macOS	ARM64 / AArch64
- macOS	x64
- Linux	x64
- Windows x64

## AGENTS.md の ビルドマトリクス(JNI)を作成する

## test, benchmark, cli をクロスプラットフォーム試験する
## Github Actions で 
- 現在ビルド済みで bin/フォルダにあるものが対象
  - MacOSはまだbin/フォルダにないので対応できない
- 動かしたら Benchmark.md を更新する
- 最後に lz4-java と lz4 native の速度を比較する


## JVMターゲットへのリソース埋め込み

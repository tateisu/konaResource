
# 禁止事項
- CI関連の情報を文書に記載しない
- ./gradlew build はリリースビルドを含むが、Kotlin/Nativeのそれはかなり重いのでなるべく避ける。特にtestモジュールはデバッグバイナリを作成してそれを動かすのが良い

# 資料
- `README.md` ユーザ向け説明
- `KonaArchiveFormat.md` KonaArchive file format

# OSS.md 編集スタイル
- Runtime Dependency の確認対象はsample2とempty。
  - sample1は現在の実装ではなく公開済みアーティファクトを参照してしまう。
- セクションごとのソフトウェア一覧はアルファベット順(case insensitive)に並べる。
- ソフトウェアごとの箇条書きへ勝手に見出しを追加しない。

# ビルドホスト
- Kotlin/Nativeが動作する以下の環境でビルドが行えるようにしたい
- ただしGradleプロパティに -Pmacos=true を指定した場合以外は macos用の処理をスキップする

```
Linux x86_64
macOS ARM64
macOS x86_64
Windows x86_64
```

# ビルドターゲット
- ただしGradleプロパティに -Pmacos=true を指定した場合以外は macos用の処理をスキップする

```
jvm => fatJar( multiplatform JNI)
linux X64 glibc   => kexe?
linux Arm64 glibc => kexe?
windows X64   => exe?
windows Arm64 => exe?
macOS ARM64
macOS x86_64
```

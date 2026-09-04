# ビルドマトリクス(test,benchmark,sample2,empty)
- KonaBuildHost と KonaBuildTarget の組み合わせ表
- empty・test・benchmark・sample2を各ホストの登録済みNativeターゲットで一括ビルド検証する。
- 成功Artifactを収集し、ホスト毎にマトリクス表を確定する。
- セルには未実施なら❓, ビルド成功したら ✅, 失敗ならマークダウン注釈参照を書く
- まず表をざっくり書き、セルは未実施の絵文字にする。
- その後、ホスト毎に Github workflow を使って試験してセルを埋めていく
- ホスト毎に test,benchmark,sample2,empty(クロスコンパイル)をビルドして、結果をダウンロードする
    - gradleを2回起動することになる。runSample2.sh を参考
    - 実行はしない。ビルドとダウンロードだけだ
    - jar は収集対象外。jar中のJNIは別マトリクスの話だ
- commonJniのビルドも動くが、ホストOSで動けばいいのでJNIクロスコンパイルの準備は不要なはず
- sample2 のバイナリ複数(クロスコンパイル)をビルドして、結果をダウンロードする
    - ダウンロード先の例 `{rootProject}/workflowResult/{hostArch}/{test|benchmark|sample2|empty}なんたら`
    - なんたらの部分はtargetアーキで異なる
    - ダウンロードできたsample2のターゲットarchごとにセルを成功とみなす
- 成功しなかったものはセルにマークダウン注釈参照を書き、注釈に失敗状況を説明する

| Host\Target | LinuxX64 | LinuxArm64 | MingwX64 | MacosArm64       |
|-------------|----------|------------|----------|------------------|
| LinuxX64    | ✅       | ✅         | ✅       | ❌[^macos-cross] |
| MingwX64    | ✅       | ✅         | ✅       | ❌[^macos-cross] |
| MacosArm64  | ✅       | ✅         | ✅       | ✅               |
| MacosX64    | ✅       | ✅         | ✅       | ✅               |

[^macos-cross]: Kotlin/Native の公式サポートでは、Linux / WindowsホストからAppleターゲットの最終バイナリをビルドできない。
非公式な`osxcross`環境で動く可能性はあるが、本プロジェクトでは未対応。
また本プロジェクトの`common`はC interopを含み、
`pluin`ではアセンブリを書いてツールチェインでオブジェクトファイルを作成する点にも注意が必要だ。

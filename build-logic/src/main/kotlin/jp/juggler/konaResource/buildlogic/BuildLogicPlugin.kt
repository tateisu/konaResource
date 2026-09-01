package jp.juggler.konaResource.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * それ自体は何も設定しない。
 * 適用したモジュールのビルドスクリプトでは、同じ jar に同梱される
 * BuildLogicUtils のユーティリティ関数を import して利用できるようになる。
 */
class BuildLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // 何もしない
    }
}

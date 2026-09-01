package jp.juggler.konaResource.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle Plugin の実装クラス。
 * - これ自体は何も変更しない
 * - 適用したモジュールのビルドスクリプトでは、同じ jar に同梱される
 * BuildLogicUtils のユーティリティ関数を import して利用できるようになる。
 */
@Suppress("unused")
class BuildLogicPlugin : Plugin<Project> {
    override fun apply(target: Project) = Unit
}

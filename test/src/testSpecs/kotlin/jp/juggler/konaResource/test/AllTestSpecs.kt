package jp.juggler.konaResource.test

import io.kotest.core.spec.SpecRef

internal actual fun allTestSpecs(): List<SpecRef> =
    jp.juggler.konaResource.test.generated.kotestSpecs

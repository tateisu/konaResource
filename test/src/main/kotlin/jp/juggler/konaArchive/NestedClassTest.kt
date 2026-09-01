package jp.juggler.konaArchive

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class NestedClassTest : FreeSpec() {
    init {
        "a" - {
            println("this line said by a")
            1 + 1 shouldBe 2
            "b" - {
                println("this line said by b")
                1 + 1 shouldBe 2
                "c" - {
                    println("this line said by c")
                    1 + 1 shouldBe 2
                    "d" {
                        println("this line said by b")
                        1 + 1 shouldBe 2
                    }
                }
            }
            "foo" - {
                println("this line said by foo")
                1 + 1 shouldBe 2
            }
        }
        "bar" {
            println("this line said by bar")
            1 + 1 shouldBe 2
        }
    }
}

package kr.jclab.restic.chunker

import org.junit.jupiter.api.Test

class PolBenchmarkTest {
    @Test
    fun benchmarkPolDivMod() {
        val f = Pol(0x2482734cacca49UL)
        val g = Pol(0x3af4b284899UL)
        repeat(1_000_000) {
            g.divMod(f)
        }
    }

    @Test
    fun benchmarkPolDiv() {
        val f = Pol(0x2482734cacca49UL)
        val g = Pol(0x3af4b284899UL)
        repeat(1_000_000) {
            g.div(f)
        }
    }

    @Test
    fun benchmarkPolMod() {
        val f = Pol(0x2482734cacca49UL)
        val g = Pol(0x3af4b284899UL)
        repeat(1_000_000) {
            g.mod(f)
        }
    }

    @Test
    fun benchmarkPolDeg() {
        val f = Pol(0x3af4b284899UL)
        var sum = 0
        repeat(1_000_000) {
            sum += f.deg()
        }
        println("sum of Deg: $sum")
    }

    @Test
    fun benchmarkRandomPolynomial() {
        repeat(100) {
            Pol.randomPolynomial()
        }
    }
}

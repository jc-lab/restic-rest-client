package kr.jclab.restic.chunker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolTest {
    private val polAddTests = listOf(
        Triple(Pol(23UL), Pol(16UL), Pol(23UL xor 16UL)),
        Triple(Pol(0x9a7e30d1e855e0a0UL), Pol(0x670102a1f4bcd414UL), Pol(0xfd7f32701ce934b4UL)),
        Triple(Pol(0x9a7e30d1e855e0a0UL), Pol(0x9a7e30d1e855e0a0UL), Pol(0UL))
    )

    @Test
    fun testPolAdd() {
        polAddTests.forEachIndexed { i, (x, y, sum) ->
            assertEquals(sum, x.add(y), "test $i failed: sum != x+y")
            assertEquals(sum, y.add(x), "test $i failed: sum != y+x")
        }
    }

    private fun parseBin(s: String): Pol = Pol(s.toULong(2))

    private val polMulTests = listOf(
        Triple(Pol(1UL), Pol(2UL), Pol(2UL)),
        Triple(parseBin("1101"), parseBin("10"), parseBin("11010")),
        Triple(parseBin("1101"), parseBin("11"), parseBin("10111")),
        Triple(Pol(0x40000000UL), Pol(0x40000000UL), Pol(0x1000000000000000UL)),
        Triple(parseBin("1010"), parseBin("100100"), parseBin("101101000")),
        Triple(parseBin("100"), parseBin("11"), parseBin("1100")),
        Triple(parseBin("11"), parseBin("110101"), parseBin("1011111")),
        Triple(parseBin("10011"), parseBin("110101"), parseBin("1100001111"))
    )

    @Test
    fun testPolMul() {
        polMulTests.forEachIndexed { i, (x, y, res) ->
            assertEquals(res, x.mul(y),
                "Test $i failed: ${x.value} * ${y.value}: expected ${res.value}")
            assertEquals(res, y.mul(x),
                "Test $i failed: ${y.value} * ${x.value}: expected ${res.value}")
        }
    }

    @Test
    fun testPolMulOverflow() {
        assertThrows<ArithmeticException> {
            Pol(1UL shl 63).mul(Pol(2UL))
        }
    }

    private val polDivTests = listOf(
        Triple(Pol(10UL), Pol(50UL), Pol(0UL)),
        Triple(Pol(0UL), Pol(1UL), Pol(0UL)),
        Triple(parseBin("101101000"), parseBin("1010"), parseBin("100100")),
        Triple(Pol(2UL), Pol(2UL), Pol(1UL)),
        Triple(Pol(0x8000000000000000UL), Pol(0x8000000000000000UL), Pol(1UL)),
        Triple(parseBin("1100"), parseBin("100"), parseBin("11")),
        Triple(parseBin("1100001111"), parseBin("10011"), parseBin("110101"))
    )

    @Test
    fun testPolDiv() {
        polDivTests.forEachIndexed { i, (x, y, res) ->
            assertEquals(res, x.div(y),
                "Test $i failed: ${x.value} / ${y.value}: expected ${res.value}")
        }
    }

    @Test
    fun testPolDeg() {
        assertEquals(-1, Pol(0UL).deg(), "deg(0) should be -1")
        assertEquals(0, Pol(1UL).deg(), "deg(1) should be 0")

        for (i in 0..63) {
            assertEquals(i, Pol(1UL shl i).deg(), "deg(1<<$i) should be $i")
        }
    }

    private val polModTests = listOf(
        Triple(Pol(10UL), Pol(50UL), Pol(10UL)),
        Triple(Pol(0UL), Pol(1UL), Pol(0UL)),
        Triple(parseBin("101101001"), parseBin("1010"), parseBin("1")),
        Triple(Pol(2UL), Pol(2UL), Pol(0UL)),
        Triple(Pol(0x8000000000000000UL), Pol(0x8000000000000000UL), Pol(0UL)),
        Triple(parseBin("1100"), parseBin("100"), parseBin("0")),
        Triple(parseBin("1100001111"), parseBin("10011"), parseBin("0"))
    )

    @Test
    fun testPolMod() {
        polModTests.forEachIndexed { i, (x, y, res) ->
            assertEquals(res, x.mod(y),
                "Test $i failed: ${x.value} mod ${y.value}: expected ${res.value}")
        }
    }

    private val polIrredTests = listOf(
        Pair(Pol(0x38f1e565e288dfUL), false),
        Pair(Pol(0x3DA3358B4DC173UL), true),
        Pair(Pol(0x30a8295b9d5c91UL), false),
        Pair(Pol(0x255f4350b962cbUL), false),
        Pair(Pol(0x267f776110a235UL), false),
        Pair(Pol(0x2f4dae10d41227UL), false),
        Pair(Pol(0x2482734cacca49UL), true),
        Pair(Pol(0x312daf4b284899UL), false),
        Pair(Pol(0x29dfb6553d01d1UL), false),
        Pair(Pol(0x3548245eb26257UL), false),
        Pair(Pol(0x3199e7ef4211b3UL), false),
        Pair(Pol(0x362f39017dae8bUL), false),
        Pair(Pol(0x200d57aa6fdacbUL), false),
        Pair(Pol(0x35e0a4efa1d275UL), false),
        Pair(Pol(0x2ced55b026577fUL), false),
        Pair(Pol(0x260b012010893dUL), false),
        Pair(Pol(0x2df29cbcd59e9dUL), false),
        Pair(Pol(0x3f2ac7488bd429UL), false),
        Pair(Pol(0x3e5cb1711669fbUL), false),
        Pair(Pol(0x226d8de57a9959UL), false),
        Pair(Pol(0x3c8de80aaf5835UL), false),
        Pair(Pol(0x2026a59efb219bUL), false),
        Pair(Pol(0x39dfa4d13fb231UL), false),
        Pair(Pol(0x3143d0464b3299UL), false)
    )

    @Test
    fun testPolIrreducible() {
        polIrredTests.forEach { (pol, expected) ->
            assertEquals(expected, pol.irreducible(),
                "Irreducibility test for ${pol.value} failed")
        }
    }

    private val polGCDTests = listOf(
        Triple(Pol(10UL), Pol(50UL), Pol(2UL)),
        Triple(Pol(0UL), Pol(1UL), Pol(1UL)),
        Triple(parseBin("101101001"), parseBin("1010"), parseBin("1")),
        Triple(Pol(2UL), Pol(2UL), Pol(2UL)),
        Triple(parseBin("1010"), parseBin("11"), parseBin("11")),
        Triple(Pol(0x8000000000000000UL), Pol(0x8000000000000000UL), Pol(0x8000000000000000UL)),
        Triple(parseBin("1100"), parseBin("101"), parseBin("11")),
        Triple(parseBin("1100001111"), parseBin("10011"), parseBin("10011")),
        Triple(Pol(0x3DA3358B4DC173UL), Pol(0x3DA3358B4DC173UL), Pol(0x3DA3358B4DC173UL)),
        Triple(Pol(0x3DA3358B4DC173UL), Pol(0x230d2259defdUL), Pol(1UL)),
        Triple(Pol(0x230d2259defdUL), Pol(0x51b492b3eff2UL), parseBin("10011"))
    )

    @Test
    fun testPolGCD() {
        polGCDTests.forEachIndexed { i, (f1, f2, expectedGcd) ->
            assertEquals(expectedGcd, f1.gcd(f2),
                "GCD test $i failed for ${f1.value} and ${f2.value}")
            assertEquals(expectedGcd, f2.gcd(f1),
                "GCD test $i failed for ${f2.value} and ${f1.value}")
        }
    }

    private val polMulModTests = listOf(
        Triple(
            Pol(0x1230UL),
            Pol(0x230UL),
            Pair(Pol(0x55UL), Pol(0x22UL))
        ),
        Triple(
            Pol(0x0eae8c07dbbb3026UL),
            Pol(0xd5d6db9de04771deUL),
            Pair(Pol(0xdd2bda3b77c9UL), Pol(0x425ae8595b7aUL))
        )
    )

    @Test
    fun testPolMulMod() {
        polMulModTests.forEachIndexed { i, (f1, f2, pair) ->
            val (g, expected) = pair
            assertEquals(expected, f1.mulMod(f2, g),
                "MulMod test $i failed")
        }
    }

    @Test
    fun testExpandPolynomial() {
        val pol = Pol(0x3DA3358B4DC173UL)
        assertEquals(
            "x^53+x^52+x^51+x^50+x^48+x^47+x^45+x^41+x^40+x^37+x^36+x^34+x^32+x^31+x^27+x^25+x^24+x^22+x^19+x^18+x^16+x^15+x^14+x^8+x^6+x^5+x^4+x+1",
            pol.expand()
        )
    }

    @Test
    fun testRandomPolynomial() {
        val pol = Pol.randomPolynomial()
        assertTrue(pol.irreducible(), "Random polynomial should be irreducible")
    }
}

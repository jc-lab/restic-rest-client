package kr.jclab.restic.chunker

import java.security.SecureRandom

@JvmInline
value class Pol(val value: ULong) {
    fun add(y: Pol): Pol = Pol(value xor y.value)

    fun mul(y: Pol): Pol {
        when {
            value == 0UL || y.value == 0UL -> return Pol(0UL)
            value == 1UL -> return y
            y.value == 1UL -> return this
            y.value == 2UL -> return mul2()
        }

        var res = Pol(0UL)
        for (i in 0..y.deg()) {
            if ((y.value and (1UL shl i)) > 0UL) {
                res = res.add(Pol(value shl i))
            }
        }

        if (res.div(y) != this) {
            throw ArithmeticException("multiplication would overflow uint64")
        }

        return res
    }

    private fun mul2(): Pol {
        if (value and (1UL shl 63) != 0UL) {
            throw ArithmeticException("multiplication would overflow uint64")
        }
        return Pol(value shl 1)
    }

    fun deg(): Int = if (value == 0UL) -1 else 63 - value.countLeadingZeroBits()

    override fun toString(): String = value.toString(16)

    fun expand(): String {
        if (value == 0UL) return "0"

        val terms = mutableListOf<String>()
        for (i in deg() downTo 2) {
            if (value and (1UL shl i) > 0UL) {
                terms.add("x^$i")
            }
        }

        if (value and 2UL > 0UL) terms.add("x")
        if (value and 1UL > 0UL) terms.add("1")

        return terms.joinToString("+")
    }

    fun divMod(d: Pol): Pair<Pol, Pol> {
        if (value == 0UL) return Pair(Pol(0UL), Pol(0UL))
        if (d.value == 0UL) throw ArithmeticException("division by zero")

        val D = d.deg()
        var diff = deg() - D
        if (diff < 0) return Pair(Pol(0UL), this)

        var q = 0UL
        var x = value
        while (diff >= 0) {
            val m = d.value shl diff
            q = q or (1UL shl diff)
            x = x xor m

            val newPol = Pol(x)
            diff = if (newPol == Pol(0UL)) -1 else newPol.deg() - D
        }

        return Pair(Pol(q), Pol(x))
    }

    fun div(d: Pol): Pol = divMod(d).first

    fun mod(d: Pol): Pol = divMod(d).second

    /**
     * GCD computes the Greatest Common Divisor x and f.
     */
    fun gcd(f: Pol): Pol {
        if (f == Pol(0UL)) return this
        if (this == Pol(0UL)) return f

        var x = this
        var y = f
        if (x.deg() < y.deg()) {
            val temp = x
            x = y
            y = temp
        }

        return y.gcd(x.mod(y))
    }

    /**
     * MulMod computes x*f mod g
     */
    fun mulMod(f: Pol, g: Pol): Pol {
        if (value == 0UL || f.value == 0UL) return Pol(0UL)

        var res = Pol(0UL)
        for (i in 0..f.deg()) {
            if ((f.value and (1UL shl i)) > 0UL) {
                var a = this
                repeat(i) {
                    a = a.mul(Pol(2UL)).mod(g)
                }
                res = res.add(a).mod(g)
            }
        }

        return res
    }

    /**
     * Irreducible returns true iff x is irreducible over F_2. This function
     * uses Ben Or's reducibility test.
     *
     * For details see "Tests and Constructions of Irreducible Polynomials over
     * Finite Fields".
     */
    fun irreducible(): Boolean {
        for (i in 1..deg() / 2) {
            if (gcd(qp(i.toUInt(), this)) != Pol(1UL)) {
                return false
            }
        }
        return true
    }

    companion object {
        private val random = SecureRandom()

        private const val RAND_POL_MAX_TRIES = 1_000_000

        fun randomPolynomial(): Pol {
            repeat(RAND_POL_MAX_TRIES) {
                val bytes = ByteArray(8)
                random.nextBytes(bytes)
                var value = 0UL
                for (i in bytes.indices) {
                    value = value or (bytes[i].toUByte().toULong() shl (i * 8))
                }

                // mask away bits above bit 53
                value = value and ((1UL shl 54) - 1UL)
                // set highest and lowest bit
                value = value or (1UL shl 53) or 1UL

                val pol = Pol(value)
                if (pol.irreducible()) {
                    return pol
                }
            }

            throw IllegalStateException("unable to find new random irreducible polynomial")
        }

        /**
         * qp computes the polynomial (x^(2^p)-x) mod g. This is needed for the
         * reducibility test.
         */
        private fun qp(p: UInt, g: Pol): Pol {
            val num = 1 shl p.toInt()
            var i = 1

            // start with x
            var res = Pol(2UL)

            while (i < num) {
                // repeatedly square res
                res = res.mulMod(res, g)
                i *= 2
            }

            // add x
            return res.add(Pol(2UL)).mod(g)
        }
    }
}

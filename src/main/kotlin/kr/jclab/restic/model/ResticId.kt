package kr.jclab.restic.model

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.util.encoders.Hex

@JvmInline
value class ResticId(
    val data: ByteArray = ByteArray(SIZE)
) {
    init {
        if (data.size != SIZE) {
            throw IllegalArgumentException("invalid size: ${data.size}")
        }
    }

    companion object {
        const val SIZE = 32 // sha-256

        fun hash(data: ByteArray): ResticId {
            val d = SHA256Digest.newInstance()
            d.update(data, 0, data.size)
            val data = ByteArray(SIZE)
            d.doFinal(data, 0)
            return ResticId(data)
        }

        fun parse(input: String): ResticId {
            return ResticId(Hex.decode(input))
        }
    }

    override fun toString(): String {
        return Hex.toHexString(data)
    }

    fun isEqualsTo(o: ResticId): Boolean {
        return toString() == o.toString()
    }
}
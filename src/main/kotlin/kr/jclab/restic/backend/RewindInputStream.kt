package kr.jclab.restic.backend

import java.io.InputStream

abstract class RewindInputStream : InputStream() {
    abstract fun rewind()

    companion object {
        fun from(buf: ByteArray): RewindInputStream {
            return SimpleBuffered(buf)
        }
    }

    class SimpleBuffered(
        private val buf: ByteArray
    ) : RewindInputStream() {
        private var pos: Int = 0

        override fun read(): Int {
            return if (pos < buf.size) {
                buf[pos++].toInt() and 0xFF
            } else {
                -1
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (pos >= buf.size) {
                return -1
            }
            val bytesRead = minOf(len, buf.size - pos)
            System.arraycopy(buf, pos, b, off, bytesRead)
            pos += bytesRead
            return bytesRead
        }

        override fun available(): Int {
            return buf.size - pos
        }

        override fun rewind() {
            pos = 0
        }
    }
}

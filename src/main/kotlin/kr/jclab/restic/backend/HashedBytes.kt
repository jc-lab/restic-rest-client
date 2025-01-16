package kr.jclab.restic.backend

import kr.jclab.restic.model.ResticId

class HashedBytes(
    buf: ByteArray,
) : HashedValue<ByteArray>(
    buf
) {
    override fun getHash(): ResticId {
        return ResticId.hash(data)
    }
}

package kr.jclab.restic.backend

import kr.jclab.restic.model.ResticId

abstract class HashedValue<T>(
    val data: T,
) {
    abstract fun getHash(): ResticId
}

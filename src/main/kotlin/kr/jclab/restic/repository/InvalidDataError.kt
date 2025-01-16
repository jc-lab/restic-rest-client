package kr.jclab.restic.repository

import kr.jclab.restic.model.ResticId

class InvalidDataError(
    val expected: ResticId,
    val actual: ResticId,
) : RuntimeException("expected=${expected}, actual=${actual}")
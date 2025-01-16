package kr.jclab.restic.repository

class RepositoryAlreadyExistsError(
    message: String,
) : RuntimeException(message)
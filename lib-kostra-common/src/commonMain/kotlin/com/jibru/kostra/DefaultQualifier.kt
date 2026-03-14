package com.jibru.kostra

expect fun defaultQualifiers(): KQualifiers

interface IDefaultQualifiersProvider {
    val current: KQualifiers
}

object DefaultQualifiersProvider : IDefaultQualifiersProvider {
    var delegate: IDefaultQualifiersProvider? = null

    override val current: KQualifiers get() = delegate?.current ?: defaultQualifiers()
}

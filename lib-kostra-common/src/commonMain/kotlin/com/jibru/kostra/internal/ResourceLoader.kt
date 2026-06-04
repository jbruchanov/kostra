package com.jibru.kostra.internal

//Prepends the assets root and delegates to the active KostraResourceStorage (installed one, else the
//platform default). The per-platform reading logic now lives in those storages, not in expect/actuals.
internal fun loadResource(key: String): ByteArray = KostraResourceStorage.read("${KostraAssets.RootDir}/$key")

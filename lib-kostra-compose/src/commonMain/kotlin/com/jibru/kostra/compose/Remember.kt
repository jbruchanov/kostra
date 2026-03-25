package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import com.jibru.kostra.KQualifiers
import androidx.compose.runtime.remember as composableRemember

@Composable
public inline fun <T> rememberWithQualifiers(
    crossinline calculation: @DisallowComposableCalls (KQualifiers) -> T,
): T {
    val qualifiers = LocalQualifiers.current
    return composableRemember(LocalQualifiers.current) { calculation(qualifiers) }
}

@Composable
public inline fun <T> rememberWithQualifiers(
    key1: Any?,
    crossinline calculation: @DisallowComposableCalls (KQualifiers) -> T,
): T {
    val qualifiers = LocalQualifiers.current
    return composableRemember(key1, LocalQualifiers.current) { calculation(qualifiers) }
}

@Composable
public inline fun <T> rememberWithQualifiers(
    key1: Any?,
    key2: Any?,
    crossinline calculation: @DisallowComposableCalls (KQualifiers) -> T,
): T {
    val qualifiers = LocalQualifiers.current
    return composableRemember(key1, key2, LocalQualifiers.current) { calculation(qualifiers) }
}

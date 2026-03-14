package com.jibru.kostra.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import com.jibru.kostra.DefaultQualifiersProvider
import com.jibru.kostra.KQualifiers

val LocalQualifiers = compositionLocalOf { DefaultQualifiersProvider.current }

@Composable
fun ProvideLocalQualifiers(qualifiers: KQualifiers = DefaultQualifiersProvider.current, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQualifiers provides qualifiers) {
        content()
    }
}

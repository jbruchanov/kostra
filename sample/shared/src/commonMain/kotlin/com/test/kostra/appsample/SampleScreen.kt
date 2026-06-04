@file:OptIn(ExperimentalLayoutApi::class)

package com.test.kostra.appsample

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.darkColors
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.jibru.kostra.KDpi
import com.jibru.kostra.KLocale
import com.jibru.kostra.KQualifiers
import com.jibru.kostra.compose.KostraPreviewEffect
import com.jibru.kostra.compose.LocalQualifiers
import com.jibru.kostra.icu.FixedDecimal
import com.sample.app.K
import com.sample.app.compose.assetPath
import com.sample.app.compose.get
import com.sample.app.compose.ordinalResource
import com.sample.app.compose.painterResource
import com.sample.app.compose.pluralResource
import com.sample.app.compose.stringResource
import com.sample.lib1.compose.get

private object SampleScreenDefaults {
    val spacing = 8.dp
}

@Composable
fun SampleScreen(extraContent: @Composable ColumnScope.() -> Unit = {}) {
    val showKeyboardBanner = remember { getPlatform().isIos }
    Box(modifier = Modifier.fillMaxSize()) {
        SampleScreenContent(extraContent = extraContent)
        if (showKeyboardBanner) {
            KeyboardDismissBanner(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SampleScreenContent(
    extraContent: @Composable ColumnScope.() -> Unit,
) = with(SampleScreenDefaults) {
    Box(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(spacing),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .padding(spacing)
                .animateContentSize()
                .wrapContentSize(),
        ) {
            val defaultQualifiers = LocalQualifiers.current
            val locales = remember {
                val codes = listOf("ar", "cs", "en", "enGB", "enUS", "he", "hi", "ja", "ko", "ru", "th")
                (listOf(defaultQualifiers.locale) + codes.map { KLocale(it) }).distinct()
            }
            var localeIndex by remember { mutableIntStateOf(0) }
            val locale by remember { derivedStateOf { KLocale(locales[localeIndex].tag) } }

            val dpis = remember { listOf(null, KDpi.Undefined, KDpi.XHDPI, KDpi.XXHDPI) }
            var dpiIndex by remember { mutableIntStateOf(0) }
            val dpi by remember { derivedStateOf { dpis[dpiIndex] ?: defaultQualifiers.dpi } }

            val qualifiers by remember { derivedStateOf { KQualifiers(locale, dpi) } }
            CompositionLocalProvider(LocalQualifiers provides qualifiers) {
                Text("KQualifiers:$defaultQualifiers")
                Text("KLocale")
                FlowRow(
                    verticalArrangement = Arrangement.Center,
                ) {
                    locales.forEachIndexed { index, locale ->
                        TextCheckBox(
                            onCheckedChange = { if (it) localeIndex = index else Unit },
                            checked = index == localeIndex,
                            text = locale.languageRegion,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Text("DPI: ${defaultQualifiers.dpi}")
                Text("Density:${LocalDensity.current}")
                FlowRow(
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    dpis.forEachIndexed { index, dpi ->
                        val text = buildAnnotatedString {
                            append(dpi?.name ?: "Dev:${defaultQualifiers.dpi.name}")
                            if (dpi == KDpi.XXHDPI) {
                                withStyle(SpanStyle(fontSize = TextUnit(0.5f, TextUnitType.Em))) {
                                    append("\n(most of capital_city asset)")
                                }
                            }
                        }

                        TextCheckBox(
                            onCheckedChange = { if (it) dpiIndex = index else Unit },
                            checked = index == dpiIndex,
                            text = text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    Button(onClick = {}) { Text(stringResource(K.string.action_add)) }
                    Button(onClick = {}) { Text(stringResource(K.string.action_remove)) }
                    Button(onClick = {}) { Text(KM.string.color.get()) }
                    Button(onClick = {}) { Text(KM.string.lib1_text.get()) }
                    //Lib2 is not released with compose dependency, `get()` must be done manually
                    Button(onClick = {}) { Text(KM.string.lib2_text.get()) }
                }

                Text(assetPath(K.images.capital_city))
                Image(painterResource(K.images.capital_city), contentDescription = null)

                Spacer(modifier = Modifier.height(4.dp))

                Row {
                    Image(KM.images.gear1.get(), contentDescription = null)
                    Image(KM.images.gear2.get(), contentDescription = null)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(assetPath(K.flagsxml.country_flag))
                Image(painterResource(K.flagsxml.country_flag), contentDescription = null, modifier = Modifier.height(64.dp))

                extraContent()

                var quantity by remember { mutableStateOf("1") }

                TextField(
                    value = quantity,
                    label = { Text(K.string.plurals.get(1, 2, 3)) },
                    placeholder = { Text(stringResource(K.string.type_quantity)) },
                    onValueChange = { quantity = it.takeIf { it.isEmpty() || it.toFloatOrNull() != null } ?: quantity },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                Text(stringResource(K.string.plurals) + ": " + (quantity.toDoubleOrNull()?.let { pluralResource(K.plural.bug_x, FixedDecimal(it), quantity) } ?: ""))
                Text(stringResource(K.string.ordinals) + ": " + (quantity.toDoubleOrNull()?.let { ordinalResource(K.plural.day_x, FixedDecimal(it), quantity) } ?: ""))
            }
        }
    }
}

@Composable
private fun KeyboardDismissBanner(modifier: Modifier = Modifier) = with(SampleScreenDefaults) {
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val focusManager = LocalFocusManager.current
    if (keyboardVisible) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .background(Color.DarkGray)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(spacing),
        ) {
            Text("Keyboard visible", modifier = Modifier.weight(1f))
            Button(onClick = { focusManager.clearFocus() }) {
                Text("Hide")
            }
        }
    }
}

@Composable
private fun TextCheckBox(onCheckedChange: (Boolean) -> Unit, checked: Boolean, text: CharSequence, modifier: Modifier = Modifier) = with(SampleScreenDefaults) {
    @Suppress("NAME_SHADOWING")
    val checked by rememberUpdatedState(checked)
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(spacing))
            .clickable { onCheckedChange(!checked) }
            .padding(end = spacing),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        @Suppress("NAME_SHADOWING")
        val text = when {
            text is AnnotatedString -> text
            else -> AnnotatedString(text.toString())
        }
        Text(text)
    }
}

@Composable
@Preview(name = "Default")
@Preview(name = "MDPI", device = "spec:width=411dp,height=891dp,dpi=160")
@Preview(name = "MDPI cs", locale = "cs", device = "spec:width=411dp,height=891dp,dpi=160")
@Preview(name = "XHDPI cs", locale = "cs", device = "spec:width=411dp,height=891dp,dpi=480")
private fun SampleScreenPreview() = MaterialTheme(darkColors()) {
    KostraPreviewEffect()
    SampleScreen()
}

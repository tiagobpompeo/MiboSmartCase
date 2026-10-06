package br.com.pompeo.casa.platform

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

// O framework é ligado em Debug ou Release pelo Xcode; o binário Kotlin/Native sabe qual dos dois é.
@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean get() = Platform.isDebugBinary

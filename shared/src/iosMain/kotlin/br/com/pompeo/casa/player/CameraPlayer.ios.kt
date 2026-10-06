package br.com.pompeo.casa.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitView

/** iOS: o AVPlayer não reproduz RTSP; usamos o VLCKit, implementado em Swift (ver [NativeVideoPlayer]). */
@Composable
actual fun CameraPlayer(url: String, muted: Boolean, modifier: Modifier, onProgress: (PlayerProgress) -> Unit) {
    val factory = NativePlayers.factory
    if (factory == null) {
        // Esqueceu o registro no iOSApp.swift: mostra o problema em vez de quebrar.
        Box(modifier.background(Color(0xFF171E24)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VideocamOff, null, tint = Color.White)
                Text("Player nativo não registrado", color = Color.White)
            }
        }
        return
    }
    // key(url): o factory do UIKitView roda uma vez por nó; URL nova = nó novo com a view do player novo.
    key(url) {
        val player = remember { factory.create() }
        val currentOnProgress by rememberUpdatedState(onProgress)
        DisposableEffect(player) {
            // Objeto Kotlin implementando interface Kotlin, entregue ao Swift: o VLCKit chama de volta.
            player.setListener(object : NativeVideoListener {
                override fun onProgress(percent: Int, label: String) = currentOnProgress(PlayerProgress(percent, label))
                override fun onFirstFrame() = currentOnProgress(PlayerProgress(100, "Ao vivo", playing = true))
                override fun onError(message: String) = currentOnProgress(PlayerProgress(0, "", error = message))
            })
            currentOnProgress(PlayerProgress(32, "Iniciando player"))
            player.play(url)
            onDispose {
                player.setListener(null)
                player.stop()
            }
        }
        LaunchedEffect(player, muted) { player.setMuted(muted) }
        UIKitView(factory = { player.view() }, modifier = modifier)
    }
}

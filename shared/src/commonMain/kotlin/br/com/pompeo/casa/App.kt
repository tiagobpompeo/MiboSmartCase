package br.com.pompeo.casa

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Raiz da UI compartilhada: a mesma árvore Compose roda no Android e no iOS. */
@Composable
fun App() {
    MaterialTheme { Text("Casa Inteligente") }
}

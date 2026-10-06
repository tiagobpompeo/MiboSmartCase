package br.com.pompeo.casa.ui

import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceOrigin

/** Lentes da linha "Dual" na ordem dos canais da GDI: canalVideo 0 = móvel, 1 = fixa. Usado pela CameraScreen (M8). */
val LENS_LABELS = listOf("Lente móvel", "Lente fixa")

/** Lentes pelo nome do modelo: a GDI não informa canais; a linha "Dual" (iM4 Dual) tem duas (móvel + fixa).
 *  O nome também é consultado porque o modelo pode vir genérico e o nome de fábrica traz o sufixo. */
fun lensCount(model: String?, name: String? = null): Int =
    if (listOfNotNull(model, name).any { it.contains("dual", ignoreCase = true) }) 2 else 1

/** "Online"/"Offline"; quando a GDI não informa (null) mostramos "—" em vez de inventar estado. */
fun onlineLabel(online: Boolean?): String = when (online) { true -> "Online"; false -> "Offline"; null -> "—" }

/** Rótulo "compartilhado"; vinculado é o caso comum e não ganha rótulo. */
fun sharedLabel(origin: DeviceOrigin?): String? = if (origin == DeviceOrigin.SHARED) "compartilhado" else null

/** Subtítulo da linha no modo lista: "MFR 2030 • via hub • compartilhado". */
fun deviceSubtitle(device: Device): String =
    listOfNotNull(device.model ?: device.kind.label, "via hub".takeIf { device.subdevice }, sharedLabel(device.origin)).joinToString(" • ")

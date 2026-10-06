package br.com.pompeo.casa

import br.com.pompeo.casa.data.gdi.GdiDeviceParser
import br.com.pompeo.casa.data.gdi.gdiApiNs
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceOrigin
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Listagem com a forma real da conta de teste (câmera iM4 Dual, fechadura MFR 2030 no hub MCA 1002); ns fictícios. */
class GdiRealResponseTest {
    private fun parseRealListing(): List<Device> = GdiDeviceParser.parse(Json.parseToJsonElement(REAL_LISTING))

    @Test
    fun keepsCamerasLockAndHubFromRealListing() {
        // Act
        val devices = parseRealListing()
        // Assert
        assertEquals(listOf("CAM000000000001", "LOCK000000000002", "HUB0000000003", "CAM000000000004"), devices.map { it.ns })
        assertEquals(listOf<Boolean?>(true, true, true, false), devices.map { it.online }, "online vem de status")
        assertEquals(
            listOf(DeviceOrigin.LINKED, DeviceOrigin.LINKED, DeviceOrigin.LINKED, DeviceOrigin.SHARED),
            devices.map { it.origin },
        )
        assertEquals(listOf("CAM000000000001", "CAM000000000004"), devices.filter { it.isCamera }.map { it.ns })
    }

    @Test
    fun classifiesCameraLockAndHubAndBuildsSubdeviceNs() {
        // Act
        val (camera, lock, hub) = parseRealListing()
        // Assert
        assertEquals(DeviceKind.CAMERA, camera.kind)
        assertEquals(DeviceKind.LOCK, lock.kind)
        assertEquals(DeviceKind.HUB, hub.kind)
        assertEquals("IOT-ZG2-IB", hub.model, "o hub MCA 1002 vem com outro modelo: o nome também é consultado")
        assertEquals(DeviceKind.HUB, GdiDeviceParser.inferKind(type = null, model = null, name = "MCA 1002-0003"))
        assertTrue(lock.subdevice)
        assertEquals("LOCK000000000002_HUB0000000003_PRODHUB1", lock.gdiApiNs, "fechadura Zigbee usa o ns composto")
        assertEquals("PRODLCK1", lock.productId, "o corpo leva o idProduto da própria fechadura")
        assertEquals("CAM000000000001", camera.gdiApiNs, "dispositivo que não é subdispositivo usa o ns puro")
        assertEquals("02/10/2026 19:29:05 UTC", lock.lastOnline)
        assertNull(hub.lastOnline)
    }

    private companion object {
        const val REAL_LISTING = """{"status":"sucesso","data":[
 {"ns":"CAM000000000001","modelo":"iM4 Dual","nome":"iM4 Dual-0001","status":"online","versao":"2.8.0","subdispositivo":false,"idProduto":"PRODCAM1","ultimaVezOnline":"20261005T120000Z","origem":"vinculado","atualizacaoDisponivel":false},
 {"ns":"LOCK000000000002","modelo":"MFR 2030","nome":"MFR 2030-0002","status":"online","versao":"1.0.0","subdispositivo":true,"idProduto":"PRODLCK1","ultimaVezOnline":"20261002T192905Z","origem":"vinculado","dispositivoPai":"HUB0000000003","idProdutoDispositivoPai":"PRODHUB1","atualizacaoDisponivel":false},
 {"ns":"HUB0000000003","modelo":"IOT-ZG2-IB","nome":"MCA 1002-0003","status":"online","versao":"2.4.628243","subdispositivo":false,"idProduto":"PRODHUB1","origem":"vinculado","atualizacaoDisponivel":false},
 {"ns":"CAM000000000004","modelo":"iM5","nome":"Garagem","status":"offline","subdispositivo":false,"idProduto":"PRODCAM4","origem":"compartilhado"}
]}"""
    }
}

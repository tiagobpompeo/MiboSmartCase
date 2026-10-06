package br.com.pompeo.casa

import br.com.pompeo.casa.data.gdi.GdiDeviceParser
import br.com.pompeo.casa.domain.model.Device
import br.com.pompeo.casa.domain.model.DeviceKind
import br.com.pompeo.casa.domain.model.DeviceOrigin
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O Swagger não documenta o corpo da listagem: o parser precisa tolerar envelopes e nomes de campo alternativos. */
class GdiDeviceParserTest {
    private fun parse(json: String): List<Device> = GdiDeviceParser.parse(Json.parseToJsonElement(json))

    @Test
    fun parsesEnvelopeWithPortugueseKeys() {
        // Arrange: lista dentro de data.dispositivos, com chaves alternativas em português.
        val json = """
            {"status":"sucesso","data":{"dispositivos":[
              {"numeroSerie":"LAMP0001","nomeDispositivo":"Luz da sala","tipo":"lampada","idProduto":"PLAMP1","online":true},
              {"ns":"CAM0002","apelido":"Entrada","modelo":"iM3","online":false}
            ]}}
        """
        // Act
        val devices = parse(json)
        // Assert
        assertEquals(listOf("LAMP0001", "CAM0002"), devices.map { it.ns })
        val (lamp, camera) = devices
        assertEquals("Luz da sala", lamp.name)
        assertEquals(DeviceKind.LAMP, lamp.kind)
        assertEquals("PLAMP1", lamp.productId)
        assertEquals(true, lamp.online)
        assertEquals(DeviceOrigin.UNKNOWN, lamp.origin, "sem campo de origem")
        assertEquals("Entrada", camera.name)
        assertEquals(DeviceKind.CAMERA, camera.kind)
        assertEquals(false, camera.online)
        assertTrue(devices.all { it.providerId == GdiDeviceParser.PROVIDER_ID })
    }

    @Test
    fun parsesFlatArrayWithEnglishKeysAndStatusStrings() {
        // Arrange: lista plana, chaves em inglês e o mesmo número de série repetido.
        val json = """
            [
              {"serial":"CAM0001","name":"Garage","model":"iM5","status":"online","origin":"shared"},
              {"serialNumber":"CAM0001","name":"Garage again","model":"iM5","status":"online"},
              {"deviceSn":"LOCK0002","deviceName":"Front door","productName":"MFR 2030","productId":"PLOCK2","status":"offline"}
            ]
        """
        // Act
        val devices = parse(json)
        // Assert
        assertEquals(listOf("CAM0001", "LOCK0002"), devices.map { it.ns }, "duplicata por ns descartada")
        val (camera, lock) = devices
        assertEquals("Garage", camera.name, "fica a primeira ocorrência")
        assertEquals(DeviceKind.CAMERA, camera.kind)
        assertEquals(true, camera.online, "status \"online\" (string) vira true")
        assertEquals(DeviceOrigin.UNKNOWN, camera.origin, "a GDI fala português: \"shared\" não é uma origem conhecida")
        assertEquals(DeviceKind.LOCK, lock.kind)
        assertEquals("PLOCK2", lock.productId)
        assertEquals(false, lock.online)
    }

    @Test
    fun unknownTypeAndModelIsTreatedAsCamera() {
        // Arrange: sem tipo, sem modelo e sem status; a linha original da GDI era só de câmeras.
        val json = """{"data":[{"ns":"ABC0001","nome":"Varanda"}]}"""
        // Act
        val device = parse(json).single()
        // Assert
        assertEquals(DeviceKind.CAMERA, device.kind)
        assertNull(device.online, "a API não informou: desconhecido, não offline")
    }

    @Test
    fun ignoresObjectsWithoutSerial() {
        // Arrange: envelopes de erro reais e um item sem número de série.
        val bodies = listOf(
            """{"status":"erro","msg":"Token expirado, por favor gere um novo token"}""",
            """{"message":"Forbidden"}""",
            "\"Não autorizado\"",
            """{"status":"sucesso","data":[{"nome":"Sem série","modelo":"iM5","status":"online"}]}""",
        )
        // Act
        val parsed = bodies.map { parse(it) }
        // Assert
        assertTrue(parsed.all { it.isEmpty() }, parsed.toString())
    }

    @Test
    fun parsesOriginValues() {
        // Arrange: singular no item, plural no filtro, caixa e espaços variados; nulo e desconhecido viram UNKNOWN.
        val json = """
            [
              {"ns":"N1","nome":"Sala","origem":"vinculado"},
              {"ns":"N2","nome":"Sala","origem":"VINCULADOS"},
              {"ns":"N3","nome":"Sala","origem":" compartilhado "},
              {"ns":"N4","nome":"Sala","origem":"Compartilhados"},
              {"ns":"N5","nome":"Sala","origem":null},
              {"ns":"N6","nome":"Sala","origem":"desconhecido"},
              {"ns":"N7","nome":"Sala"}
            ]
        """
        // Act
        val origins = parse(json).associate { it.ns to it.origin }
        // Assert
        assertEquals(
            mapOf(
                "N1" to DeviceOrigin.LINKED,
                "N2" to DeviceOrigin.LINKED,
                "N3" to DeviceOrigin.SHARED,
                "N4" to DeviceOrigin.SHARED,
                "N5" to DeviceOrigin.UNKNOWN,
                "N6" to DeviceOrigin.UNKNOWN,
                "N7" to DeviceOrigin.UNKNOWN,
            ),
            origins,
        )
    }
}

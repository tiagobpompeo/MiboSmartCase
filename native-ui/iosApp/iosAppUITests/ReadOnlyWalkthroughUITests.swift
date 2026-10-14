import XCTest

/// Percurso de LEITURA no aparelho físico, usado para conferir o app e gerar as capturas:
/// token -> Entrar -> Home -> fechadura -> hub -> câmera. As capturas saem como anexos do .xcresult
/// (`xcrun xcresulttool export attachments`).
///
/// Regra inviolável: nenhum comando físico à fechadura. O teste nunca toca em "Destrancar"/"Trancar"/"Confirmar";
/// se algum diálogo aparecer por acidente, ele toca em "Cancelar". Não existe caminho até `setLock`/`changeVolume`
/// sem o botão de confirmação do diálogo.
final class ReadOnlyWalkthroughUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
    }

    func testReadOnlyWalkthrough() throws {
        // 1. Token: o campo vem pré-preenchido (desenvolvimento); o teste só toca em "Entrar".
        //    Se o token já estiver no Keychain (execução anterior), a Home aparece direto.
        let enter = app.buttons["Entrar"]
        if enter.waitForExistence(timeout: 8) {
            attach("01-token")
            enter.tap()
        }

        // 2. Home com a listagem.
        let lockRow = row("row-LOCK")
        XCTAssertTrue(lockRow.waitForExistence(timeout: 90), "A Home não listou a fechadura em 90 s")
        sleep(2)
        attach("02-home")

        // 3. Fechadura: só leituras (estado, bateria, online, volume, histórico).
        openRow("row-LOCK")
        XCTAssertTrue(app.staticTexts["Abertura remota"].waitForExistence(timeout: 30), "Tela da fechadura não abriu")
        sleep(8) // lockDetails (estado, bateria, volume, histórico) + isOnline
        cancelDialogIfAny()
        attach("03-fechadura")
        scrollDown()
        cancelDialogIfAny()
        attach("04-fechadura-volume-historico")
        goBack()

        // 4. Hub: online, firmware e acessórios.
        openRow("row-HUB")
        XCTAssertTrue(app.staticTexts["Versão"].waitForExistence(timeout: 30), "Tela do hub não abriu")
        sleep(6)
        attach("05-hub")
        goBack()

        // 5. Câmera (bônus): sessão RTSP + VLCKit; espera o "AO VIVO" até 60 s e captura o que houver.
        if row("row-CAMERA").waitForExistence(timeout: 30) {
            openRow("row-CAMERA")
            let live = app.descendants(matching: .any).matching(identifier: "camera-live").firstMatch
            let gotLive = live.waitForExistence(timeout: 60)
            sleep(3)
            // Diagnóstico: depois do 1.º quadro o overlay de progresso não pode coexistir com o selo "AO VIVO".
            let loadingStillThere = app.descendants(matching: .any).matching(identifier: "camera-loading").firstMatch.exists
            let note = XCTAttachment(string: "AO VIVO: \(gotLive) · overlay de carregamento ainda visível: \(loadingStillThere)")
            note.name = "06-camera-diagnostico"
            note.lifetime = .keepAlways
            add(note)
            attach("06-camera")
            XCTAssertFalse(gotLive && loadingStillThere, "Overlay de carregamento continua sobre o vídeo depois do 1.º quadro")
            goBack()
        }
        XCTAssertTrue(lockRow.waitForExistence(timeout: 30), "Não voltou para a Home")
    }

    // MARK: - Apoio

    private func row(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    /// Espera a Home assentar (barra "Minha casa") e a linha ficar tocável antes do toque: logo após o pop de
    /// uma tela de detalhe a linha existe na árvore mas ainda não está estável, e o tap falhava.
    private func openRow(_ identifier: String) {
        XCTAssertTrue(app.navigationBars["Minha casa"].waitForExistence(timeout: 30), "Home não apareceu")
        let element = row(identifier)
        let deadline = Date().addingTimeInterval(30)
        while Date() < deadline, !(element.exists && element.isHittable) { usleep(300_000) }
        XCTAssertTrue(element.exists, "Linha \(identifier) não apareceu na Home")
        element.tap()
    }

    private func attach(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func goBack() {
        app.navigationBars.buttons.element(boundBy: 0).tap()
    }

    /// Arrasta a partir da área do estado (sem botões) para mostrar volume e histórico.
    private func scrollDown() {
        let start = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.30))
        let end = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.06))
        start.press(forDuration: 0.1, thenDragTo: end)
        sleep(1)
    }

    /// Segurança extra: qualquer diálogo de confirmação é cancelado, nunca confirmado.
    private func cancelDialogIfAny() {
        let cancel = app.buttons["Cancelar"]
        if cancel.exists { cancel.tap() }
    }
}

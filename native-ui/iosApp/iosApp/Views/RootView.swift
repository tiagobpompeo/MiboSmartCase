import SwiftUI

/// Raiz: token guardado -> Home; senão -> tela de token. Os dois modelos de sessão/listagem vivem aqui e descem
/// pelo environment; os modelos de detalhe (câmera, fechadura, hub) nascem em cada tela, só com o `ns`.
struct RootView: View {
    @StateObject private var session = SessionModel()
    @StateObject private var devices = DevicesModel()

    var body: some View {
        Group {
            // Durante a validação o token já está em memória (hasToken = true): a TokenView fica até o resultado.
            if session.hasToken && !session.validating {
                HomeView()
            } else {
                TokenView()
            }
        }
        .environmentObject(session)
        .environmentObject(devices)
        .tint(MiboColors.green)
    }
}

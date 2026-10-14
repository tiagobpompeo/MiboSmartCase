import SwiftUI
import Shared

/// Definições: conta (token só mascarado, parceiros), itens por página (RF08) e Sair.
struct SettingsView: View {
    @EnvironmentObject private var session: SessionModel
    @EnvironmentObject private var devices: DevicesModel
    @State private var confirmLogout = false

    var body: some View {
        List {
            Section("Conta Intelbras") {
                LabeledContent("Parceiros", value: devices.providerNames.joined(separator: " · "))
                // O token inteiro nunca chega à UI: a fachada só expõe "Ot_ab…wxyz".
                LabeledContent("Token", value: session.maskedToken ?? textPlaceholder)
                LabeledContent("Armazenamento", value: session.persisted ? "Keychain deste aparelho" : "Só nesta sessão")
                Button("Atualizar dispositivos") { devices.refresh() }
            }
            Section("Listagem") {
                Picker("Itens por página", selection: pageSizeBinding) {
                    ForEach(devices.pageSizes, id: \.self) { size in
                        Text("\(size)").tag(size)
                    }
                }
                .pickerStyle(.segmented)
                Text("2 existe para ver o \"Carregar mais\" com a conta de teste (3 dispositivos).")
                    .font(.footnote).foregroundStyle(MiboColors.textSecondary)
            }
            Section {
                Button("Sair", role: .destructive) { confirmLogout = true }
            } footer: {
                Text("Apaga o token do Keychain e volta para a tela de token.")
            }
            Section("Sobre") {
                LabeledContent("UI", value: "SwiftUI nativo")
                LabeledContent("Regra e dados", value: "Kotlin Multiplatform (Shared)")
            }
        }
        .scrollContentBackground(.hidden)
        .background(MiboColors.pageBg)
        .navigationTitle("Definições")
        .navigationBarTitleDisplayMode(.inline)
        .confirmationDialog("Sair da conta?", isPresented: $confirmLogout, titleVisibility: .visible) {
            Button("Sair", role: .destructive) { session.logout() }
            Button("Cancelar", role: .cancel) {}
        }
    }

    private var pageSizeBinding: Binding<Int32> {
        Binding(get: { devices.query.pageSize }, set: { devices.setPageSize($0) })
    }
}

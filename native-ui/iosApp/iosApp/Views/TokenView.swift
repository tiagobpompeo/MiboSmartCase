import SwiftUI
import Shared

/// RF01: campo seguro, pré-preenchido com o token de desenvolvimento (nunca login automático), botão "Entrar".
/// O valor digitado fica só no @State; a tela nunca o imprime nem registra.
struct TokenView: View {
    @EnvironmentObject private var session: SessionModel
    @State private var token = ""
    @State private var visible = false

    private var prefilled: Bool { session.suggestedToken != nil && token == session.suggestedToken }
    private var canSubmit: Bool { !session.validating && !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Conectar à conta Intelbras")
                        .font(.title2.bold())
                    Text("Cole o token temporário gerado no portal Casa Inteligente (menu Contas → Token Temporário). O token fica guardado com criptografia neste aparelho.")
                        .font(.subheadline)
                        .foregroundStyle(MiboColors.textSecondary)

                    tokenField

                    if prefilled {
                        Text("Pré-preenchido pelo local.properties (desenvolvimento)")
                            .font(.footnote)
                            .foregroundStyle(MiboColors.textSecondary)
                    }

                    if let error = session.error {
                        errorBlock(error)
                    }

                    Button(action: { session.submit(token) }) {
                        HStack(spacing: 8) {
                            if session.validating {
                                ProgressView().tint(.white)
                                Text("Validando token…")
                            } else {
                                Text("Entrar")
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canSubmit)

                    Text("O token nunca é enviado para fora do domínio\napi-casainteligente.intelbras.com.br.")
                        .font(.footnote)
                        .foregroundStyle(MiboColors.textSecondary)
                }
                .padding(20)
            }
            .background(MiboColors.pageBg)
            .navigationTitle("Casa Nativa")
            .onAppear {
                if token.isEmpty, let suggested = session.suggestedToken { token = suggested }
            }
        }
    }

    private var tokenField: some View {
        HStack {
            Group {
                if visible {
                    TextField("Token de acesso", text: $token)
                } else {
                    SecureField("Token de acesso", text: $token)
                }
            }
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .font(.body.monospaced())
            Button(action: { visible.toggle() }) {
                Image(systemName: visible ? "eye.slash" : "eye")
                    .foregroundStyle(MiboColors.textSecondary)
            }
            .accessibilityLabel(visible ? "Ocultar token" : "Mostrar token")
        }
        .padding(12)
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: 0xE6E8EA)))
    }

    /// `userMessage` é o texto para o usuário; `detail` é técnico e só aparece em build de depuração.
    @ViewBuilder
    private func errorBlock(_ error: AppError) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(error.userMessage)
                .font(.subheadline)
                .foregroundStyle(.red)
            #if DEBUG
            if let detail = error.detail {
                Text(detail)
                    .font(.caption.monospaced())
                    .foregroundStyle(MiboColors.textSecondary)
            }
            #endif
        }
    }
}

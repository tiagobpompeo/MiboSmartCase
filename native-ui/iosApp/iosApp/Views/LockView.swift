import SwiftUI
import Shared

/// RF05/RF06/RF09: estado, bateria, online, abertura remota, volume (4 níveis) e histórico (10 + "Ver mais").
/// Qualquer comando físico passa por `confirmationDialog`; o modelo só é chamado no botão de confirmação.
struct LockView: View {
    private static let remoteHint = "Habilite a abertura remota no app Mibo Smart para comandar pela nuvem."

    @EnvironmentObject private var devices: DevicesModel
    @StateObject private var model: LockModel
    let ns: String

    /// Comando de porta aguardando confirmação: true = destrancar, false = trancar.
    @State private var pendingDoor: Bool?
    @State private var showDoorDialog = false
    /// Nível de volume aguardando confirmação.
    @State private var pendingVolume: LockVolume?
    @State private var showVolumeDialog = false

    init(ns: String) {
        self.ns = ns
        _model = StateObject(wrappedValue: LockModel(ns: ns))
    }

    var body: some View {
        List {
            stateSection
            commandSection
            volumeSection
            historySection
            if let lock = model.lock {
                DeviceInfoSection(device: lock, providerName: devices.providerName(lock))
            }
        }
        .scrollContentBackground(.hidden)
        .background(MiboColors.pageBg)
        .navigationTitle(model.lock?.name ?? "Fechadura")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button(action: { model.load() }) { Image(systemName: "arrow.clockwise") }
                    .disabled(model.loading || model.sending)
                    .accessibilityLabel("Atualizar")
            }
        }
        .onAppear { model.load() }
        .confirmationDialog(
            pendingDoor == true ? "Destrancar a fechadura?" : "Trancar a fechadura?",
            isPresented: $showDoorDialog,
            titleVisibility: .visible,
            presenting: pendingDoor
        ) { wantOpen in
            // Único caminho até `setLock`: o comando chega ao aparelho real pela nuvem.
            Button(wantOpen ? "Destrancar" : "Trancar", role: .destructive) { model.setLock(open: wantOpen) }
            Button("Cancelar", role: .cancel) {}
        } message: { _ in
            Text(commandNotice)
        }
        .confirmationDialog(
            "Alterar o volume para \(pendingVolume?.label ?? "")?",
            isPresented: $showVolumeDialog,
            titleVisibility: .visible,
            presenting: pendingVolume
        ) { level in
            Button("Confirmar") { model.changeVolume(level) }
            Button("Cancelar", role: .cancel) {}
        } message: { _ in
            Text(commandNotice)
        }
        .alert(model.notice ?? "", isPresented: noticeBinding) {
            Button("OK", role: .cancel) {}
        }
    }

    /// Deixa claro que a ação chega ao aparelho real pela nuvem.
    private var commandNotice: String { "O comando será enviado para \(model.lock?.name ?? "a fechadura") pela nuvem Intelbras." }

    private var noticeBinding: Binding<Bool> {
        Binding(get: { model.notice != nil }, set: { if !$0 { model.notice = nil } })
    }

    // MARK: Estado (só leitura)

    private var stateText: String {
        switch model.isOpen {
        case .some(true): return "Porta aberta"
        case .some(false): return "Porta fechada"
        case .none: return model.settled ? "Estado indisponível" : "Consultando…"
        }
    }

    private var stateSection: some View {
        Section {
            HStack(spacing: 16) {
                Image(systemName: model.isOpen == true ? "lock.open.fill" : "lock.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(model.isOpen == nil ? MiboColors.textSecondary : MiboColors.green)
                    .frame(width: 64)
                VStack(alignment: .leading, spacing: 4) {
                    Text(stateText).font(.title3.bold())
                    if model.loading {
                        HStack(spacing: 6) { ProgressView(); Text("Consultando a fechadura…") }
                            .font(.footnote).foregroundStyle(MiboColors.textSecondary)
                    }
                }
            }
            .padding(.vertical, 6)
            LabeledContent("Bateria") {
                HStack(spacing: 4) {
                    Image(systemName: batterySymbol)
                        .foregroundStyle(model.battery == nil ? MiboColors.textSecondary : (model.lowBattery ? MiboColors.amber : MiboColors.green))
                    Text(model.battery.map { "\($0)%" } ?? textPlaceholder)
                }
            }
            LabeledContent("Conexão") { OnlineBadge(online: model.online) }
            LabeledContent("Abertura remota", value: remoteLabel)
            if let error = model.loadError {
                Text(error.userMessage).font(.footnote).foregroundStyle(.red)
                Button("Tentar de novo") { model.load() }
            }
        }
    }

    /// Ícone proporcional ao nível (SF Symbols só tem 0/25/50/75/100); sem leitura, bateria vazia em cinza.
    private var batterySymbol: String {
        guard let battery = model.battery else { return "battery.0" }
        switch battery {
        case ..<13: return "battery.0"
        case ..<38: return "battery.25"
        case ..<63: return "battery.50"
        case ..<88: return "battery.75"
        default: return "battery.100"
        }
    }

    private var remoteLabel: String {
        switch model.remoteEnabled {
        case .some(true): return "Habilitada"
        case .some(false): return "Desabilitada"
        case .none: return textPlaceholder
        }
    }

    // MARK: Comando de porta (RF05) - só com confirmação

    private var canCommand: Bool { model.settled && model.remoteEnabled == true && model.isOpen != nil && !model.sending }

    private var commandSection: some View {
        Section("Comando") {
            Button(action: {
                guard let open = model.isOpen else { return }
                pendingDoor = !open // porta aberta -> trancar; fechada -> destrancar
                showDoorDialog = true
            }) {
                HStack(spacing: 8) {
                    if model.sending { ProgressView() }
                    Image(systemName: model.isOpen == true ? "lock.fill" : "lock.open.fill")
                    Text(model.isOpen == true ? "Trancar a porta" : "Destrancar a porta")
                }
            }
            .disabled(!canCommand)
            if model.remoteEnabled == false {
                Text(Self.remoteHint).font(.footnote).foregroundStyle(MiboColors.textSecondary)
            } else {
                Text("Pede confirmação antes de enviar o comando pela nuvem.")
                    .font(.footnote).foregroundStyle(MiboColors.textSecondary)
            }
        }
    }

    // MARK: Volume (RF06) - só com confirmação

    private var volumeSection: some View {
        Section("Volume") {
            // A leitura da fechadura de teste responde HTTP 500: os níveis ficam habilitados mesmo sem leitura,
            // e mudar o volume age no aparelho real, por isso passa por confirmação.
            HStack(spacing: 8) {
                ForEach(LockVolume.entries, id: \.self) { level in
                    let selected = model.volume == level
                    Button(action: {
                        guard !selected else { return }
                        pendingVolume = level
                        showVolumeDialog = true
                    }) {
                        Text(level.label)
                            .font(.subheadline)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(selected ? MiboColors.green : Color.white)
                            .foregroundStyle(selected ? Color.white : Color.primary)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: 0xE6E8EA)))
                    }
                    .buttonStyle(.plain)
                    .disabled(model.sending || model.loading)
                }
            }
            .padding(.vertical, 4)
            if model.volume == nil, model.settled {
                Text(model.volumeError.map { "Volume indisponível (\($0))" } ?? "Volume indisponível")
                    .font(.footnote).foregroundStyle(MiboColors.textSecondary)
            }
        }
    }

    // MARK: Histórico (RF09)

    private var historySection: some View {
        Section("Histórico de abertura") {
            if let error = model.historyError {
                Text("Histórico indisponível: \(error)").font(.footnote).foregroundStyle(.red)
                Button("Tentar de novo") { model.loadMoreHistory() }.disabled(model.historyLoading)
            } else if model.history.isEmpty {
                Text(model.settled ? "Ainda não há registros de abertura" : "Consultando…")
                    .foregroundStyle(MiboColors.textSecondary)
            } else {
                ForEach(Array(model.visibleHistory.enumerated()), id: \.offset) { _, event in
                    HStack {
                        // `description` colide com NSObject: o Kotlin/Native exporta como `description_`.
                        Text(event.description_)
                        Spacer()
                        Text(event.time).font(.footnote).foregroundStyle(MiboColors.textSecondary)
                    }
                }
                if model.canExpandHistory {
                    Button(action: { model.loadMoreHistory() }) {
                        HStack(spacing: 8) {
                            if model.historyLoading { ProgressView() }
                            Text("Ver mais")
                        }
                    }
                    .disabled(model.historyLoading)
                } else if model.historyExpanded {
                    Button("Ver menos") { model.collapseHistory() }
                }
            }
        }
    }
}

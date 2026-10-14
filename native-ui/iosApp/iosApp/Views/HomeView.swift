import SwiftUI
import Shared

/// RF02/RF07/RF08: lista com filtro de origem, paginação ("Carregar mais"), pull-to-refresh e os estados
/// carregando/vazio/erro. A navegação leva só o `ns` (String): a tela de destino busca o Device na fachada.
struct HomeView: View {
    @EnvironmentObject private var devices: DevicesModel
    @EnvironmentObject private var session: SessionModel

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Picker("Origem", selection: originBinding) {
                        // Enum Kotlin: `entries` chega como [OriginFilter]; NSObject já é Hashable para o tag.
                        ForEach(OriginFilter.entries, id: \.self) { filter in
                            Text(filter.label).tag(filter)
                        }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
                }
                content
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(MiboColors.pageBg)
            .refreshable { await devices.refreshAndWait() }
            .overlay(alignment: .top) {
                if (devices.state as? DevicesStateContent)?.refreshing == true {
                    ProgressView().progressViewStyle(.linear).tint(MiboColors.green)
                }
            }
            .navigationTitle("Minha casa")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) { pageSizeMenu }
                ToolbarItem(placement: .navigationBarTrailing) {
                    NavigationLink { SettingsView() } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel("Definições")
                }
            }
            .navigationDestination(for: String.self) { ns in DeviceDestination(ns: ns) }
        }
    }

    private var originBinding: Binding<OriginFilter> {
        Binding(get: { devices.query.origin }, set: { devices.setOrigin($0) })
    }

    private var pageSizeBinding: Binding<Int32> {
        Binding(get: { devices.query.pageSize }, set: { devices.setPageSize($0) })
    }

    /// RF08: 2 existe para ver "Carregar mais" funcionando na conta de teste (3 dispositivos).
    private var pageSizeMenu: some View {
        Menu {
            Picker("Itens por página", selection: pageSizeBinding) {
                ForEach(devices.pageSizes, id: \.self) { size in
                    Text("\(size) por página").tag(size)
                }
            }
        } label: {
            Image(systemName: "list.number")
        }
        .accessibilityLabel("Itens por página")
    }

    // MARK: Estados da listagem (sealed interface -> `as?` por classe)

    @ViewBuilder
    private var content: some View {
        let state = devices.state
        if state is DevicesStateLoading {
            Section {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("Consultando dispositivos da conta…").foregroundStyle(MiboColors.textSecondary)
                }
            }
        } else if let error = state as? DevicesStateError {
            Section { errorCard(error.error) }
        } else if let empty = state as? DevicesStateEmpty {
            Section { emptyCard(empty.query.origin) }
        } else if let content = state as? DevicesStateContent {
            Section("Meus dispositivos") {
                ForEach(content.devices, id: \.ns) { device in
                    NavigationLink(value: device.ns) { DeviceRow(device: device) }
                        // "row-LOCK", "row-HUB", "row-CAMERA": o UI test de leitura acha a linha pelo tipo, não pelo nome.
                        .accessibilityIdentifier("row-\(device.kind.name)")
                }
            }
            Section { paginationFooter(content) }
        }
        // DevicesStateNoToken não chega aqui: a RootView já trocou para a TokenView.
    }

    private func errorCard(_ error: AppError) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(error.userMessage)
            HStack {
                Button("Tentar novamente") { devices.refresh() }.buttonStyle(.borderedProminent)
                // Token recusado pela GDI: a saída é trocar o token, não insistir.
                if error is AppError.TokenInvalid || error is AppError.TokenExpired {
                    Button("Trocar token") { session.logout() }.buttonStyle(.bordered)
                }
            }
        }
        .padding(.vertical, 4)
    }

    /// Vazio com texto próprio por filtro: "vazio em Compartilhados" não é o mesmo que conta sem dispositivos.
    private func emptyCard(_ origin: OriginFilter) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Nenhum dispositivo encontrado").font(.headline)
            Text(emptyMessage(origin)).foregroundStyle(MiboColors.textSecondary)
            Button("Atualizar") { devices.refresh() }.buttonStyle(.bordered)
        }
        .padding(.vertical, 4)
    }

    private func emptyMessage(_ origin: OriginFilter) -> String {
        if origin == OriginFilter.linked { return "Nenhum dispositivo vinculado. Veja em Compartilhados ou Todos." }
        if origin == OriginFilter.shared { return "Nenhum dispositivo compartilhado com esta conta." }
        return "Esta conta não tem dispositivos vinculados nem compartilhados."
    }

    /// A GDI não devolve total: "Carregar mais" aparece enquanto a página vem cheia (`hasMore`).
    private func paginationFooter(_ content: DevicesStateContent) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if content.hasMore {
                Button(action: { devices.loadMore() }) {
                    HStack(spacing: 8) {
                        if content.loadingMore { ProgressView() }
                        Text("Carregar mais")
                    }
                }
                .disabled(content.loadingMore)
            } else {
                Text("Sem mais informações").foregroundStyle(MiboColors.textSecondary)
            }
            if let error = content.loadMoreError {
                Text(error.userMessage).font(.footnote).foregroundStyle(.red)
            }
            if let note = content.note {
                Text(note).font(.footnote).foregroundStyle(MiboColors.textSecondary)
            }
            Text("\(content.pagesLoaded) página(s) · \(content.devices.count) dispositivo(s) · \(content.query.pageSize) por página")
                .font(.caption)
                .foregroundStyle(MiboColors.textSecondary)
        }
    }
}

/// Linha da lista: ícone por tipo, nome, "modelo • via hub • compartilhado" e online/offline/"—".
struct DeviceRow: View {
    let device: Device

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: device.symbolName)
                .font(.title3)
                .foregroundStyle(MiboColors.green)
                .frame(width: 32)
            VStack(alignment: .leading, spacing: 2) {
                Text(device.name).font(.body)
                Text(device.subtitle).font(.footnote).foregroundStyle(MiboColors.textSecondary)
            }
            Spacer()
            OnlineBadge(online: device.isOnline)
        }
        .padding(.vertical, 2)
    }
}

struct OnlineBadge: View {
    let online: Bool?

    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(online == true ? MiboColors.onlineDot : Color(hex: 0xC8CCD0))
                .frame(width: 8, height: 8)
            Text(onlineLabel(online)).font(.caption).foregroundStyle(MiboColors.textSecondary)
        }
    }
}

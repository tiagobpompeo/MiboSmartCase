import SwiftUI
import Shared

/// Hub Zigbee: online, firmware e acessórios (subdispositivos da listagem via `sdk.children(hubNs:)`).
struct HubView: View {
    @EnvironmentObject private var devices: DevicesModel
    @StateObject private var model: HubModel
    let ns: String

    init(ns: String) {
        self.ns = ns
        _model = StateObject(wrappedValue: HubModel(ns: ns))
    }

    var body: some View {
        List {
            if let hub = model.hub {
                Section("Estado") {
                    HStack(spacing: 16) {
                        Image(systemName: "wifi.router.fill")
                            .font(.system(size: 40))
                            .foregroundStyle(model.online == true ? MiboColors.green : MiboColors.textSecondary)
                            .frame(width: 56)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(hub.model ?? hub.kind.label).font(.headline)
                            OnlineBadge(online: model.online)
                        }
                    }
                    .padding(.vertical, 6)
                }
                FirmwareSection(model: model, listedVersion: hub.version)
                accessoriesSection
                DeviceInfoSection(device: hub, providerName: devices.providerName(hub))
            }
        }
        .scrollContentBackground(.hidden)
        .background(MiboColors.pageBg)
        .navigationTitle(model.hub?.name ?? "Hub")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.load() }
    }

    private var accessoriesSection: some View {
        let children = model.children
        return Section("Dispositivos conectados ao hub (\(children.count))") {
            if children.isEmpty {
                Text("Nenhum subdispositivo").foregroundStyle(MiboColors.textSecondary)
            } else {
                ForEach(children, id: \.ns) { child in
                    NavigationLink(value: child.ns) { DeviceRow(device: child) }
                }
            }
            // Eventos em tempo real chegam por webhook e exigem backend: este app não os recebe.
            Text("Eventos em tempo real chegam por webhook e exigem backend.")
                .font(.footnote).foregroundStyle(MiboColors.textSecondary)
        }
    }
}

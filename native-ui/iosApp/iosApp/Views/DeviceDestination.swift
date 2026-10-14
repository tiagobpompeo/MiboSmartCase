import SwiftUI
import Shared

/// Destino da navegação: recebe só o `ns`, busca o Device na fachada e escolhe a tela pelo tipo.
/// Se o dispositivo saiu da lista (logout/refresh), mostra um aviso em vez de quebrar.
struct DeviceDestination: View {
    @EnvironmentObject private var devices: DevicesModel
    let ns: String

    var body: some View {
        if let device = devices.device(ns: ns) {
            if device.isCamera {
                CameraView(ns: ns)
            } else if device.isLock {
                LockView(ns: ns)
            } else if device.kind == DeviceKind.hub {
                HubView(ns: ns)
            } else {
                GenericDeviceView(ns: ns)
            }
        } else {
            VStack(spacing: 8) {
                Image(systemName: "questionmark.circle").font(.largeTitle).foregroundStyle(MiboColors.textSecondary)
                Text("Dispositivo não encontrado").foregroundStyle(MiboColors.textSecondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(MiboColors.pageBg)
        }
    }
}

/// Sensor, lâmpada e demais tipos: só online e firmware da listagem (a GDI não expõe mais nada para eles).
struct GenericDeviceView: View {
    @EnvironmentObject private var devices: DevicesModel
    @StateObject private var model: HubModel
    let ns: String

    init(ns: String) {
        self.ns = ns
        _model = StateObject(wrappedValue: HubModel(ns: ns))
    }

    var body: some View {
        List {
            if let device = model.hub {
                Section("Estado") {
                    LabeledContent("Conexão") { OnlineBadge(online: model.online) }
                }
                FirmwareSection(model: model, listedVersion: device.version)
                DeviceInfoSection(device: device, providerName: devices.providerName(device))
            }
        }
        .scrollContentBackground(.hidden)
        .background(MiboColors.pageBg)
        .navigationTitle(model.hub?.name ?? "Dispositivo")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.load() }
    }
}

/// Firmware consultado na hora (a listagem pode estar velha); enquanto não responde mostra a versão da listagem.
struct FirmwareSection: View {
    @ObservedObject var model: HubModel
    let listedVersion: String?

    var body: some View {
        Section("Firmware") {
            if let firmware = model.firmware {
                LabeledContent("Versão", value: firmware.version ?? textPlaceholder)
                if let status = firmware.statusLabel {
                    Text(status)
                        .font(.footnote)
                        .foregroundStyle(firmware.updateAvailable?.boolValue == true ? MiboColors.amber : MiboColors.green)
                }
            } else if let error = model.firmwareError {
                Text("Não foi possível consultar o firmware: \(error.userMessage)")
                    .font(.footnote)
                    .foregroundStyle(.red)
                Button("Tentar de novo") { model.loadFirmware() }
            } else {
                HStack(spacing: 12) {
                    if model.loadingFirmware { ProgressView() }
                    LabeledContent("Versão", value: listedVersion ?? textPlaceholder)
                }
            }
        }
    }
}

/// Dados de identificação comuns a todas as telas de detalhe (o `ns` é o número de série na GDI).
struct DeviceInfoSection: View {
    let device: Device
    let providerName: String?

    var body: some View {
        Section("Informações") {
            LabeledContent("Tipo", value: device.kind.label)
            LabeledContent("Modelo", value: device.model ?? textPlaceholder)
            LabeledContent("Número de série", value: device.ns)
            LabeledContent("Origem", value: device.origin.label)
            LabeledContent("Parceiro", value: providerName ?? device.providerId)
            if let lastOnline = device.lastOnline {
                LabeledContent("Último contato", value: lastOnline)
            }
        }
    }
}

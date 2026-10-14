import SwiftUI
import Shared

/// RF03: vídeo ao vivo com progresso, selo "AO VIVO", métrica "1º quadro em X s" e seletor de lente (só na linha Dual).
/// O player só existe com sessão; `onDisappear` encerra a sessão na GDI.
struct CameraView: View {
    @EnvironmentObject private var devices: DevicesModel
    @StateObject private var model: CameraModel
    let ns: String

    init(ns: String) {
        self.ns = ns
        _model = StateObject(wrappedValue: CameraModel(ns: ns))
    }

    var body: some View {
        // O vídeo fica FORA da List: dentro de uma célula (UICollectionView) o UIViewRepresentable do VLC fez a
        // célula parar de redesenhar os overlays no iPhone físico (selo "AO VIVO" não aparecia). Fixo no topo
        // também é o esperado de uma tela de câmera: a lista de detalhes rola por baixo.
        VStack(spacing: 0) {
            videoBox
                .padding(.horizontal, 16)
                .padding(.top, 8)
            List {
                if model.lensCount == 2 {
                Section("Lente") {
                    Picker("Lente", selection: lensBinding) {
                        ForEach(Array(CameraModel.lensLabels.enumerated()), id: \.offset) { index, label in
                            Text(label).tag(Int32(index))
                        }
                    }
                    .pickerStyle(.segmented)
                    if model.channel == CameraModel.fixedLensChannel {
                        Text(CameraModel.sameStreamNote).font(.footnote).foregroundStyle(MiboColors.textSecondary)
                    }
                }
            }
                Section("Fluxo") {
                    // A API real não devolve session_id: o fluxo é um RTSP do proxy da nuvem que vence sozinho.
                    LabeledContent("Origem", value: model.session?.sessionId == nil ? "RTSP da nuvem • expira automaticamente" : "Sessão GDI")
                    LabeledContent("Áudio", value: "Desativado (AAC sem config no SDP)")
                    if let ms = model.firstFrameMs {
                        LabeledContent("1º quadro", value: formatSeconds(ms))
                    }
                }
                if let camera = model.camera {
                    DeviceInfoSection(device: camera, providerName: devices.providerName(camera))
                }
            }
            .scrollContentBackground(.hidden)
        }
        .background(MiboColors.pageBg)
        .navigationTitle(model.camera?.name ?? "Câmera")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { model.start() }
        .onDisappear { model.stopLive() }
    }

    private var lensBinding: Binding<Int32> {
        Binding(get: { model.channel }, set: { model.switchLens($0) })
    }

    private var videoBox: some View {
        ZStack {
            MiboColors.videoBg
            if let session = model.session {
                VLCPlayerView(
                    url: session.streamUrl,
                    onProgress: { percent, label in model.onPlayerProgress(percent: percent, label: label) },
                    onFirstFrame: { model.onFirstFrame() },
                    onError: { message in model.onPlayerError(message) }
                )
            }
            if let error = model.error {
                errorOverlay(error)
            } else if model.loading {
                loadingOverlay.accessibilityIdentifier("camera-loading")
            }
            VStack {
                HStack {
                    videoTag("1/1") // um player por vez (cota e CPU): o contador do Mibo fica sempre em 1 de 1
                    Spacer()
                    if model.playing { liveChip.accessibilityIdentifier("camera-live") }
                }
                Spacer()
                HStack {
                    if let ms = model.firstFrameMs {
                        videoTag("1º quadro em \(formatSeconds(ms))", font: .caption2)
                    }
                    Spacer()
                }
            }
            .padding(12)
        }
        .aspectRatio(16 / 9, contentMode: .fit)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private var loadingOverlay: some View {
        VStack(spacing: 8) {
            ProgressView().tint(.white).scaleEffect(1.4)
            Text("\(model.percent)%").font(.title3).foregroundStyle(.white)
            Text(model.label).font(.caption).foregroundStyle(Color(white: 0.85))
        }
    }

    private func errorOverlay(_ message: String) -> some View {
        VStack(spacing: 8) {
            Text(message)
                .font(.footnote)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .lineLimit(3)
            Button("Tentar novamente") { model.retry() }
                .buttonStyle(.borderedProminent)
        }
        .padding(12)
        .background(Color.black.opacity(0.7))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .padding(.horizontal, 32)
    }

    private var liveChip: some View {
        HStack(spacing: 4) {
            Image(systemName: "dot.radiowaves.left.and.right").font(.caption2)
            Text("AO VIVO").font(.caption2.bold())
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.black.opacity(0.5))
        .foregroundStyle(.white)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    private func videoTag(_ text: String, font: Font = .footnote) -> some View {
        Text(text)
            .font(font)
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(Color.black.opacity(0.6))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

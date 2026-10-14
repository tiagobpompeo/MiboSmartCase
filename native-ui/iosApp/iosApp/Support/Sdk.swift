import Shared

/// Acesso à fachada Kotlin. `CasaSdk` é um `single` no Koin, então `casaSdk()` devolve sempre a mesma instância;
/// guardar aqui só evita repetir a busca no grafo a cada modelo. Precisa de `KoinKt.doInitKoin()` antes (iOSApp.init).
enum Sdk {
    static let shared: CasaSdk = CasaSdkKt.casaSdk()
}

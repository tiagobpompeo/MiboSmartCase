package br.com.pompeo.casa.domain

/** Erros que a UI sabe explicar (RF04). [userMessage] é o texto exibido; [detail] é técnico (diagnóstico). */
sealed class AppError(val userMessage: String, val detail: String? = null) {
    data object TokenMissing : AppError("Informe o token de acesso gerado no portal Casa Inteligente.")

    class TokenInvalid(detail: String?) :
        AppError("Token inválido ou expirado. Gere um novo token no portal (Contas → Token Temporário) e tente de novo.", detail)

    /** A GDI distingue expirado (403) de inválido (401); a UI oferece "Trocar token" nos dois. */
    class TokenExpired(detail: String?) :
        AppError("Seu token expirou (ele vale cerca de 2 horas). Gere um novo no portal (Contas → Token Temporário) e tente de novo.", detail)

    class Network(detail: String?) : AppError("Sem conexão com a internet. Verifique a rede e tente novamente.", detail)

    class Timeout(detail: String?) : AppError("A plataforma Intelbras demorou a responder. Tente novamente.", detail)

    class Server(val httpStatus: Int, detail: String?) :
        AppError("A plataforma Intelbras respondeu com erro (HTTP $httpStatus). Tente novamente em instantes.", detail)

    class Unexpected(detail: String?) : AppError("Algo deu errado. Tente novamente.", detail)

    override fun toString(): String = "${this::class.simpleName}(${detail ?: ""})"
}

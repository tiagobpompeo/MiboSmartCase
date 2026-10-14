package br.com.pompeo.casa.domain

/**
 * Nome do CoroutineScope que vive enquanto o app vive, registrado no Koin por :core:data e pedido pelas
 * features para trabalhos que não podem morrer com uma tela: comando físico na fechadura e encerramento da
 * sessão de vídeo ao sair da câmera. É só uma String, para o domain continuar sem dependência de Koin.
 */
const val APP_SCOPE_NAME = "appScope"

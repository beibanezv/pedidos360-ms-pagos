package com.pedidos360.pagos;

/** Frontera del SDK de Transbank: permite probar el flujo sin llamar a Webpay. */
interface WebpayClient {
  Crear crear(String buyOrder, String sessionId, long amount, String returnUrl);

  Confirmar confirmar(String token);

  record Crear(String token, String url) {}

  record Confirmar(String status, String buyOrder, String sessionId, long amount) {}
}

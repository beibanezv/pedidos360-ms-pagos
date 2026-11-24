package com.pedidos360.pagos;

import cl.transbank.common.IntegrationApiKeys;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Implementacion real con el SDK 6.2.1 de Transbank, en modo integracion. */
@Component
class TransbankWebpayClient implements WebpayClient {
  private final WebpayPlus.Transaction transaction;

  TransbankWebpayClient(
      @Value("${webpay.commerce-code:597055555532}") String commerceCode,
      @Value("${webpay.api-key:}") String apiKey) {
    this.transaction = WebpayPlus.Transaction.buildForIntegration(
        commerceCode, apiKey.isBlank() ? IntegrationApiKeys.WEBPAY : apiKey);
  }

  @Override
  public Crear crear(String buyOrder, String sessionId, long amount, String returnUrl) {
    try {
      var respuesta = transaction.create(buyOrder, sessionId, amount, returnUrl);
      return new Crear(respuesta.getToken(), respuesta.getUrl());
    } catch (Exception error) {
      throw new IllegalStateException("No se pudo iniciar la transaccion con Webpay", error);
    }
  }

  @Override
  public Confirmar confirmar(String token) {
    try {
      var respuesta = transaction.commit(token);
      return new Confirmar(respuesta.getStatus(), respuesta.getBuyOrder(), respuesta.getSessionId(),
          Math.round(respuesta.getAmount()));
    } catch (Exception error) {
      throw new IllegalStateException("No se pudo confirmar la transaccion con Webpay", error);
    }
  }
}

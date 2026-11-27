package com.pedidos360.pagos.messaging;

/** SIMPLIFICADO: literales de M06a/C4; migrar a constantes centralizadas cuando se unifiquen los repos. */
public final class PagoTopologia {

  public static final String EVENTOS_EXCHANGE = "p360.eventos";
  public static final String PAGO_RESUELTO_ROUTING_KEY = "pago.resuelto";

  private PagoTopologia() {}
}

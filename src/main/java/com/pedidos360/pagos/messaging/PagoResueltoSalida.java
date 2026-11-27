package com.pedidos360.pagos.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Salida Rabbit pendiente: la PK es el eventoId estable, un reenvío no crea otra fila. */
@Entity
@Table(name = "pagos_outbox_rabbit", indexes = {
    @Index(name = "idx_pagos_rabbit_enviado", columnList = "enviado_en,ocurrio_en")})
public class PagoResueltoSalida {

  @Id private UUID eventoId;
  @Column(nullable = false) private UUID intentoId;
  @Column(nullable = false) private UUID ordenId;
  @Column(nullable = false) private String usuarioId;
  @Column(nullable = false) private String resultado;
  @Column(nullable = false) private Instant ocurrioEn;
  private Instant enviadoEn;

  protected PagoResueltoSalida() {}

  public PagoResueltoSalida(UUID eventoId, UUID intentoId, UUID ordenId, String usuarioId,
      String resultado, Instant ocurrioEn) {
    this.eventoId = eventoId;
    this.intentoId = intentoId;
    this.ordenId = ordenId;
    this.usuarioId = usuarioId;
    this.resultado = resultado;
    this.ocurrioEn = ocurrioEn;
  }

  public UUID getEventoId() { return eventoId; }
  public UUID getIntentoId() { return intentoId; }
  public UUID getOrdenId() { return ordenId; }
  public String getUsuarioId() { return usuarioId; }
  public String getResultado() { return resultado; }
  public Instant getOcurrioEn() { return ocurrioEn; }
  public Instant getEnviadoEn() { return enviadoEn; }
}

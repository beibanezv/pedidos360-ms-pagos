package com.pedidos360.pagos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagos_intentos", indexes = {
    @Index(name = "idx_pagos_tarjeta_fecha", columnList = "tarjeta,ocurrio_en"),
    @Index(name = "idx_pagos_orden_usuario", columnList = "orden_id,usuario_id"),
    @Index(name = "idx_pagos_outbox", columnList = "publicado_en,ocurrio_en")})
class Pago {
  @Id private UUID id;
  @Column(nullable = false) private UUID ordenId;
  @Column(nullable = false) private String usuarioId;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PagoService.TarjetaDemo tarjeta;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PagoService.Resultado resultado;
  @Column(nullable = false) private long totalClp;
  @Column(nullable = false) private Instant ocurrioEn;
  private Instant publicadoEn;

  protected Pago() {}

  Pago(String usuarioId, UUID ordenId, long totalClp, PagoService.TarjetaDemo tarjeta,
      PagoService.Resultado resultado, Instant ocurrioEn) {
    this.id = UUID.randomUUID();
    this.usuarioId = usuarioId;
    this.ordenId = ordenId;
    this.totalClp = totalClp;
    this.tarjeta = tarjeta;
    this.resultado = resultado;
    this.ocurrioEn = ocurrioEn;
  }

  UUID getId() { return id; }
  PagoService.Resultado getResultado() { return resultado; }

  PagoEvent evento() {
    return new PagoEvent(id, usuarioId, ordenId, resultado, totalClp, ocurrioEn);
  }
}

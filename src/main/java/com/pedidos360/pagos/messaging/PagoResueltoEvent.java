package com.pedidos360.pagos.messaging;

import java.time.Instant;
import java.util.UUID;

/** Espejo local del evento de seis campos acordado en G0 (orders consume su propia copia). */
public record PagoResueltoEvent(UUID eventoId, UUID intentoId, UUID ordenId, String usuarioId,
                                String resultado, Instant ocurrioEn) {}

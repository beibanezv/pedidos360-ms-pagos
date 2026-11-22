package com.pedidos360.pagos;

import java.time.Instant;
import java.util.UUID;

record PagoEvent(UUID eventoId, String usuarioId, UUID ordenId, PagoService.Resultado resultado,
                 long totalClp, Instant ocurrioEn) {}

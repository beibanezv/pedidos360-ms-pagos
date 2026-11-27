package com.pedidos360.pagos;

import com.pedidos360.pagos.messaging.PagoResueltoSalida;
import com.pedidos360.pagos.messaging.PagoResueltoSalidaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Registra la salida Rabbit en la misma transacción que el resultado definitivo del pago. */
@Service
class PagoResueltoSalidaService {

  private final PagoResueltoSalidaRepository repository;
  private final Clock clock;

  PagoResueltoSalidaService(PagoResueltoSalidaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  static UUID eventoId(UUID intentoId) {
    return UUID.nameUUIDFromBytes(("pago.resuelto:" + intentoId).getBytes(StandardCharsets.UTF_8));
  }

  PagoResueltoSalida registrar(Pago pago) {
    var salida = new PagoResueltoSalida(eventoId(pago.getId()), pago.getId(), pago.getOrdenId(),
        pago.getUsuarioId(), pago.getResultado().name(), clock.instant());
    return repository.save(salida);
  }
}

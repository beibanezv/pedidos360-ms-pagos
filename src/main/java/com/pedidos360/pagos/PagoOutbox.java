package com.pedidos360.pagos;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PagoOutbox {
  private static final Logger log = LoggerFactory.getLogger(PagoOutbox.class);
  private final PagoRepository repository;
  private final KafkaTemplate<String, PagoEvent> kafka;
  private final Clock clock;

  PagoOutbox(PagoRepository repository, KafkaTemplate<String, PagoEvent> kafka, Clock clock) {
    this.repository = repository;
    this.kafka = kafka;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 1500)
  void enviarPendientes() {
    for (Pago pago : repository.findTop20ByPublicadoEnIsNullOrderByOcurrioEnAscIdAsc()) {
      // Un pago iniciado en Webpay aún no tiene resultado: se publica solo tras el commit.
      if (pago.getResultado() == PagoService.Resultado.PENDIENTE) continue;
      var evento = pago.evento();
      try {
        kafka.send("p360.pagos.v1", evento.usuarioId(), evento).get(13, TimeUnit.SECONDS);
        repository.marcarPublicado(evento.eventoId(), clock.instant());
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception error) {
        // Sin ACK la fila sigue pendiente; el consumidor deduplica si se pierde el ACK.
        log.warn("Pago {} pendiente de Kafka: {}", evento.eventoId(), error.getClass().getSimpleName());
        return;
      }
    }
  }
}

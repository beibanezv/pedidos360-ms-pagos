package com.pedidos360.pagos.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publica la salida persistida y marca enviado_en solo con confirmación positiva. */
@Component
public class PagoResueltoOutbox {

  private static final Logger log = LoggerFactory.getLogger(PagoResueltoOutbox.class);

  private final RabbitTemplate rabbit;
  private final PagoResueltoSalidaRepository repository;
  private final ObjectMapper objectMapper;

  public PagoResueltoOutbox(RabbitTemplate rabbit, PagoResueltoSalidaRepository repository,
      ObjectMapper objectMapper) {
    this.rabbit = rabbit;
    this.repository = repository;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedDelayString = "${p360.pagos.rabbit.outbox.delay-ms:2000}",
      initialDelayString = "${p360.pagos.rabbit.outbox.delay-ms:2000}")
  public void publicarPendientes() {
    for (PagoResueltoSalida salida : repository.findTop20ByEnviadoEnIsNullOrderByOcurrioEnAscEventoIdAsc()) {
      enviar(salida);
    }
  }

  private void enviar(PagoResueltoSalida salida) {
    try {
      // JSON crudo sin __TypeId__: orders deserializa con su propia copia del DTO de seis campos.
      var evento = new PagoResueltoEvent(salida.getEventoId(), salida.getIntentoId(), salida.getOrdenId(),
          salida.getUsuarioId(), salida.getResultado(), salida.getOcurrioEn());
      var mensaje = MessageBuilder.withBody(objectMapper.writeValueAsBytes(evento))
          .setContentType("application/json").build();
      var correlacion = new CorrelationData(salida.getEventoId().toString());
      rabbit.send(PagoTopologia.EVENTOS_EXCHANGE, PagoTopologia.PAGO_RESUELTO_ROUTING_KEY, mensaje, correlacion);
      Confirm confirm = correlacion.getFuture().get(5, TimeUnit.SECONDS);
      if (confirm != null && confirm.isAck()) {
        repository.marcarEnviado(salida.getEventoId(), Instant.now());
      }
    } catch (AmqpException | JsonProcessingException | InterruptedException
             | ExecutionException | TimeoutException error) {
      if (error instanceof InterruptedException) Thread.currentThread().interrupt();
      log.warn("Salida {} pendiente de Rabbit: {}", salida.getEventoId(), error.getClass().getSimpleName());
    }
  }
}

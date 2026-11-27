package com.pedidos360.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.pagos.messaging.PagoResueltoOutbox;
import com.pedidos360.pagos.messaging.PagoResueltoSalida;
import com.pedidos360.pagos.messaging.PagoResueltoSalidaRepository;
import com.pedidos360.pagos.messaging.PagoTopologia;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class PagoResueltoOutboxTest {

  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final PagoResueltoSalidaRepository repository = mock(PagoResueltoSalidaRepository.class);
  private PagoResueltoOutbox relay;

  @BeforeEach
  void setUp() {
    relay = new PagoResueltoOutbox(rabbit, repository, new ObjectMapper().findAndRegisterModules());
  }

  private PagoResueltoSalida salida() {
    return new PagoResueltoSalida(UUID.nameUUIDFromBytes("evento".getBytes(StandardCharsets.UTF_8)),
        UUID.randomUUID(), UUID.randomUUID(), "cliente", "APROBADO",
        Instant.parse("2026-11-27T12:00:00Z"));
  }

  private void pendiente(PagoResueltoSalida salida) {
    when(repository.findTop20ByEnviadoEnIsNullOrderByOcurrioEnAscEventoIdAsc()).thenReturn(List.of(salida));
  }

  private void confirmar(boolean ack) {
    doAnswer(inv -> {
      CorrelationData correlacion = inv.getArgument(3);
      correlacion.getFuture().complete(new CorrelationData.Confirm(ack, null));
      return null;
    }).when(rabbit).send(eq(PagoTopologia.EVENTOS_EXCHANGE),
        eq(PagoTopologia.PAGO_RESUELTO_ROUTING_KEY), any(Message.class), any(CorrelationData.class));
  }

  @Test
  void publicaSeisCamposSinTypeIdYMarcaSoloConAck() {
    PagoResueltoSalida salida = salida();
    pendiente(salida);
    confirmar(true);

    relay.publicarPendientes();

    ArgumentCaptor<Message> mensaje = ArgumentCaptor.forClass(Message.class);
    ArgumentCaptor<CorrelationData> correlacion = ArgumentCaptor.forClass(CorrelationData.class);
    verify(rabbit).send(eq(PagoTopologia.EVENTOS_EXCHANGE), eq(PagoTopologia.PAGO_RESUELTO_ROUTING_KEY),
        mensaje.capture(), correlacion.capture());
    String json = new String(mensaje.getValue().getBody(), StandardCharsets.UTF_8);
    assertThat(json).contains(salida.getEventoId().toString(), salida.getIntentoId().toString(),
        salida.getOrdenId().toString(), "cliente", "APROBADO")
        .doesNotContain("__TypeId__", "tokenWs", "buyOrder", "email", "pan", "cvv");
    assertThat(mensaje.getValue().getMessageProperties().getContentType()).isEqualTo("application/json");
    assertThat(correlacion.getValue().getId()).isEqualTo(salida.getEventoId().toString());
    verify(repository).marcarEnviado(eq(salida.getEventoId()), any(Instant.class));
  }

  @Test
  void sinConfirmacionNoMarcaEnviado() {
    PagoResueltoSalida salida = salida();
    pendiente(salida);
    confirmar(false);

    relay.publicarPendientes();

    verify(repository, never()).marcarEnviado(any(), any());
  }
}

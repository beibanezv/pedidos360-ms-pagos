package com.pedidos360.pagos;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class PagoOutboxTest {
  @Test
  void noPublicaPagosPendientesDeWebpay() {
    PagoRepository repository = mock(PagoRepository.class);
    @SuppressWarnings("unchecked") KafkaTemplate<String, PagoEvent> kafka = mock(KafkaTemplate.class);
    var pendiente = new Pago("cliente", UUID.randomUUID(), 18990L, PagoService.TarjetaDemo.WEBPAY,
        PagoService.Resultado.PENDIENTE, Clock.systemUTC().instant());
    when(repository.findTop20ByPublicadoEnIsNullOrderByOcurrioEnAscIdAsc()).thenReturn(List.of(pendiente));

    new PagoOutbox(repository, kafka, Clock.systemUTC()).enviarPendientes();

    verifyNoInteractions(kafka);
  }
}

package com.pedidos360.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = "spring.datasource.url=${P360_TEST_POSTGRES_URL}")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PagoService.class, PagoServicePostgresTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "P360_TEST_POSTGRES_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "P360_TEST_KAFKA", matches = "true")
class PagoOutboxKafkaTest {
  @Autowired PagoRepository repository;
  @Autowired PagoService service;
  @Value("${spring.kafka.bootstrap-servers}") String bootstrap;

  @Test
  void conservaPendienteSinAckYPublicaUnEventoSinTarjetaAlReconectar() {
    repository.deleteAllInBatch();
    var topic = "p360.pagos.v1";
    var usuario = UUID.randomUUID().toString();
    var orden = UUID.randomUUID();
    var reloj = Clock.systemUTC();
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, PagoEvent> desconectado = mock(KafkaTemplate.class);
    when(desconectado.send(eq(topic), eq(usuario), any(PagoEvent.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka no disponible")));

    var factory = new DefaultKafkaProducerFactory<>(Map.of(
        "bootstrap.servers", bootstrap, "acks", "all", "max.block.ms", 7000,
        "spring.json.add.type.headers", false), new StringSerializer(), new JsonSerializer<PagoEvent>());
    try (var reader = new KafkaConsumer<>(Map.of("bootstrap.servers", bootstrap),
        new StringDeserializer(), new StringDeserializer())) {
      var particiones = reader.partitionsFor(topic, Duration.ofSeconds(8)).stream()
          .map(p -> new TopicPartition(topic, p.partition())).toList();
      reader.assign(particiones);
      reader.seekToEnd(particiones);
      for (var p : particiones) reader.position(p, Duration.ofSeconds(8));

      var pago = service.registrar(usuario, orden, 18990, PagoService.TarjetaDemo.RECHAZO);
      new PagoOutbox(repository, desconectado, reloj).enviarPendientes();
      assertThat(repository.findTop20ByPublicadoEnIsNullOrderByOcurrioEnAscIdAsc()).hasSize(1);
      new PagoOutbox(repository, new KafkaTemplate<>(factory), reloj).enviarPendientes();
      assertThat(repository.findTop20ByPublicadoEnIsNullOrderByOcurrioEnAscIdAsc()).isEmpty();

      String recibido = null;
      for (int i = 0; i < 6 && recibido == null; i++) {
        for (var registro : reader.poll(Duration.ofSeconds(1))) {
          if (usuario.equals(registro.key())) recibido = registro.value();
        }
      }
      assertThat(recibido).contains(pago.getId().toString(), orden.toString(), "RECHAZADO")
          .doesNotContain("tarjeta", "cvv", "pan");
    } finally {
      factory.destroy();
    }
  }
}

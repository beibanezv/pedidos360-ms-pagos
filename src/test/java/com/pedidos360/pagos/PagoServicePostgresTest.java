package com.pedidos360.pagos;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PagoService.class, PagoServicePostgresTest.FixedClock.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIfEnvironmentVariable(named = "P360_TEST_POSTGRES_URL", matches = ".+")
@TestPropertySource(properties = "spring.datasource.url=${P360_TEST_POSTGRES_URL}")
class PagoServicePostgresTest {
  @Autowired PagoService service;
  @Autowired PagoRepository repository;

  @Test
  void cuatroIntentosConcurrentesSoloPermitenTres() throws Exception {
    repository.deleteAllInBatch();
    var start = new CountDownLatch(1);
    var tasks = new ArrayList<Future<PagoService.Resultado>>();
    var pool = Executors.newFixedThreadPool(4);
    try {
      for (int i = 0; i < 4; i++) {
        tasks.add(pool.submit(() -> {
          start.await();
          return service.registrar("comprador", UUID.randomUUID(), 18990L, PagoService.TarjetaDemo.RECHAZO)
              .getResultado();
        }));
      }
      start.countDown();
      var resultados = new ArrayList<PagoService.Resultado>();
      for (var task : tasks) resultados.add(task.get(10, TimeUnit.SECONDS));
      assertThat(resultados).containsExactlyInAnyOrder(
          PagoService.Resultado.RECHAZADO, PagoService.Resultado.RECHAZADO,
          PagoService.Resultado.RECHAZADO, PagoService.Resultado.BLOQUEADO);
      assertThat(repository.count()).isEqualTo(4);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void reintentarUnaOrdenPagadaNoDuplicaCobro() {
    repository.deleteAllInBatch();
    UUID id = UUID.randomUUID();
    var primero = service.registrar("comprador", id, 18990L, PagoService.TarjetaDemo.APROBACION);
    var repetido = service.registrar("comprador", id, 18990L, PagoService.TarjetaDemo.APROBACION);
    assertThat(repetido.getId()).isEqualTo(primero.getId());
    assertThat(repository.count()).isEqualTo(1);
  }

  @TestConfiguration
  static class FixedClock {
    @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC); }
  }
}

package com.pedidos360.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class PagoServiceWebpayTest {
  private final PagoRepository repository = mock(PagoRepository.class);
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-24T12:00:00Z"), ZoneOffset.UTC);
  private final PagoService service = new PagoService(repository, jdbc, clock);

  private void sinBloqueo() {
    when(repository.findFirstByOrdenIdAndUsuarioIdAndResultado(any(), any(), any())).thenReturn(Optional.empty());
    when(repository.countByTarjetaAndOcurrioEnAfterAndResultadoNot(any(), any(), any())).thenReturn(0L);
    when(repository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void guardaPagoPendienteConElTokenDeWebpay() {
    sinBloqueo();
    UUID orden = UUID.randomUUID();

    var pago = service.iniciarWebpay("cliente", orden, 18990L, "token-abc", "TX1234567890abcdef");

    assertThat(pago.getResultado()).isEqualTo(PagoService.Resultado.PENDIENTE);
    assertThat(pago.getTokenWebpay()).isEqualTo("token-abc");
    assertThat(pago.getBuyOrder()).isEqualTo("TX1234567890abcdef");
  }

  @Test
  void bloqueaAlCuartoIntentoEnDiezSegundos() {
    UUID orden = UUID.randomUUID();
    when(repository.countByTarjetaAndOcurrioEnAfterAndResultadoNot(any(), any(), any())).thenReturn(3L);

    assertThatThrownBy(() -> service.verificar("cliente", orden, 18990L))
        .isInstanceOf(ResponseStatusException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void respuestaIncoherenteNoCierraElPago() {
    UUID orden = UUID.randomUUID();
    var pendiente = new Pago("cliente", orden, 18990L, PagoService.TarjetaDemo.WEBPAY,
        PagoService.Resultado.PENDIENTE, clock.instant());
    pendiente.setTokenWebpay("token-abc");
    pendiente.setBuyOrder(orden.toString());
    when(repository.findFirstByTokenWebpay("token-abc")).thenReturn(Optional.of(pendiente));
    when(repository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var pago = service.confirmarWebpay("token-abc",
        new WebpayClient.Confirmar("AUTHORIZED", UUID.randomUUID().toString(), "cliente", 18990L));

    // G0 §7.3: discordancia con AUTHORIZED no es un rechazo verificado.
    assertThat(pago.getResultado()).isEqualTo(PagoService.Resultado.PENDIENTE);
  }

  @Test
  void inicializadaNoEsResultadoDefinitivo() {
    UUID orden = UUID.randomUUID();
    var pendiente = new Pago("cliente", orden, 18990L, PagoService.TarjetaDemo.WEBPAY,
        PagoService.Resultado.PENDIENTE, clock.instant());
    pendiente.setTokenWebpay("token-abc");
    pendiente.setBuyOrder(orden.toString());
    when(repository.findFirstByTokenWebpay("token-abc")).thenReturn(Optional.of(pendiente));
    when(repository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var pago = service.confirmarWebpay("token-abc",
        new WebpayClient.Confirmar("INITIALIZED", orden.toString(), "cliente", 18990L));

    assertThat(pago.getResultado()).isEqualTo(PagoService.Resultado.PENDIENTE);
  }

  @Test
  void timeoutDelProveedorConservaPendiente() {
    UUID orden = UUID.randomUUID();
    var pendiente = new Pago("cliente", orden, 18990L, PagoService.TarjetaDemo.WEBPAY,
        PagoService.Resultado.PENDIENTE, clock.instant());
    pendiente.setTokenWebpay("token-abc");
    pendiente.setBuyOrder("TX123");
    when(repository.findFirstByTokenWebpay("token-abc")).thenReturn(Optional.of(pendiente));
    when(repository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.fallarWebpay("token-abc");

    // G0 caso 10: un error de red no demuestra que Transbank rechazó; sin salida Rabbit/Kafka.
    assertThat(pendiente.getResultado()).isEqualTo(PagoService.Resultado.PENDIENTE);
  }

  @Test
  void confirmaRespuestaCoherente() {
    UUID orden = UUID.randomUUID();
    var pendiente = new Pago("cliente", orden, 18990L, PagoService.TarjetaDemo.WEBPAY,
        PagoService.Resultado.PENDIENTE, clock.instant());
    pendiente.setTokenWebpay("token-abc");
    pendiente.setBuyOrder(orden.toString());
    when(repository.findFirstByTokenWebpay("token-abc")).thenReturn(Optional.of(pendiente));
    when(repository.save(any(Pago.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var pago = service.confirmarWebpay("token-abc",
        new WebpayClient.Confirmar("AUTHORIZED", orden.toString(), "cliente", 18990L));

    assertThat(pago.getResultado()).isEqualTo(PagoService.Resultado.APROBADO);
  }
}

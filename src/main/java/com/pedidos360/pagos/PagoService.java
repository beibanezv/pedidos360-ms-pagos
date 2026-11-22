package com.pedidos360.pagos;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PagoService {
  enum TarjetaDemo { APROBACION, RECHAZO }
  enum Resultado { APROBADO, RECHAZADO, BLOQUEADO }

  private static final Logger log = LoggerFactory.getLogger(PagoService.class);
  private final PagoRepository repository;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  PagoService(PagoRepository repository, JdbcTemplate jdbc, Clock clock) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional
  public Pago registrar(String usuario, UUID orden, long total, TarjetaDemo tarjeta) {
    if (total <= 0) throw new IllegalArgumentException("El total debe ser positivo");
    // Los dos espacios de llaves no se solapan; cada réplica mantiene el mismo orden de bloqueo.
    bloquear(360, orden.hashCode());
    var pagado = repository.findFirstByOrdenIdAndUsuarioIdAndResultado(orden, usuario, Resultado.APROBADO);
    if (pagado.isPresent()) return pagado.get();
    bloquear(361, tarjeta.ordinal());
    Instant ahora = clock.instant();
    boolean bloqueado = repository.countByTarjetaAndOcurrioEnAfterAndResultadoNot(
        tarjeta, ahora.minusSeconds(10), Resultado.BLOQUEADO) >= 3;
    Resultado resultado = bloqueado ? Resultado.BLOQUEADO
        : tarjeta == TarjetaDemo.APROBACION ? Resultado.APROBADO : Resultado.RECHAZADO;
    var pago = repository.save(new Pago(usuario, orden, total, tarjeta, resultado, ahora));
    if (bloqueado) log.warn("Alerta antifraude: cuarto intento en 10 s, pago={}, orden={}", pago.getId(), orden);
    return pago;
  }

  private void bloquear(int dominio, int clave) {
    jdbc.query("select pg_advisory_xact_lock(?, ?)", rs -> {}, dominio, clave);
  }
}

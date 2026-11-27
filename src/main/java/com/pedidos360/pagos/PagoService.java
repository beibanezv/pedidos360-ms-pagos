package com.pedidos360.pagos;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PagoService {
  enum TarjetaDemo { APROBACION, RECHAZO, WEBPAY }
  enum Resultado { APROBADO, RECHAZADO, BLOQUEADO, PENDIENTE }

  private static final Logger log = LoggerFactory.getLogger(PagoService.class);
  private final PagoRepository repository;
  private final JdbcTemplate jdbc;
  private final Clock clock;
  private final PagoResueltoSalidaService salidas;

  PagoService(PagoRepository repository, JdbcTemplate jdbc, Clock clock, PagoResueltoSalidaService salidas) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.clock = clock;
    this.salidas = salidas;
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

  /** Antifraude del simulador, reutilizado para Webpay antes de crear la transaccion. */
  @Transactional
  public void verificar(String usuario, UUID orden, long total) {
    if (total <= 0) throw new IllegalArgumentException("El total debe ser positivo");
    bloquear(360, orden.hashCode());
    if (repository.findFirstByOrdenIdAndUsuarioIdAndResultado(orden, usuario, Resultado.APROBADO).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden ya está pagada");
    }
    if (repository.countByTarjetaAndOcurrioEnAfterAndResultadoNot(
        TarjetaDemo.WEBPAY, clock.instant().minusSeconds(10), Resultado.BLOQUEADO) >= 3) {
      throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
          "Demasiados intentos de pago. Espera unos segundos antes de volver a intentar.");
    }
  }

  /** Guarda el pago como PENDIENTE: aún no debe publicarse a Kafka ni contar como aprobado. */
  @Transactional
  public Pago iniciarWebpay(String usuario, UUID orden, long total, String tokenWebpay, String buyOrder) {
    verificar(usuario, orden, total);
    bloquear(361, TarjetaDemo.WEBPAY.ordinal());
    var pago = new Pago(usuario, orden, total, TarjetaDemo.WEBPAY, Resultado.PENDIENTE, clock.instant());
    pago.setTokenWebpay(tokenWebpay);
    pago.setBuyOrder(buyOrder);
    return repository.save(pago);
  }

  /** PRE_CLAIM: se persiste antes de pedir el claim; sin JWT guardado ni reanudación automática. */
  @Transactional
  public Pago preparar(UUID intentoId, String usuario, UUID orden) {
    var pago = new Pago(intentoId, usuario, orden, 0L, TarjetaDemo.WEBPAY, Resultado.PENDIENTE, clock.instant());
    pago.setFase("PRE_CLAIM");
    return repository.save(pago);
  }

  @Transactional
  public void claimada(UUID intentoId, long total) {
    repository.findById(intentoId).ifPresent(pago -> {
      pago.setTotalClp(total);
      pago.setFase("CLAIMADA");
      repository.save(pago);
    });
  }

  @Transactional
  public void creando(UUID intentoId) {
    repository.findById(intentoId).ifPresent(pago -> {
      pago.setFase("CREANDO");
      repository.save(pago);
    });
  }

  @Transactional
  public void creada(UUID intentoId, String tokenWebpay, String buyOrder) {
    repository.findById(intentoId).ifPresent(pago -> {
      pago.setFase("CREADA");
      pago.setTokenWebpay(tokenWebpay);
      pago.setBuyOrder(buyOrder);
      repository.save(pago);
    });
  }

  /** Cierra el ciclo del callback de Webpay comprobando que la respuesta corresponda al pago guardado. */
  @Transactional
  public Pago confirmarWebpay(String token, WebpayClient.Confirmar respuesta) {
    var pago = repository.findFirstByTokenWebpay(token)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transacción Webpay no encontrada"));
    if (pago.getResultado() != Resultado.PENDIENTE) return pago;
    boolean coherente = pago.getBuyOrder().equals(respuesta.buyOrder())
        && pago.getUsuarioId().equals(respuesta.sessionId())
        && pago.getTotalClp() == respuesta.amount();
    boolean autorizadoIncoherente = "AUTHORIZED".equals(respuesta.status()) && respuesta.responseCode() != 0;
    if (!coherente || "INITIALIZED".equals(respuesta.status()) || autorizadoIncoherente) {
      // G0 §7.3: incoherencia, resultado abierto o código negativo con AUTHORIZED no cierran el pago.
      log.warn("Respuesta de Webpay no definitiva para el pago {}: se conserva PENDIENTE", pago.getId());
      return pago;
    }
    pago.setResultado("AUTHORIZED".equals(respuesta.status()) ? Resultado.APROBADO : Resultado.RECHAZADO);
    var guardado = repository.save(pago);
    salidas.registrar(guardado);
    return guardado;
  }

  /** status(token) es autoritativo; la decisión se persiste con las mismas reglas del callback. */
  @Transactional
  public Pago reconciliar(String token, WebpayClient.Estado estado) {
    return confirmarWebpay(token, new WebpayClient.Confirmar(estado.status(), estado.responseCode(),
        estado.buyOrder(), estado.sessionId(), estado.amount()));
  }

  /** G0 caso 10: un error de red no demuestra rechazo; conserva PENDIENTE para reconciliar. */
  public void fallarWebpay(String token) {
    log.warn("Resultado de Webpay desconocido: se conserva PENDIENTE para reconciliación");
  }

  private void bloquear(int dominio, int clave) {
    jdbc.query("select pg_advisory_xact_lock(?, ?)", rs -> {}, dominio, clave);
  }
}

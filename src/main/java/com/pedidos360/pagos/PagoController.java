package com.pedidos360.pagos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pagos")
class PagoController {
  private static final Logger log = LoggerFactory.getLogger(PagoController.class);
  private final PagoRepository repository;
  private final PagoService service;
  private final OrdenClient ordenes;
  private final WebpayClient webpay;
  private final String returnUrl;
  private final String frontendUrl;

  PagoController(PagoRepository repository, PagoService service, OrdenClient ordenes, WebpayClient webpay,
      @Value("${webpay.return-url:http://localhost:8089/pagos/webpay/retorno}") String returnUrl,
      @Value("${webpay.frontend-url:http://localhost:4200}") String frontendUrl) {
    this.repository = repository;
    this.service = service;
    this.ordenes = ordenes;
    this.webpay = webpay;
    this.returnUrl = returnUrl;
    this.frontendUrl = frontendUrl;
  }

  @PostMapping
  PagoDto pagar(@Valid @RequestBody Solicitud solicitud, @AuthenticationPrincipal Jwt jwt) {
    String usuario = IdentityConfig.usuario(jwt);
    long total = ordenes.total(solicitud.ordenId(), jwt);
    return dto(service.registrar(usuario, solicitud.ordenId(), total, solicitud.tarjetaDemo()));
  }

  @GetMapping("/{ordenId}")
  PagoDto consultar(@PathVariable UUID ordenId, @AuthenticationPrincipal Jwt jwt) {
    String usuario = IdentityConfig.usuario(jwt);
    return repository.findFirstByOrdenIdAndUsuarioIdAndResultado(ordenId, usuario, PagoService.Resultado.APROBADO)
        .or(() -> repository.findFirstByOrdenIdAndUsuarioIdOrderByOcurrioEnDescIdDesc(ordenId, usuario))
        .map(PagoController::dto)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pago no encontrado"));
  }

  /** Crea la transaccion Webpay usando el total real de la orden ya registrada. */
  @PostMapping("/webpay/iniciar")
  ResponseEntity<?> iniciarWebpay(@Valid @RequestBody SolicitudWebpay solicitud,
      @AuthenticationPrincipal Jwt jwt) {
    String usuario = IdentityConfig.usuario(jwt);
    long total = ordenes.total(solicitud.ordenId(), jwt);
    // Antifraude primero: no se crea una transaccion en Transbank si el cliente esta bloqueado.
    service.verificar(usuario, solicitud.ordenId(), total);
    // Transbank limita buyOrder a 26 caracteres: ID corto y determinista desde la orden.
    String buyOrder = "TX" + solicitud.ordenId().toString().replace("-", "").substring(0, 22);
    var creada = webpay.crear(buyOrder, usuario, total, returnUrl);
    service.iniciarWebpay(usuario, solicitud.ordenId(), total, creada.token(), buyOrder);
    return ResponseEntity.ok(Map.of("url", creada.url(), "token", creada.token()));
  }

  /**
   * Retorno de Transbank: recibe el POST del navegador con token_ws (sin JWT),
   * confirma la transaccion y devuelve al usuario a la SPA con el resultado.
   */
  @PostMapping("/webpay/retorno")
  ResponseEntity<Void> retorno(@RequestParam(value = "token_ws", required = false) String tokenWs,
      @RequestParam(value = "TBK_TOKEN", required = false) String tbkToken) {
    String resultado = "ERROR";
    UUID ordenId = null;
    try {
      if (tokenWs != null && !tokenWs.isBlank()) {
        var pago = service.confirmarWebpay(tokenWs, webpay.confirmar(tokenWs));
        resultado = pago.getResultado().name();
        ordenId = pago.getOrdenId();
      }
    } catch (Exception error) {
      log.warn("Retorno de Webpay invalido: {}", error.getMessage());
    }
    String destino = frontendUrl + "/pago/resultado?resultado=" + resultado
        + (ordenId == null ? "" : "&ordenId=" + ordenId);
    return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destino)).build();
  }

  private static PagoDto dto(Pago pago) {
    var evento = pago.evento();
    return new PagoDto(evento.eventoId(), evento.ordenId(), evento.resultado(), evento.totalClp(), evento.ocurrioEn());
  }

  record Solicitud(@NotNull UUID ordenId, @NotNull PagoService.TarjetaDemo tarjetaDemo) {}
  record SolicitudWebpay(@NotNull UUID ordenId) {}
  record PagoDto(UUID pagoId, UUID ordenId, PagoService.Resultado resultado, long totalClp, Instant ocurrioEn) {}
}

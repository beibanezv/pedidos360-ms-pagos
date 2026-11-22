package com.pedidos360.pagos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/pagos")
class PagoController {
  private final PagoRepository repository;
  private final PagoService service;
  private final OrdenClient ordenes;

  PagoController(PagoRepository repository, PagoService service, OrdenClient ordenes) {
    this.repository = repository;
    this.service = service;
    this.ordenes = ordenes;
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

  private static PagoDto dto(Pago pago) {
    var evento = pago.evento();
    return new PagoDto(evento.eventoId(), evento.ordenId(), evento.resultado(), evento.totalClp(), evento.ocurrioEn());
  }

  record Solicitud(@NotNull UUID ordenId, @NotNull PagoService.TarjetaDemo tarjetaDemo) {}
  record PagoDto(UUID pagoId, UUID ordenId, PagoService.Resultado resultado, long totalClp, Instant ocurrioEn) {}
}

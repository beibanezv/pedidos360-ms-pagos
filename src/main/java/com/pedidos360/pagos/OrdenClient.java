package com.pedidos360.pagos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
class OrdenClient {
  private final RestClient client;

  OrdenClient(RestClient.Builder builder, @Value("${ORDENES_URL:http://localhost:8084}") String baseUrl) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(2));
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
  }

  long total(UUID id, Jwt jwt) {
    Orden orden;
    try {
      orden = client.get().uri("/ordenes/{id}", id).headers(h -> h.setBearerAuth(jwt.getTokenValue()))
          .retrieve().body(Orden.class);
    } catch (RestClientResponseException ex) {
      if (ex.getStatusCode().value() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
      if (ex.getStatusCode().value() == 401) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudieron verificar tus órdenes");
    } catch (RestClientException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudieron verificar tus órdenes");
    }
    if (orden == null || !id.equals(orden.ordenId()) || !IdentityConfig.usuario(jwt).equals(orden.usuarioId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada");
    }
    if (!"REGISTRADA".equals(orden.estado()) || orden.totalClp() == null || orden.totalClp() <= 0) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "La orden no está disponible para pago");
    }
    return orden.totalClp();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Orden(UUID ordenId, String usuarioId, Long totalClp, String estado) {}
}

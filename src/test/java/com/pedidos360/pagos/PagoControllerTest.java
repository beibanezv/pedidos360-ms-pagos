package com.pedidos360.pagos;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(PagoController.class)
@Import(IdentityConfig.class)
class PagoControllerTest {
  @Autowired MockMvc mvc;
  @MockBean PagoRepository repository;
  @MockBean PagoService service;
  @MockBean OrdenClient ordenes;
  @MockBean WebpayClient webpay;

  @Test
  void sinJwtNoConsultaPago() throws Exception {
    mvc.perform(get("/pagos/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
  }

  @Test
  void noSePuedePagarUnaOrdenAjena() throws Exception {
    when(ordenes.total(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
    mvc.perform(post("/pagos").with(jwt()).contentType("application/json")
        .content("{\"ordenId\":\"" + UUID.randomUUID() + "\",\"tarjetaDemo\":\"APROBACION\"}"))
        .andExpect(status().isNotFound());
    org.mockito.Mockito.verifyNoInteractions(service);
  }

  @Test
  void noSePuedeConsultarUnPagoAjeno() throws Exception {
    mvc.perform(get("/pagos/{id}", UUID.randomUUID()).with(jwt())).andExpect(status().isNotFound());
  }

  @Test
  void rechazaDatosDeTarjetaReal() throws Exception {
    mvc.perform(post("/pagos").with(jwt()).contentType("application/json")
        .content("{\"ordenId\":\"" + UUID.randomUUID()
            + "\",\"tarjetaDemo\":\"APROBACION\",\"pan\":\"no-es-un-dato-aceptado\"}"))
        .andExpect(status().isBadRequest());
    org.mockito.Mockito.verifyNoInteractions(service, ordenes);
  }

  @Test
  void iniciaPagoConWebpayConElTotalDeLaOrden() throws Exception {
    UUID orden = UUID.randomUUID();
    when(ordenes.total(any(), any())).thenReturn(18990L);
    when(webpay.crear(argThat(id -> id.startsWith("TX") && id.length() <= 26), anyString(), eq(18990L), anyString()))
        .thenReturn(new WebpayClient.Crear("token-123", "https://webpay.test/pagar"));

    mvc.perform(post("/pagos/webpay/iniciar").with(jwt()).contentType("application/json")
        .content("{\"ordenId\":\"" + orden + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("token-123"))
        .andExpect(jsonPath("$.url").value("https://webpay.test/pagar"));
  }

  @Test
  void noCreaTransaccionSiElClienteEstaBloqueado() throws Exception {
    UUID orden = UUID.randomUUID();
    when(ordenes.total(any(), any())).thenReturn(18990L);
    org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
        .when(service).verificar(anyString(), any(), anyLong());

    mvc.perform(post("/pagos/webpay/iniciar").with(jwt()).contentType("application/json")
        .content("{\"ordenId\":\"" + orden + "\"}"))
        .andExpect(status().isTooManyRequests());
    org.mockito.Mockito.verifyNoInteractions(webpay);
  }

  @Test
  void retornoConfirmaYRedirigeAlFrontend() throws Exception {
    UUID orden = UUID.randomUUID();
    var aprobado = new Pago("cliente", orden, 18990L, PagoService.TarjetaDemo.WEBPAY,
        PagoService.Resultado.APROBADO, Instant.now());
    when(webpay.confirmar("token-123"))
        .thenReturn(new WebpayClient.Confirmar("AUTHORIZED", orden.toString(), "cliente", 18990L));
    when(service.confirmarWebpay(eq("token-123"), any())).thenReturn(aprobado);

    mvc.perform(post("/pagos/webpay/retorno").param("token_ws", "token-123"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", containsString("resultado=APROBADO")))
        .andExpect(header().string("Location", containsString(orden.toString())));
  }

  @Test
  void retornoConTokenDesconocidoRedirigeConError() throws Exception {
    when(webpay.confirmar("basura")).thenThrow(new IllegalStateException("Token no encontrado"));

    mvc.perform(post("/pagos/webpay/retorno").param("token_ws", "basura"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", containsString("resultado=ERROR")));
  }
}

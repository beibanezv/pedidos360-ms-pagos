package com.pedidos360.pagos;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}

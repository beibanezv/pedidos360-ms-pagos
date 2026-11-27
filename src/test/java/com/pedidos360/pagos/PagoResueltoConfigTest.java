package com.pedidos360.pagos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Gate de configuración: sin starter AMQP ni confirmaciones, la salida no es fiable. */
class PagoResueltoConfigTest {

  @Test
  void publicacionRabbitHabilitadaConConfirmacionesCorrelacionadas() throws Exception {
    Properties properties = new Properties();
    try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
      assertThat(in).as("application.properties debe estar en el classpath").isNotNull();
      properties.load(in);
    }
    assertThat(properties.getProperty("spring.rabbitmq.publisher-confirm-type")).isEqualTo("correlated");
    assertThatCode(() -> Class.forName("org.springframework.amqp.rabbit.core.RabbitTemplate"))
        .as("spring-boot-starter-amqp debe estar en el classpath").doesNotThrowAnyException();
  }
}

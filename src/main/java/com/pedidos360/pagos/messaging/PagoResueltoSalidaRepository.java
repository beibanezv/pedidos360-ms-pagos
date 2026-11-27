package com.pedidos360.pagos.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PagoResueltoSalidaRepository extends JpaRepository<PagoResueltoSalida, UUID> {

  List<PagoResueltoSalida> findTop20ByEnviadoEnIsNullOrderByOcurrioEnAscEventoIdAsc();

  @Modifying @Transactional
  @Query("update PagoResueltoSalida s set s.enviadoEn = :cuando "
      + "where s.eventoId = :id and s.enviadoEn is null")
  void marcarEnviado(UUID id, Instant cuando);
}

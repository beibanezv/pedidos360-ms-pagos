package com.pedidos360.pagos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface PagoRepository extends JpaRepository<Pago, UUID> {
  Optional<Pago> findFirstByOrdenIdAndUsuarioIdAndResultado(UUID ordenId, String usuarioId,
      PagoService.Resultado resultado);
  Optional<Pago> findFirstByOrdenIdAndUsuarioIdOrderByOcurrioEnDescIdDesc(UUID ordenId, String usuarioId);
  long countByTarjetaAndOcurrioEnAfterAndResultadoNot(PagoService.TarjetaDemo tarjeta, Instant desde,
      PagoService.Resultado resultado);
  List<Pago> findTop20ByPublicadoEnIsNullOrderByOcurrioEnAscIdAsc();
  Optional<Pago> findFirstByTokenWebpay(String tokenWebpay);

  @Modifying @Transactional
  @Query("update Pago p set p.publicadoEn = :cuando where p.id = :id and p.publicadoEn is null")
  void marcarPublicado(UUID id, Instant cuando);
}

package br.com.nhac.backend_nhac.domain.entrega;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfertaEntregaRepository extends JpaRepository<OfertaEntrega, String> {

    List<OfertaEntrega> findByEntregadorIdAndStatus(String entregadorId, StatusOferta status);

    List<OfertaEntrega> findByPedidoIdAndStatus(String pedidoId, StatusOferta status);

    List<OfertaEntrega> findByStatusAndExpiraEmBefore(StatusOferta status, Instant agora);

    Optional<OfertaEntrega> findByIdAndEntregadorId(String id, String entregadorId);

    Optional<OfertaEntrega> findByPedidoIdAndEntregadorIdAndStatus(
            String pedidoId, String entregadorId, StatusOferta status);
}

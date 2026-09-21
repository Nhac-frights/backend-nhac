package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.domain.entregador.Entregador;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, String> {
    Optional<Pedido> findByStripePaymentIntentId(String stripePaymentIntentId);
    Optional<Pedido> findByAsaasPaymentId(String asaasPaymentId);
    Optional<Pedido> findByUsuarioIdAndIdempotencyKey(String usuarioId, String idempotencyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Pedido p SET p.entregador = :entregador, p.version = p.version + 1 WHERE p.id = :pedidoId AND p.entregador IS NULL")
    int atribuirEntregadorSeDisponivel(
            @Param("pedidoId") String pedidoId,
            @Param("entregador") Entregador entregador
    );

    boolean existsByEntregadorIdAndStatusIn(String entregadorId, java.util.List<StatusPedido> status);
    Page<Pedido> findByUsuarioId(String usuarioId, Pageable pageable);

    @Query(value = """
        SELECT DISTINCT p FROM Pedido p
        JOIN FETCH p.loja
        WHERE p.loja.id = :lojaId
        AND (:status IS NULL OR p.status = :status)
        ORDER BY p.criadoEm DESC
        """,
            countQuery = """
        SELECT COUNT(p) FROM Pedido p
        WHERE p.loja.id = :lojaId
        AND (:status IS NULL OR p.status = :status)
        """)
    Page<Pedido> findByLoja(
            @Param("lojaId") String lojaId,
            @Param("status") StatusPedido status,
            Pageable pageable
    );

    long countByUsuarioId(String usuarioId);
    long countByUsuarioIdAndCupomIdIsNotNull(String usuarioId);

    long countByLojaIdAndStatusIn(String lojaId, java.util.List<StatusPedido> status);

    java.util.List<Pedido> findTop5ByLojaIdOrderByCriadoEmDesc(String lojaId);

    @Query("SELECT p FROM Pedido p WHERE p.loja.id = :lojaId AND p.criadoEm >= :inicio AND p.criadoEm <= :fim")
    java.util.List<Pedido> findByLojaIdAndPeriodo(
            @Param("lojaId") String lojaId,
            @Param("inicio") java.time.Instant inicio,
            @Param("fim") java.time.Instant fim
    );

    Optional<Pedido> findFirstByEntregadorIdAndStatusIn(String entregadorId, java.util.List<StatusPedido> status);

    // ---------- Entregador (V039) ----------

    /**
     * Histórico de corridas do entregador. COALESCE(entregueEm, criadoEm)
     * porque os pedidos criados antes da V039 não têm entregue_em preenchido —
     * sem o fallback eles sumiriam da ordenação.
     */
    @Query(value = """
        SELECT p FROM Pedido p
        JOIN FETCH p.loja
        WHERE p.entregador.id = :entregadorId
          AND (:status IS NULL OR p.status = :status)
        ORDER BY COALESCE(p.entregueEm, p.criadoEm) DESC
        """,
            countQuery = """
        SELECT COUNT(p) FROM Pedido p
        WHERE p.entregador.id = :entregadorId
          AND (:status IS NULL OR p.status = :status)
        """)
    Page<Pedido> findHistoricoDoEntregador(
            @Param("entregadorId") String entregadorId,
            @Param("status") StatusPedido status,
            Pageable pageable
    );

    /**
     * Pedidos do entregador dentro de uma janela, pra agregação de ganhos.
     * Segue o mesmo padrão de findByLojaIdAndPeriodo (usado pelo financeiro do
     * lojista): devolve a lista e a soma é feita em Java.
     */
    @Query("""
        SELECT p FROM Pedido p
        JOIN FETCH p.loja
        WHERE p.entregador.id = :entregadorId
          AND p.status = :status
          AND COALESCE(p.entregueEm, p.criadoEm) >= :inicio
          AND COALESCE(p.entregueEm, p.criadoEm) <= :fim
        """)
    java.util.List<Pedido> findDoEntregadorNoPeriodo(
            @Param("entregadorId") String entregadorId,
            @Param("status") StatusPedido status,
            @Param("inicio") java.time.Instant inicio,
            @Param("fim") java.time.Instant fim
    );

    long countByEntregadorIdAndStatus(String entregadorId, StatusPedido status);
}

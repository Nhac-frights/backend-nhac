package br.com.nhac.backend_nhac.domain.loja;


import br.com.nhac.backend_nhac.domain.loja.Loja;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface LojaRepository extends JpaRepository<Loja, String> {

    // findAll com paginação direta na url
    Page<Loja> findByIsAbertoTrue(Pageable pageable);


    Optional<Loja> findByIdAndIsAbertoTrue(String id);

    Optional<Loja> findByUsuarioId(String usuarioId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Loja> findLockedById(String id);

    @org.springframework.data.jpa.repository.Query(value = "SELECT l.* FROM tb_lojas l " +
            "WHERE l.is_aberto = true " +
            "AND (:nome IS NULL OR LOWER(l.nome) LIKE LOWER(CONCAT('%', :nome, '%'))) " +
            "AND (:lat IS NULL OR :lng IS NULL OR :raio IS NULL " +
            "OR (6371 * acos(cos(radians(:lat)) * cos(radians(l.geo_lat)) " +
            "* cos(radians(l.geo_lng) - radians(:lng)) + sin(radians(:lat)) * sin(radians(l.geo_lat)))) <= :raio) " +
            "ORDER BY " +
            "CASE WHEN :lat IS NOT NULL AND :lng IS NOT NULL THEN " +
            "(6371 * acos(cos(radians(:lat)) * cos(radians(l.geo_lat)) * cos(radians(l.geo_lng) - radians(:lng)) + sin(radians(:lat)) * sin(radians(l.geo_lat)))) " +
            "ELSE 0 END ASC",
            countQuery = "SELECT COUNT(*) FROM tb_lojas l " +
            "WHERE l.is_aberto = true " +
            "AND (:nome IS NULL OR LOWER(l.nome) LIKE LOWER(CONCAT('%', :nome, '%'))) " +
            "AND (:lat IS NULL OR :lng IS NULL OR :raio IS NULL " +
            "OR (6371 * acos(cos(radians(:lat)) * cos(radians(l.geo_lat)) " +
            "* cos(radians(l.geo_lng) - radians(:lng)) + sin(radians(:lat)) * sin(radians(l.geo_lat)))) <= :raio)",
            nativeQuery = true)
    Page<Loja> buscarLojasComFiltros(
            @org.springframework.data.repository.query.Param("nome") String nome,
            @org.springframework.data.repository.query.Param("lat") Double lat,
            @org.springframework.data.repository.query.Param("lng") Double lng,
            @org.springframework.data.repository.query.Param("raio") Double raio,
            Pageable pageable);
}

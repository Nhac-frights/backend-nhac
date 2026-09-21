package br.com.nhac.backend_nhac.domain.entregador;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntregadorRepository extends JpaRepository<Entregador, String> {

    Optional<Entregador> findByUsuarioId(String usuarioId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Entregador e where e.usuario.id = :usuarioId")
    Optional<Entregador> findLockedByUsuarioId(@org.springframework.data.repository.query.Param("usuarioId") String usuarioId);

    List<Entregador> findByStatusOperacionalAndAtivoTrue(StatusOperacional statusOperacional);

    boolean existsByUsuarioId(String usuarioId);

    /**
     * Usado na autenticação (SecurityFilter / StompAuthChannelInterceptor)
     * para somar ROLE_ENTREGADOR às authorities de quem tem cadastro ativo,
     * sem depender de Usuario.papel (que continua CLIENTE mesmo depois do
     * cadastro de entregador — ver EntregadorService.cadastrar()).
     */
    boolean existsByUsuarioIdAndAtivoTrue(String usuarioId);
}

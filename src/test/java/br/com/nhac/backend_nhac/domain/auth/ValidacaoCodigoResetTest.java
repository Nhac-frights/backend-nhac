package br.com.nhac.backend_nhac.domain.auth;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidacaoCodigoResetTest {
    @Mock CodigoVerificacaoEmailRepository repository;
    @Mock EmailService emailService;
    @Mock UsuarioRepository usuarios;
    VerificacaoEmailService service;
    CodigoVerificacaoEmail codigo;

    @BeforeEach
    void setup() {
        service = new VerificacaoEmailService(repository, emailService, usuarios);
        codigo = CodigoVerificacaoEmail.builder().email("cliente@exemplo.com")
                .codigo("123456").tentativas(0).utilizado(false)
                .tipo(CodigoVerificacaoEmail.TipoCodigo.RESET_SENHA)
                .dataExpiracao(LocalDateTime.now().plusMinutes(15)).build();
        when(repository.findTopByEmailAndTipoAndUtilizadoFalseAndDataExpiracaoAfterOrderByCriadoEmDesc(
                eq("cliente@exemplo.com"), eq(CodigoVerificacaoEmail.TipoCodigo.RESET_SENHA), any()))
                .thenAnswer(invocation -> (codigo == null || codigo.isUtilizado()) ? Optional.empty() : Optional.of(codigo));
    }

    @Test
    void validacaoPreservaCodigoAteRedefinicao() {
        service.validarCodigoReset(" CLIENTE@EXEMPLO.COM ", "123456");
        assertFalse(codigo.isUtilizado());
        verify(repository, never()).save(any());
        service.verificarCodigoValido("cliente@exemplo.com", "123456");
        assertTrue(codigo.isUtilizado());
        assertThrows(RegraDeNegocioException.class,
                () -> service.validarCodigoReset("cliente@exemplo.com", "123456"));
    }

    @Test
    void codigoIncorretoContaTentativasEBloqueia() {
        for (int i = 0; i < 3; i++) {
            assertThrows(RegraDeNegocioException.class,
                    () -> service.validarCodigoReset("cliente@exemplo.com", "000000"));
        }
        assertEquals(3, codigo.getTentativas());
        assertThrows(RegraDeNegocioException.class,
                () -> service.validarCodigoReset("cliente@exemplo.com", "123456"));
        assertTrue(codigo.isUtilizado());
    }

    @Test
    void rejeitaCodigoExpiradoOuAusente() {
        codigo = null;
        assertThrows(RegraDeNegocioException.class,
                () -> service.validarCodigoReset("cliente@exemplo.com", "123456"));
        verify(repository, never()).save(any());
    }
}

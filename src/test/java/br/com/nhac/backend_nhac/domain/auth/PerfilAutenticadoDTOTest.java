package br.com.nhac.backend_nhac.domain.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.nhac.backend_nhac.domain.auth.dto.LoginResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.dto.UsuarioResponseDTO;
import org.junit.jupiter.api.Test;

class PerfilAutenticadoDTOTest {

    @Test
    void loginEPerfilInformamOCargoSemAlterarOPapelDoFuncionario() {
        Usuario funcionario = Usuario.builder()
                .id("funcionario-1")
                .nome("Funcionária")
                .papel(Papel.FUNCIONARIO)
                .cargo("Gerente")
                .build();

        LoginResponseDTO login = LoginResponseDTO.from(funcionario, "token", false);
        UsuarioResponseDTO perfil = new UsuarioResponseDTO(funcionario);

        assertEquals("FUNCIONARIO", login.papel());
        assertEquals("Gerente", login.cargo());
        assertEquals(Papel.FUNCIONARIO, perfil.papel());
        assertEquals(login.cargo(), perfil.cargo());
        assertEquals(Papel.FUNCIONARIO, funcionario.getPapel());
    }

    @Test
    void contaNovaContinuaClienteSemCargoDeFuncionario() {
        Usuario usuario = new Usuario();

        LoginResponseDTO login = LoginResponseDTO.from(usuario, "token", true);
        UsuarioResponseDTO perfil = new UsuarioResponseDTO(usuario);

        assertEquals("CLIENTE", login.papel());
        assertNull(login.cargo());
        assertEquals(Papel.CLIENTE, perfil.papel());
        assertNull(perfil.cargo());
    }
}

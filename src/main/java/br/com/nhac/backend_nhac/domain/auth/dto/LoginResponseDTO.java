package br.com.nhac.backend_nhac.domain.auth.dto;

import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;

public record LoginResponseDTO(
        String token,
        String usuarioId,
        String nome,
        boolean isNovoUsuario,
        String papel,
        String cargo
) {
    public static LoginResponseDTO from(Usuario usuario, String token, boolean isNovoUsuario) {
        Papel papel = usuario.getPapel() != null ? usuario.getPapel() : Papel.CLIENTE;
        return new LoginResponseDTO(token, usuario.getId(), usuario.getNome(), isNovoUsuario, papel.name(), usuario.getCargo());
    }
}

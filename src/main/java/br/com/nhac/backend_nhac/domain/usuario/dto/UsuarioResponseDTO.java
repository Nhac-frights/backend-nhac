package br.com.nhac.backend_nhac.domain.usuario.dto;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Objeto de resposta com os dados públicos do perfil do utilizador (Sem dados sensíveis)")
public record UsuarioResponseDTO(
        @Schema(description = "ID único do utilizador")
        String id,

        @Schema(description = "Nome completo")
        String nome,

        @Schema(description = "E-mail cadastrado")
        String email,

        @Schema(description = "Telefone cadastrado")
        String telefone,

        @Schema(description = "URL da foto de perfil")
        String imagemUrl,

        @Schema(description = "Papel (Role) do utilizador", example = "CLIENTE")
        br.com.nhac.backend_nhac.domain.usuario.Papel papel,

        @Schema(description = "Cargo do funcionário na loja", example = "Gerente")
        String cargo
) {

    public UsuarioResponseDTO(Usuario usuario) {
        this(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getImagemUrl(),
                usuario.getPapel(),
                usuario.getCargo()
        );
    }
}

package br.com.nhac.backend_nhac.domain.usuario.dto;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;

@Schema(description = "Objeto de entrada para criação ou registo de um novo utilizador")
public record UsuarioCreateDTO(
        @Schema(description = "ID único do utilizador (UID do Firebase ou Auth)", example = "usr_abc123xyz")
        @NotBlank(message = "O ID do usuário é obrigatório.")
        String id,

        @Schema(description = "Nome completo do utilizador", example = "Matheus Alves")
        @NotBlank(message = "O nome é obrigatório.")
        String nome,

        @Schema(description = "Endereço de e-mail", example = "matheus@nhac.com")
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "O e-mail informado é inválido.")
        String email,

        @Schema(description = "Número de telefone", example = "11999998888")
        @NotBlank(message = "O telefone é obrigatório.")
        String telefone,

        @Schema(description = "URL da foto de perfil", example = "https://images.nhac.com/profiles/usr_abc.jpg")
        String imagemUrl,

        @Schema(description = "Senha em texto limpo para ser encriptada pelo servidor", example = "senhaSegura123")
        @jakarta.validation.constraints.Size(min = 8, message = "A senha deve ter pelo menos 8 caracteres.")
        @jakarta.validation.constraints.Pattern(regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).*$", message = "A senha deve conter pelo menos uma letra e um número.")
        String senha
) {
  public Usuario toEntity() {
    return new Usuario(
            this.id(),          // id
            this.nome(),        // nome
            this.email(),       // email
            this.telefone(),    // telefone
            this.imagemUrl(),   // imagemUrl
            null,               // cpf  ← FALTAVA ESSE
            new ArrayList<>(),  // enderecos
            this.senha(),       // senha
            null,               // fcmToken
            false,              // telefoneVerificado
            br.com.nhac.backend_nhac.domain.usuario.Papel.CLIENTE, // papel
            true,               // ativo
            false,              // emailVerificado
            null,               // lojaVinculadaId
            null,               // cargo
            java.time.Instant.now(), // criadoEm
            true,               // notificarNovoPedido
            true,               // notificarMensagens
            false,              // notificarAvaliacoes
            false               // notificarNovidades
        );
    }
}
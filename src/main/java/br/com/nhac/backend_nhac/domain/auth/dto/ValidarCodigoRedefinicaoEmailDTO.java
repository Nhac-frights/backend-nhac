package br.com.nhac.backend_nhac.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ValidarCodigoRedefinicaoEmailDTO(
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        String email,
        @NotBlank(message = "O código é obrigatório.")
        @Pattern(regexp = "[0-9]{6}", message = "O código deve ter 6 dígitos.")
        String codigo
) {}

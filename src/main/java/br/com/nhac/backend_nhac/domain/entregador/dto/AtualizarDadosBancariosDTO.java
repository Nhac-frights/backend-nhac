package br.com.nhac.backend_nhac.domain.entregador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AtualizarDadosBancariosDTO(
        @NotBlank @Pattern(regexp = "CPF|CELULAR|EMAIL|ALEATORIA") String tipoChavePix,
        @NotBlank @Size(max = 255) String chavePix
) {}

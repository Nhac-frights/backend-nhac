package br.com.nhac.backend_nhac.domain.entregador.dto;

import br.com.nhac.backend_nhac.domain.entregador.TipoVeiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AtualizarVeiculoDTO(
        @NotNull TipoVeiculo tipoVeiculo,
        @NotBlank @Size(max = 20)
        @Pattern(regexp = "(?i)^[a-z]{3}-?(?:[0-9]{4}|[0-9][a-z][0-9]{2})$",
                message = "Placa inválida.") String placaVeiculo,
        @Size(max = 60) String modeloVeiculo,
        @Size(max = 30) String corVeiculo
) {}

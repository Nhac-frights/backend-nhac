package br.com.nhac.backend_nhac.domain.entregador.dto;

import br.com.nhac.backend_nhac.domain.entregador.TipoVeiculo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CadastroEntregadorDTO(
        @NotBlank(message = "A CNH é obrigatória.")
        String cnh,

        @NotBlank(message = "A placa do veículo é obrigatória.")
        String placaVeiculo,

        @NotNull(message = "O tipo do veículo é obrigatório.")
        TipoVeiculo tipoVeiculo,

        @NotBlank(message = "O CPF é obrigatório.")
        String cpf,

        String corVeiculo,

        String modeloVeiculo
) {
}
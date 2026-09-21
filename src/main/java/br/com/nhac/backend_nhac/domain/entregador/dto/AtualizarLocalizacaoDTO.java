package br.com.nhac.backend_nhac.domain.entregador.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record AtualizarLocalizacaoDTO(
        @NotNull(message = "A latitude é obrigatória.")
        @DecimalMin(value = "-90.0", message = "A latitude mínima é -90.")
        @DecimalMax(value = "90.0", message = "A latitude máxima é 90.")
        Double latitude,

        @NotNull(message = "A longitude é obrigatória.")
        @DecimalMin(value = "-180.0", message = "A longitude mínima é -180.")
        @DecimalMax(value = "180.0", message = "A longitude máxima é 180.")
        Double longitude
) {
}

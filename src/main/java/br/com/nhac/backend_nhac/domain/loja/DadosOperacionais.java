package br.com.nhac.backend_nhac.domain.loja;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DadosOperacionais {

    @Schema(description = "Avaliação média da loja pelos clientes", example = "4.6")
    @Column(name = "avaliacao_media", columnDefinition = "DECIMAL(3,1)")
    private float avaliacaoMedia;

    @Schema(description = "Valor base cobrado para a taxa de entrega", example = "5.99")
    @Column(name = "taxa_entrega_base")
    private BigDecimal taxaEntregaBase;

    @Schema(description = "Tempo mínimo estimado para entrega (em minutos)", example = "20")
    @Column(name = "tempo_entrega_min")
    private int tempoEntregaMin;

    @Schema(description = "Tempo máximo estimado para entrega (em minutos)", example = "40")
    @Column(name = "tempo_entrega_max")
    private int tempoEntregaMax;

    @Schema(description = "Número total de avaliações que a loja já recebeu", example = "456")
    @Column(name = "total_avaliacoes")
    private int totalAvaliacoes;

    // B3 - Novos campos de dados operacionais
    @Schema(description = "Indica se a loja realiza entrega própria", example = "true")
    @Column(name = "entrega_propria", nullable = false)
    private Boolean entregaPropria = true;

    @Schema(description = "Indica se a loja permite retirada no local", example = "false")
    @Column(name = "retirada_no_local", nullable = false)
    private Boolean retiradaNoLocal = false;

    @Schema(description = "Raio de entrega em quilômetros (null = ilimitado)", example = "10.5")
    @Column(name = "raio_entrega_km", precision = 6, scale = 2)
    private BigDecimal raioEntregaKm;

}

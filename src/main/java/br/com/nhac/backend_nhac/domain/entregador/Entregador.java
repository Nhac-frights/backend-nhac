package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "tb_entregadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Entregador {

    @Id
    @Column(updatable = false, nullable = false, length = 50)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false, length = 30)
    private String cnh;

    @Version
    private Long version;

    @Column(name = "cor_veiculo", length = 30)
    private String corVeiculo;

    @Column(name = "modelo_veiculo", length = 60)
    private String modeloVeiculo;

    @Column(name = "placa_veiculo", nullable = false, length = 20)
    private String placaVeiculo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_veiculo", nullable = false, length = 20)
    @Builder.Default
    private TipoVeiculo tipoVeiculo = TipoVeiculo.MOTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_operacional", nullable = false, length = 30)
    @Builder.Default
    private StatusOperacional statusOperacional = StatusOperacional.OFFLINE;

    @Column(name = "latitude_atual")
    private Double latitudeAtual;

    @Column(name = "longitude_atual")
    private Double longitudeAtual;

    @Column(name = "ultima_atualizacao_localizacao")
    private Instant ultimaAtualizacaoLocalizacao;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();

    public void atualizarLocalizacao(Double latitude, Double longitude) {
        this.latitudeAtual = latitude;
        this.longitudeAtual = longitude;
        this.ultimaAtualizacaoLocalizacao = Instant.now();
    }
}

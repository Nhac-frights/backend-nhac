package br.com.nhac.backend_nhac.domain.loja;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import br.com.nhac.backend_nhac.domain.loja.dto.CalcularFreteResponseDTO;

/**
 * Fonte única da regra de frete usada tanto na estimativa pública quanto na
 * finalização do pedido. O endereço/coordenadas continuam disponíveis para
 * evolução geográfica futura, mas a regra atual é a taxa base da loja.
 */
@Service
public class FreteService {

    private static final BigDecimal FRETE_PADRAO = new BigDecimal("5.00");
    private static final int TEMPO_PADRAO_MINUTOS = 45;

    public BigDecimal calcularTaxa(Loja loja) {
        if (loja.getDadosOperacionais() != null
                && loja.getDadosOperacionais().getTaxaEntregaBase() != null) {
            return loja.getDadosOperacionais().getTaxaEntregaBase();
        }
        return FRETE_PADRAO;
    }

    public CalcularFreteResponseDTO calcular(Loja loja) {
        int tempo = TEMPO_PADRAO_MINUTOS;
        if (loja.getDadosOperacionais() != null
                && loja.getDadosOperacionais().getTempoEntregaMax() > 0) {
            tempo = loja.getDadosOperacionais().getTempoEntregaMax();
        }
        return new CalcularFreteResponseDTO(calcularTaxa(loja), tempo);
    }
}

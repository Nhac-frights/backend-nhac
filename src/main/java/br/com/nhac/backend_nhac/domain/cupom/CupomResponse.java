package br.com.nhac.backend_nhac.domain.cupom;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CupomResponse(String id, String titulo, String descricao, String codigo,
        BigDecimal desconto, String tipo, BigDecimal usoMinimo, LocalDateTime dataValidade,
        String status, BigDecimal descontoAplicado) {
    public static CupomResponse from(Cupom c, BigDecimal aplicado) {
        String status = c.getUsosAtuais() >= c.getLimiteUsos() ? "USADO"
                : !c.isAtivo() || (c.getDataValidade() != null && !c.getDataValidade().isAfter(LocalDateTime.now()))
                ? "EXPIRADO" : "DISPONIVEL";
        return new CupomResponse(c.getId(), "Cupom de boas-vindas", c.getDescricao(), c.getCodigo(),
                c.getValorDesconto(), c.getTipoDesconto(), c.getValorMinimoPedido(),
                c.getDataValidade(), status, aplicado);
    }
}

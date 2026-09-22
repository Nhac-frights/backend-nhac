package br.com.nhac.backend_nhac.domain.cupom;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;

@Service
public class CupomService {
    private final CupomRepository repository;
    private final UsuarioRepository usuarios;
    private final BigDecimal valor;
    private final BigDecimal minimo;
    private final int dias;

    public CupomService(CupomRepository repository, UsuarioRepository usuarios,
            @Value("${nhac.cupom.boas-vindas.valor:5.00}") BigDecimal valor,
            @Value("${nhac.cupom.boas-vindas.minimo:25.00}") BigDecimal minimo,
            @Value("${nhac.cupom.boas-vindas.validade-dias:30}") int dias) {
        if (valor.signum() <= 0 || minimo.compareTo(valor) <= 0 || dias <= 0) {
            throw new IllegalArgumentException("Cupom de boas-vindas exige valor positivo, mínimo maior que o desconto e validade positiva.");
        }
        this.repository = repository;
        this.usuarios = usuarios;
        this.valor = valor;
        this.minimo = minimo;
        this.dias = dias;
    }

    @Transactional(readOnly = true)
    public List<CupomResponse> listar(String usuarioId) {
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .map(c -> CupomResponse.from(c, BigDecimal.ZERO)).toList();
    }

    @Transactional
    public CupomResponse ganharBoasVindas(String usuarioId) {
        // O mesmo lock utilizado no checkout serializa resgates da mesma conta.
        var usuario = usuarios.findLockedById(usuarioId)
                .orElseThrow(() -> new RegraDeNegocioException("Usuário não encontrado."));
        if (!usuario.isAtivo()) throw new RegraDeNegocioException("Usuário inativo.");
        Cupom cupom = repository.findByUsuarioIdAndOrigem(usuarioId, "BOAS_VINDAS").orElseGet(() -> {
            Cupom novo = new Cupom();
            novo.setId(UUID.randomUUID().toString());
            novo.setCodigo("BEMVINDO-" + novo.getId());
            novo.setDescricao("Desconto nos produtos. Um cupom de boas-vindas por conta.");
            novo.setTipoDesconto("FIXO");
            novo.setValorDesconto(valor);
            novo.setValorMinimoPedido(minimo);
            novo.setCriadoEm(LocalDateTime.now());
            novo.setDataValidade(LocalDateTime.now().plusDays(dias));
            novo.setUsuarioId(usuarioId);
            novo.setOrigem("BOAS_VINDAS");
            return repository.save(novo);
        });
        // Repetir o resgate não renova a validade e não gera outro cupom.
        return CupomResponse.from(cupom, BigDecimal.ZERO);
    }

    @Transactional
    public CupomResponse validar(String usuarioId, String cupomId, BigDecimal subtotal) {
        Cupom cupom = obterValido(usuarioId, cupomId, subtotal);
        return CupomResponse.from(cupom, desconto(cupom, subtotal));
    }

    @Transactional
    public BigDecimal consumir(String usuarioId, String cupomId, BigDecimal subtotal) {
        Cupom cupom = obterValido(usuarioId, cupomId, subtotal);
        BigDecimal desconto = desconto(cupom, subtotal);
        cupom.setUsosAtuais(cupom.getUsosAtuais() + 1);
        repository.save(cupom);
        return desconto;
    }

    @Transactional
    public void devolver(String usuarioId, String cupomId) {
        Cupom cupom = repository.findLockedById(cupomId)
                .orElseThrow(() -> new RegraDeNegocioException("Cupom não encontrado."));
        if (!usuarioId.equals(cupom.getUsuarioId())) throw new RegraDeNegocioException("Cupom não pertence à conta.");
        cupom.setUsosAtuais(Math.max(0, cupom.getUsosAtuais() - 1));
        repository.save(cupom);
    }

    private Cupom obterValido(String usuarioId, String cupomId, BigDecimal subtotal) {
        Cupom cupom = repository.findLockedById(cupomId)
                .orElseThrow(() -> new RegraDeNegocioException("Cupom indisponível."));
        if (!usuarioId.equals(cupom.getUsuarioId())) throw new RegraDeNegocioException("Cupom indisponível para esta conta.");
        if (!"DISPONIVEL".equals(CupomResponse.from(cupom, BigDecimal.ZERO).status())) {
            throw new RegraDeNegocioException("Cupom usado ou expirado.");
        }
        if (subtotal == null || subtotal.signum() <= 0 || subtotal.compareTo(cupom.getValorMinimoPedido()) < 0) {
            throw new RegraDeNegocioException("Pedido mínimo para este cupom: R$ " + cupom.getValorMinimoPedido().toPlainString());
        }
        return cupom;
    }

    private BigDecimal desconto(Cupom cupom, BigDecimal subtotal) {
        BigDecimal desconto = "PERCENTUAL".equals(cupom.getTipoDesconto())
                ? subtotal.multiply(cupom.getValorDesconto()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : cupom.getValorDesconto();
        return desconto.min(subtotal).setScale(2, RoundingMode.HALF_UP);
    }
}

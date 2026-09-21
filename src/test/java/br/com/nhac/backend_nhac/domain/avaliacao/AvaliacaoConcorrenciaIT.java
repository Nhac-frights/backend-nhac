package br.com.nhac.backend_nhac.domain.avaliacao;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoCreateDTO;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.pedido.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class AvaliacaoConcorrenciaIT extends AbstractMariaDbIntegrationTest {

    @Autowired AvaliacaoService avaliacaoService;
    @Autowired AvaliacaoRepository avaliacaoRepository;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired LojaRepository lojaRepository;
    @Autowired UsuarioRepository usuarioRepository;

    @Test
    void avaliacoesConcorrentesDevemManterMediaEContagemCorretas() throws Exception {
        Usuario u1 = usuario("aval-u1", "11910000001");
        Usuario u2 = usuario("aval-u2", "11910000002");
        usuarioRepository.saveAll(List.of(u1, u2));

        Loja loja = loja();
        lojaRepository.save(loja);
        pedidoRepository.saveAllAndFlush(List.of(
                pedido("aval-p1", u1, loja),
                pedido("aval-p2", u2, loja)
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<?> f1 = executor.submit(() -> avaliar(largada, u1, "aval-p1", 5));
            Future<?> f2 = executor.submit(() -> avaliar(largada, u2, "aval-p2", 3));
            largada.countDown();
            f1.get();
            f2.get();
        } finally {
            executor.shutdownNow();
        }

        Loja finalLoja = lojaRepository.findById("loja-aval").orElseThrow();
        assertEquals(2, avaliacaoRepository.countByLojaId("loja-aval"));
        assertEquals(2, finalLoja.getDadosOperacionais().getTotalAvaliacoes());
        assertEquals(4.0f, finalLoja.getDadosOperacionais().getAvaliacaoMedia(), 0.01f);
    }

    private void avaliar(CountDownLatch largada, Usuario usuario, String pedidoId, int nota) {
        try {
            largada.await();
            avaliacaoService.criarAvaliacao(
                    usuario.getId(), new AvaliacaoCreateDTO(pedidoId, nota, "ok"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Usuario usuario(String id, String telefone) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(id);
        u.setTelefone(telefone);
        u.setPapel(Papel.CLIENTE);
        return u;
    }

    private Loja loja() {
        Loja loja = new Loja();
        loja.setId("loja-aval");
        loja.setNome("Loja avaliações");
        loja.setAberto(true);
        DadosOperacionais dados = new DadosOperacionais();
        dados.setAvaliacaoMedia(0);
        dados.setTotalAvaliacoes(0);
        dados.setEntregaPropria(true);
        dados.setRetiradaNoLocal(false);
        dados.setTaxaEntregaBase(new BigDecimal("5.00"));
        dados.setTempoEntregaMin(20);
        dados.setTempoEntregaMax(40);
        loja.setDadosOperacionais(dados);
        loja.setEndereco(new EnderecoLoja("Rua A", "10", "Cidade", "SP", "01000-000", "Centro", null));
        return loja;
    }

    private Pedido pedido(String id, Usuario usuario, Loja loja) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setUsuarioId(usuario.getId());
        p.setLoja(loja);
        p.setValorTotal(new BigDecimal("20.00"));
        p.setTaxaFrete(new BigDecimal("5.00"));
        p.setFormaPagamento("DINHEIRO");
        p.setStatus(StatusPedido.ENTREGUE);
        p.setCriadoEm(Instant.now());
        return p;
    }
}

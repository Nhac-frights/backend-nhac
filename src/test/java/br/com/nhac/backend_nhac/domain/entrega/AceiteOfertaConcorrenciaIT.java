package br.com.nhac.backend_nhac.domain.entrega;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import br.com.nhac.backend_nhac.domain.entregador.*;
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

class AceiteOfertaConcorrenciaIT extends AbstractMariaDbIntegrationTest {

    @Autowired DespachoService despachoService;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired OfertaEntregaRepository ofertaRepository;
    @Autowired EntregadorRepository entregadorRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired LojaRepository lojaRepository;

    @Test
    void somenteUmEntregadorDeveVencerAceiteConcorrente() throws Exception {
        Usuario cliente = usuario("cli", "Cliente", "11900000000");
        Usuario u1 = usuario("u1", "Moto 1", "11900000001");
        Usuario u2 = usuario("u2", "Moto 2", "11900000002");
        usuarioRepository.saveAll(List.of(cliente, u1, u2));

        Loja loja = new Loja();
        loja.setId("loja-conc");
        loja.setNome("Loja concorrência");
        loja.setAberto(true);
        DadosOperacionais dados = new DadosOperacionais();
        dados.setTaxaEntregaBase(new BigDecimal("5.00"));
        dados.setTempoEntregaMin(20);
        dados.setTempoEntregaMax(40);
        dados.setEntregaPropria(true);
        dados.setRetiradaNoLocal(false);
        loja.setDadosOperacionais(dados);
        loja.setEndereco(new EnderecoLoja("Rua A", "10", "Cidade", "SP", "01000-000", "Centro", null));
        lojaRepository.save(loja);

        Entregador e1 = entregador("e1", u1, "AAA1A11");
        Entregador e2 = entregador("e2", u2, "BBB2B22");
        entregadorRepository.saveAll(List.of(e1, e2));

        Pedido pedido = new Pedido();
        pedido.setId("pedido-conc");
        pedido.setUsuarioId(cliente.getId());
        pedido.setLoja(loja);
        pedido.setValorTotal(new BigDecimal("50.00"));
        pedido.setTaxaFrete(new BigDecimal("5.00"));
        pedido.setFormaPagamento("DINHEIRO");
        pedido.setStatus(StatusPedido.PREPARANDO);
        pedido.setCriadoEm(Instant.now());
        pedidoRepository.saveAndFlush(pedido);

        ofertaRepository.saveAllAndFlush(List.of(
                oferta("o1", pedido, e1),
                oferta("o2", pedido, e2)
        ));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<Boolean> f1 = executor.submit(() -> aceitar(largada, "o1", u1));
            Future<Boolean> f2 = executor.submit(() -> aceitar(largada, "o2", u2));
            largada.countDown();

            int sucessos = (f1.get() ? 1 : 0) + (f2.get() ? 1 : 0);
            assertEquals(1, sucessos);
        } finally {
            executor.shutdownNow();
        }

        Pedido finalPedido = pedidoRepository.findById("pedido-conc").orElseThrow();
        assertNotNull(finalPedido.getEntregador());

        List<OfertaEntrega> ofertas = ofertaRepository.findAll();
        assertEquals(1, ofertas.stream().filter(o -> o.getStatus() == StatusOferta.ACEITA).count());
        assertEquals(1, ofertas.stream().filter(o -> o.getStatus() == StatusOferta.EXPIRADA).count());

        Entregador final1 = entregadorRepository.findById("e1").orElseThrow();
        Entregador final2 = entregadorRepository.findById("e2").orElseThrow();
        assertEquals(1, List.of(final1, final2).stream()
                .filter(e -> e.getStatusOperacional() == StatusOperacional.EM_ENTREGA).count());
    }

    private boolean aceitar(CountDownLatch largada, String ofertaId, Usuario usuario) throws Exception {
        largada.await();
        try {
            despachoService.aceitarOferta(ofertaId, usuario);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Usuario usuario(String id, String nome, String telefone) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(nome);
        u.setTelefone(telefone);
        u.setPapel(Papel.CLIENTE);
        return u;
    }

    private Entregador entregador(String id, Usuario usuario, String placa) {
        return Entregador.builder()
                .id(id).usuario(usuario).cnh("CNH-" + id).placaVeiculo(placa)
                .tipoVeiculo(TipoVeiculo.MOTO).statusOperacional(StatusOperacional.ONLINE)
                .ativo(true).build();
    }

    private OfertaEntrega oferta(String id, Pedido pedido, Entregador entregador) {
        return OfertaEntrega.builder()
                .id(id).pedido(pedido).entregador(entregador)
                .status(StatusOferta.PENDENTE)
                .criadoEm(Instant.now()).expiraEm(Instant.now().plusSeconds(60))
                .build();
    }
}

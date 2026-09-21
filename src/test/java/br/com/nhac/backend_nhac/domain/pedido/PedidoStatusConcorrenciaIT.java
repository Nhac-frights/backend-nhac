package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.AbstractMariaDbIntegrationTest;
import br.com.nhac.backend_nhac.domain.entrega.DespachoService;
import br.com.nhac.backend_nhac.domain.entregador.*;
import br.com.nhac.backend_nhac.domain.loja.*;
import br.com.nhac.backend_nhac.domain.usuario.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class PedidoStatusConcorrenciaIT extends AbstractMariaDbIntegrationTest {

    @Autowired PedidoService pedidoService;
    @Autowired DespachoService despachoService;
    @Autowired PedidoRepository pedidoRepository;
    @Autowired EntregadorRepository entregadorRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired LojaRepository lojaRepository;

    @Test
    void cancelarEColetarSimultaneamenteDevemTerminarEmEstadoConsistente() throws Exception {
        Usuario lojista = usuario("lojista-race", Papel.LOJISTA, "11920000001");
        Usuario cliente = usuario("cliente-race", Papel.CLIENTE, "11920000002");
        Usuario motoUser = usuario("moto-race", Papel.CLIENTE, "11920000003");
        usuarioRepository.save(lojista);
        usuarioRepository.save(cliente);
        usuarioRepository.save(motoUser);

        Loja loja = loja(lojista);
        lojaRepository.save(loja);

        Entregador entregador = Entregador.builder()
                .id("ent-race")
                .usuario(motoUser)
                .cnh("CNH-RACE")
                .placaVeiculo("RAC1E23")
                .tipoVeiculo(TipoVeiculo.MOTO)
                .statusOperacional(StatusOperacional.EM_ENTREGA)
                .ativo(true)
                .build();
        entregadorRepository.save(entregador);

        Pedido pedido = new Pedido();
        pedido.setId("ped-status-race");
        pedido.setUsuarioId(cliente.getId());
        pedido.setLoja(loja);
        pedido.setEntregador(entregador);
        pedido.setValorTotal(new BigDecimal("30.00"));
        pedido.setTaxaFrete(new BigDecimal("5.00"));
        pedido.setFormaPagamento("DINHEIRO");
        pedido.setStatus(StatusPedido.PREPARANDO);
        pedido.setCriadoEm(Instant.now());
        pedidoRepository.saveAndFlush(pedido);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<Boolean> cancelar = executor.submit(() -> {
                largada.await();
                try {
                    pedidoService.atualizarStatus(
                            "ped-status-race", StatusPedido.CANCELADO, lojista);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            });
            Future<Boolean> coletar = executor.submit(() -> {
                largada.await();
                try {
                    despachoService.coletarPedido("ped-status-race", motoUser);
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            });

            largada.countDown();
            int sucessos = (cancelar.get() ? 1 : 0) + (coletar.get() ? 1 : 0);
            assertEquals(1, sucessos);
        } finally {
            executor.shutdownNow();
        }

        Pedido finalPedido = pedidoRepository.findById("ped-status-race").orElseThrow();
        Entregador finalEntregador = entregadorRepository.findById("ent-race").orElseThrow();

        assertTrue(finalPedido.getStatus() == StatusPedido.CANCELADO
                || finalPedido.getStatus() == StatusPedido.SAIU_ENTREGA);

        if (finalPedido.getStatus() == StatusPedido.CANCELADO) {
            assertEquals(StatusOperacional.ONLINE, finalEntregador.getStatusOperacional());
            assertNull(finalPedido.getColetadoEm());
        } else {
            assertEquals(StatusOperacional.EM_ENTREGA, finalEntregador.getStatusOperacional());
            assertNotNull(finalPedido.getColetadoEm());
        }
    }

    private Usuario usuario(String id, Papel papel, String telefone) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome(id);
        u.setTelefone(telefone);
        u.setPapel(papel);
        return u;
    }

    private Loja loja(Usuario lojista) {
        Loja loja = new Loja();
        loja.setId("loja-status-race");
        loja.setUsuarioId(lojista.getId());
        loja.setNome("Loja Race");
        loja.setAberto(true);
        DadosOperacionais dados = new DadosOperacionais();
        dados.setEntregaPropria(true);
        dados.setRetiradaNoLocal(false);
        dados.setTaxaEntregaBase(new BigDecimal("5.00"));
        dados.setTempoEntregaMin(20);
        dados.setTempoEntregaMax(40);
        loja.setDadosOperacionais(dados);
        loja.setEndereco(new EnderecoLoja(
                "Rua A", "10", "Cidade", "SP", "01000-000", "Centro", null));
        return loja;
    }
}

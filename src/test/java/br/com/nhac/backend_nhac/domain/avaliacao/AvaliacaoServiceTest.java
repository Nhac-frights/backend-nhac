package br.com.nhac.backend_nhac.domain.avaliacao;

import br.com.nhac.backend_nhac.domain.avaliacao.Avaliacao;
import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoCreateDTO;
import br.com.nhac.backend_nhac.domain.avaliacao.dto.AvaliacaoResumoDTO;
import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.pedido.Pedido;
import br.com.nhac.backend_nhac.domain.pedido.StatusPedido;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import br.com.nhac.backend_nhac.domain.avaliacao.AvaliacaoRepository;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.pedido.PedidoRepository;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    @Mock
    private AvaliacaoRepository avaliacaoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private LojaRepository lojaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private AvaliacaoService avaliacaoService;

    private Usuario usuarioLogado;
    private Usuario usuarioOutro;
    private Loja loja;
    private Pedido pedido;
    private AvaliacaoCreateDTO dtoValido;

    @BeforeEach
    void setUp() {
        usuarioLogado = new Usuario();
        usuarioLogado.setId("user_1");
        usuarioLogado.setNome("João");

        usuarioOutro = new Usuario();
        usuarioOutro.setId("user_2");
        usuarioOutro.setNome("Maria");

        DadosOperacionais dados = new DadosOperacionais();
        dados.setAvaliacaoMedia(4.0f);
        dados.setTotalAvaliacoes(10);

        loja = new Loja();
        loja.setId("loja_1");
        loja.setDadosOperacionais(dados);

        pedido = new Pedido();
        pedido.setId("pedido_1");
        pedido.setUsuarioId("user_1");
        pedido.setLoja(loja);
        pedido.setStatus(StatusPedido.ENTREGUE);

        dtoValido = new AvaliacaoCreateDTO("pedido_1", 5, "Muito bom!");
    }

    @Test
    @DisplayName("Deve criar avaliação com sucesso e recalcular média")
    void deveCriarAvaliacaoComSucesso() {
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuarioLogado));
        when(pedidoRepository.findById("pedido_1")).thenReturn(Optional.of(pedido));
        when(avaliacaoRepository.existsByPedidoId("pedido_1")).thenReturn(false);
        when(lojaRepository.findLockedById("loja_1")).thenReturn(Optional.of(loja));
        when(avaliacaoRepository.countByLojaId("loja_1")).thenReturn(11L);
        when(avaliacaoRepository.calcularMediaPorLojaId("loja_1")).thenReturn(45.0 / 11.0);

        // Simulando que ao salvar, a entidade receba um id
        when(avaliacaoRepository.saveAndFlush(any(Avaliacao.class))).thenAnswer(invocation -> {
            Avaliacao a = invocation.getArgument(0);
            a.setId("aval_1");
            return a;
        });

        AvaliacaoResumoDTO resumo = avaliacaoService.criarAvaliacao("user_1", dtoValido);

        assertNotNull(resumo);
        assertEquals(5, resumo.nota());
        assertEquals("Muito bom!", resumo.comentario());
        assertEquals("João", resumo.nomeUsuario());

        verify(avaliacaoRepository, times(1)).saveAndFlush(any(Avaliacao.class));
        verify(lojaRepository, times(1)).save(loja);

        // Validando o recálculo (Média antiga: 4.0, Total antigo: 10, Nova nota: 5)
        // (40 + 5) / 11 = 45 / 11 = 4.0909 -> Math.round = 4.1
        assertEquals(11, loja.getDadosOperacionais().getTotalAvaliacoes());
        assertEquals(4.1f, loja.getDadosOperacionais().getAvaliacaoMedia(), 0.01);
    }

    @Test
    @DisplayName("Deve lançar exceção se usuário tentar avaliar pedido de outro")
    void deveLancarExcecaoAoAvaliarPedidoDeOutroUsuario() {
        when(usuarioRepository.findById("user_2")).thenReturn(Optional.of(usuarioOutro));
        when(pedidoRepository.findById("pedido_1")).thenReturn(Optional.of(pedido)); // Pedido é do user_1

        Exception excecao = assertThrows(RegraDeNegocioException.class,
                () -> avaliacaoService.criarAvaliacao("user_2", dtoValido));

        assertEquals("Você só pode avaliar pedidos que pertencem a você.", excecao.getMessage());
        verify(avaliacaoRepository, never()).save(any(Avaliacao.class));
    }

    @Test
    @DisplayName("Deve lançar exceção se pedido não estiver entregue ou concluído")
    void deveLancarExcecaoSePedidoNaoConcluido() {
        pedido.setStatus(br.com.nhac.backend_nhac.domain.pedido.StatusPedido.PREPARANDO);

        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuarioLogado));
        when(pedidoRepository.findById("pedido_1")).thenReturn(Optional.of(pedido));

        Exception excecao = assertThrows(RegraDeNegocioException.class,
                () -> avaliacaoService.criarAvaliacao("user_1", dtoValido));

        assertEquals("Apenas pedidos entregues podem ser avaliados.", excecao.getMessage());
        verify(avaliacaoRepository, never()).save(any(Avaliacao.class));
    }

    @Test
    @DisplayName("Deve lançar exceção se pedido já foi avaliado")
    void deveLancarExcecaoSePedidoJaAvaliado() {
        when(usuarioRepository.findById("user_1")).thenReturn(Optional.of(usuarioLogado));
        when(pedidoRepository.findById("pedido_1")).thenReturn(Optional.of(pedido));
        when(avaliacaoRepository.existsByPedidoId("pedido_1")).thenReturn(true);

        Exception excecao = assertThrows(RegraDeNegocioException.class,
                () -> avaliacaoService.criarAvaliacao("user_1", dtoValido));

        assertEquals("Este pedido já foi avaliado.", excecao.getMessage());
        verify(avaliacaoRepository, never()).save(any(Avaliacao.class));
    }

    @Test
    @DisplayName("Deve listar avaliações por loja com sucesso")
    void deveListarAvaliacoesPorLoja() {
        when(lojaRepository.existsById("loja_1")).thenReturn(true);

        Avaliacao aval = new Avaliacao(5, "Bom", usuarioLogado, loja, pedido);
        aval.setId("aval_1");
        Page<Avaliacao> page = new PageImpl<>(List.of(aval));

        when(avaliacaoRepository.findByLojaId(eq("loja_1"), any())).thenReturn(page);

        Page<AvaliacaoResumoDTO> resultado = avaliacaoService.listarAvaliacoesPorLoja("loja_1", PageRequest.of(0, 10));

        assertFalse(resultado.isEmpty());
        assertEquals("aval_1", resultado.getContent().get(0).id());
        assertEquals(5, resultado.getContent().get(0).nota());
    }

    @Test
    @DisplayName("Deve lançar exceção ao listar avaliações de loja inexistente")
    void deveLancarExcecaoAoListarAvaliacoesLojaInexistente() {
        when(lojaRepository.existsById("loja_fantasma")).thenReturn(false);

        Exception excecao = assertThrows(IdNaoEncontradoException.class,
                () -> avaliacaoService.listarAvaliacoesPorLoja("loja_fantasma", PageRequest.of(0, 10)));

        assertEquals("A loja com o id: loja_fantasma não foi encontrada.", excecao.getMessage());
    }
}

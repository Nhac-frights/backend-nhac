package br.com.nhac.backend_nhac.domain.pedido;

import br.com.nhac.backend_nhac.AbstractIntegrationTest;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCreateDTO;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.infra.security.TokenService;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import br.com.nhac.backend_nhac.domain.pedido.StripePaymentService;
import br.com.nhac.backend_nhac.domain.pedido.dto.PedidoCriadoDTO;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PedidoFlowIT extends AbstractIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private ProdutoRepository produtoRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private TokenService tokenService;

    private Loja loja;
    private Produto produto;
    private Usuario usuario;
    private String token;

    @MockitoBean
    private StripePaymentService stripePaymentService;

    @Autowired
    private br.com.nhac.backend_nhac.domain.cupom.CupomService cupomService;

    @Test
    void cupomDescontaNoServidorReplayNaoConsomeNovamenteECancelamentoDevolve() throws Exception {
        var cupom = cupomService.ganharBoasVindas(usuario.getId());
        String body = """
            {"lojaId":"loja-123","formaPagamento":"DINHEIRO","cupomId":"%s",
             "enderecoEntrega":{"rua":"Rua Teste","numero":"123","bairro":"Centro","cidade":"Cidade","estado":"SP","cep":"00000-000"},
             "itens":[{"produtoId":"%s","nome":"Pizza","quantidade":1}]}
            """.formatted(cupom.id(), produto.getId());
        var result = mockMvc.perform(post("/api/v1/pedidos").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "pedido-cupom").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String pedidoId = objectMapper.readTree(result.getResponse().getContentAsString()).get("pedidoId").asText();
        var pedido = pedidoRepository.findById(pedidoId).orElseThrow();
        assertEquals(0, pedido.getValorTotal().compareTo(new BigDecimal("40.00")));
        assertEquals(0, pedido.getDesconto().compareTo(new BigDecimal("5.00")));
        assertEquals("USADO", cupomService.listar(usuario.getId()).getFirst().status());
        mockMvc.perform(post("/api/v1/pedidos").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "pedido-cupom").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/pedidos/" + pedidoId + "/cancelar")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        assertEquals("DISPONIVEL", cupomService.listar(usuario.getId()).getFirst().status());
    }

    @Test
    void falhaNoPagamentoNaoConsomeCupom() throws Exception {
        var cupom = cupomService.ganharBoasVindas(usuario.getId());
        Mockito.when(stripePaymentService.criarPaymentIntentCartao(Mockito.any()))
                .thenThrow(new RuntimeException("Gateway indisponível"));
        PedidoCreateDTO dto = new PedidoCreateDTO("loja-123", "CARTAO", null, null, null,
                new PedidoCreateDTO.EnderecoEntregaDTO("Rua Teste", "123", "Centro", "Cidade", "SP", "00000-000", null),
                cupom.id(), List.of(new PedidoCreateDTO.ItemPedidoDTO(produto.getId(), "Pizza", null, 1)));
        mockMvc.perform(post("/api/v1/pedidos").header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "falha-cupom").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto))).andExpect(status().isPaymentRequired());
        assertEquals("DISPONIVEL", cupomService.listar(usuario.getId()).getFirst().status());
    }

    @BeforeEach
    public void prepareData() {
        produtoRepository.deleteAll();
        lojaRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuario = new Usuario();
        usuario.setId(UUID.randomUUID().toString());
        usuario.setNome("Comprador Teste");
        usuario.setEmail("comprador@teste.com");
        usuario.setSenha("senha123");
        usuario.setTelefone("11999999999");
        usuarioRepository.save(usuario);

        token = tokenService.gerarToken(usuario);

        loja = new Loja();
        loja.setId("loja-123");
        loja.setNome("Pizzaria Nhac");
        loja.setAberto(true);
        br.com.nhac.backend_nhac.domain.loja.DadosOperacionais dadosOp = new br.com.nhac.backend_nhac.domain.loja.DadosOperacionais();
        dadosOp.setEntregaPropria(true);
        dadosOp.setRetiradaNoLocal(true);
        dadosOp.setTaxaEntregaBase(BigDecimal.ZERO);
        dadosOp.setTempoEntregaMin(10);
        dadosOp.setTempoEntregaMax(30);
        dadosOp.setRaioEntregaKm(new BigDecimal("10.0"));
        loja.setDadosOperacionais(dadosOp);
        loja.setEndereco(new br.com.nhac.backend_nhac.domain.loja.EnderecoLoja("Rua Teste", "123", "Cidade", "SP", "00000-000", "Bairro", null));
        lojaRepository.save(loja);

        // 4. Criar Produto
        produto = new Produto();
        produto.setId(UUID.randomUUID().toString());
        produto.setNome("Pizza de Calabresa");
        produto.setPreco(new BigDecimal("45.00"));
        produto.setAtivo(true);
        produto.setCategoriaMenu("Pizzas");
        produto.setLoja(loja);
        produtoRepository.save(produto);

        // Mock StripePaymentService - agora usa método para cartão/Google Pay
        Mockito.when(stripePaymentService.criarPaymentIntentCartao(Mockito.any(Pedido.class))).thenAnswer(invocation -> {
            Pedido pedidoSalvo = invocation.getArgument(0);
            return new PedidoCriadoDTO(pedidoSalvo.getId(), "mock-secret", null, null);
        });
    }

    @Test
    public void deveCriarEFinalizarPedidoComSucesso() throws Exception {
        PedidoCreateDTO.EnderecoEntregaDTO endereco = new PedidoCreateDTO.EnderecoEntregaDTO(
                "Rua Teste", "123", "Bairro", "Cidade", "SP", "12345-678", "Apto 1"
        );

        PedidoCreateDTO.ItemPedidoDTO itemDto = new PedidoCreateDTO.ItemPedidoDTO(
                produto.getId(), produto.getNome(), null, 2
        );

        PedidoCreateDTO pedidoDto = new PedidoCreateDTO(
                loja.getId(),
                "DINHEIRO",
                "Sem cebola",
                null,
                null,
                new PedidoCreateDTO.EnderecoEntregaDTO("Rua Teste", "123", "Bairro", "Cidade", "SP", "00000-000", null),
                null,
                List.of(itemDto)
        );

        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pedidoDto)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pedidoId").exists());
    }
    private PedidoCreateDTO criarPedidoDTO(int quantidade) {
        return new PedidoCreateDTO(
                loja.getId(),
                "DINHEIRO",
                "Sem cebola",
                null,
                null,
                new PedidoCreateDTO.EnderecoEntregaDTO(
                        "Rua Teste", "123", "Bairro", "Cidade", "SP", "00000-000", null),
                null,
                List.of(new PedidoCreateDTO.ItemPedidoDTO(
                        produto.getId(), "NOME ENVIADO PELO CLIENTE", "imagem-falsa", quantidade))
        );
    }

    @Test
    void deveRepetirMesmoPedidoParaMesmaIdempotencyKeyDoMesmoUsuario() throws Exception {
        PedidoCreateDTO dto = criarPedidoDTO(2);
        String key = "idem-" + UUID.randomUUID();

        MvcResult primeira = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult segunda = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andReturn();

        String id1 = objectMapper.readTree(primeira.getResponse().getContentAsString()).get("pedidoId").asText();
        String id2 = objectMapper.readTree(segunda.getResponse().getContentAsString()).get("pedidoId").asText();

        assertEquals(id1, id2);
        assertEquals(1, pedidoRepository.countByUsuarioId(usuario.getId()));
        assertEquals(98, produtoRepository.findById(produto.getId()).orElseThrow().getEstoque());
    }

    @Test
    void mesmaIdempotencyKeyPodeSerUsadaPorUsuariosDiferentes() throws Exception {
        PedidoCreateDTO dto = criarPedidoDTO(1);
        String key = "idem-compartilhada";

        Usuario outro = new Usuario();
        outro.setId(UUID.randomUUID().toString());
        outro.setNome("Outro Comprador");
        outro.setEmail("outro@teste.com");
        outro.setSenha("senha123");
        outro.setTelefone("11888888888");
        usuarioRepository.save(outro);
        String tokenOutro = tokenService.gerarToken(outro);

        MvcResult r1 = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated()).andReturn();

        MvcResult r2 = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenOutro)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated()).andReturn();

        String id1 = objectMapper.readTree(r1.getResponse().getContentAsString()).get("pedidoId").asText();
        String id2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("pedidoId").asText();

        assertNotEquals(id1, id2);
        assertEquals(1, pedidoRepository.countByUsuarioId(usuario.getId()));
        assertEquals(1, pedidoRepository.countByUsuarioId(outro.getId()));
    }

    @Test
    void requisicoesConcorrentesComMesmaKeyCriamSomenteUmPedido() throws Exception {
        PedidoCreateDTO dto = criarPedidoDTO(2);
        String body = objectMapper.writeValueAsString(dto);
        String key = "idem-concorrente-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch iniciarJuntas = new CountDownLatch(1);

        try {
            java.util.concurrent.Callable<MvcResult> chamada = () -> {
                iniciarJuntas.await();
                return mockMvc.perform(post("/api/v1/pedidos")
                                .header("Authorization", "Bearer " + token)
                                .header("Idempotency-Key", key)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
            };

            Future<MvcResult> f1 = executor.submit(chamada);
            Future<MvcResult> f2 = executor.submit(chamada);
            iniciarJuntas.countDown();

            MvcResult r1 = f1.get();
            MvcResult r2 = f2.get();

            assertTrue(List.of(200, 201).contains(r1.getResponse().getStatus()));
            assertTrue(List.of(200, 201).contains(r2.getResponse().getStatus()));

            String id1 = objectMapper.readTree(r1.getResponse().getContentAsString()).get("pedidoId").asText();
            String id2 = objectMapper.readTree(r2.getResponse().getContentAsString()).get("pedidoId").asText();

            assertEquals(id1, id2);
            assertEquals(1, pedidoRepository.countByUsuarioId(usuario.getId()));
            assertEquals(98, produtoRepository.findById(produto.getId()).orElseThrow().getEstoque());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void snapshotDoItemDeveIgnorarNomeEImagemEnviadosPeloCliente() throws Exception {
        produto.setImagemUrl("imagem-real");
        produtoRepository.save(produto);

        MvcResult resultado = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarPedidoDTO(1))))
                .andExpect(status().isCreated())
                .andReturn();

        String pedidoId = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("pedidoId").asText();

        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens[0].nome").value("Pizza de Calabresa"))
                .andExpect(jsonPath("$.itens[0].imagemUrl").value("imagem-real"))
                .andExpect(jsonPath("$.itens[0].preco").value(45.00));
    }


    @Test
    void mesmaIdempotencyKeyComPayloadDiferenteDeveRetornarConflito() throws Exception {
        String key = "idem-conflito-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarPedidoDTO(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(criarPedidoDTO(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("IDEMPOTENCIA_CONFLITO"));

        assertEquals(1, pedidoRepository.countByUsuarioId(usuario.getId()));
        assertEquals(99, produtoRepository.findById(produto.getId()).orElseThrow().getEstoque());
    }

}

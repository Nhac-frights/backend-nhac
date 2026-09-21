package br.com.nhac.backend_nhac.config;

import br.com.nhac.backend_nhac.domain.loja.DadosOperacionais;
import br.com.nhac.backend_nhac.domain.loja.EnderecoLoja;
import br.com.nhac.backend_nhac.domain.loja.FormasPagamento;
import br.com.nhac.backend_nhac.domain.loja.GeoLocalizacao;
import br.com.nhac.backend_nhac.domain.loja.HorariosFuncionamento;
import br.com.nhac.backend_nhac.domain.loja.Loja;
import br.com.nhac.backend_nhac.domain.loja.LojaRepository;
import br.com.nhac.backend_nhac.domain.produto.Produto;
import br.com.nhac.backend_nhac.domain.produto.ProdutoRepository;
import br.com.nhac.backend_nhac.domain.usuario.EnderecoUsuario;
import br.com.nhac.backend_nhac.domain.usuario.EnderecoUsuarioRepository;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("e2e")
public class E2EDataLoader implements CommandLineRunner {

    public static final String USER_ID = "e2e-cliente-001";
    public static final String STORE_ID = "e2e-loja-001";
    public static final String PRODUCT_ID = "e2e-produto-001";

    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final EnderecoUsuarioRepository enderecoRepository;
    private final LojaRepository lojaRepository;
    private final ProdutoRepository produtoRepository;
    private final PasswordEncoder passwordEncoder;

    public E2EDataLoader(
            JdbcTemplate jdbcTemplate,
            UsuarioRepository usuarioRepository,
            EnderecoUsuarioRepository enderecoRepository,
            LojaRepository lojaRepository,
            ProdutoRepository produtoRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
        this.enderecoRepository = enderecoRepository;
        this.lojaRepository = lojaRepository;
        this.produtoRepository = produtoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        limparDadosDaAplicacao();

        Usuario cliente = new Usuario();
        cliente.setId(USER_ID);
        cliente.setNome("Cliente E2E");
        cliente.setEmail("e2e.cliente@nhac.local");
        cliente.setTelefone("+5511999990001");
        cliente.setImagemUrl("");
        cliente.setSenha(passwordEncoder.encode("NhacE2E#123"));
        cliente.setEnderecos(new ArrayList<>());
        cliente.setPapel(Papel.CLIENTE);
        cliente.setTelefoneVerificado(true);
        cliente.setEmailVerificado(true);
        cliente.setAtivo(true);
        usuarioRepository.saveAndFlush(cliente);

        EnderecoUsuario endereco = new EnderecoUsuario(
                "e2e-endereco-001",
                cliente,
                "Praça da Sé",
                "100",
                "Sé",
                "São Paulo",
                "SP",
                "01001-000",
                "Fixture E2E",
                true
        );
        enderecoRepository.saveAndFlush(endereco);

        DadosOperacionais operacao = new DadosOperacionais();
        operacao.setAvaliacaoMedia(5.0f);
        operacao.setTaxaEntregaBase(new BigDecimal("5.00"));
        operacao.setTempoEntregaMin(20);
        operacao.setTempoEntregaMax(30);
        operacao.setTotalAvaliacoes(1);
        operacao.setEntregaPropria(true);
        operacao.setRetiradaNoLocal(false);
        operacao.setRaioEntregaKm(new BigDecimal("20.00"));

        HorariosFuncionamento horarios = new HorariosFuncionamento(
                "00:00-23:59", "00:00-23:59", "00:00-23:59",
                "00:00-23:59", "00:00-23:59", "00:00-23:59",
                "00:00-23:59"
        );

        Loja loja = Loja.builder()
                .id(STORE_ID)
                .nome("Loja E2E")
                .descricao("Loja determinística para testes E2E")
                .categoria("E2E")
                .imagemUrl("")
                .isAberto(true)
                .dadosOperacionais(operacao)
                .endereco(new EnderecoLoja(
                        "Praça da Sé", "1", "São Paulo", "SP", "01001-000",
                        "Sé", "Fixture E2E"))
                .geoLocalizacao(new GeoLocalizacao(-23.550520, -46.633308, "6gyf4"))
                .horariosFuncionamento(horarios)
                .formasPagamento(new FormasPagamento(true, true, true, true, false, false))
                .build();
        lojaRepository.saveAndFlush(loja);

        Produto produto = new Produto();
        produto.setId(PRODUCT_ID);
        produto.setLoja(loja);
        produto.setNome("Produto E2E");
        produto.setDescricao("Produto determinístico para o happy path E2E");
        produto.setPreco(new BigDecimal("25.00"));
        produto.setCategoriaMenu("Prato Principal");
        produto.setImagemUrl("");
        produto.setAtivo(true);
        produto.setCriadoEm(Instant.parse("2026-01-01T00:00:00Z"));
        produto.setPeso("300g");
        produto.setPercentualDesconto(0);
        produto.setEstoque(20);
        produtoRepository.saveAndFlush(produto);
    }

    private void limparDadosDaAplicacao() {
        jdbcTemplate.execute((Connection connection) -> {
            List<String> tabelas = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                                 "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
                while (result.next()) tabelas.add(result.getString(1));
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String tabela : tabelas) {
                    if (!"flyway_schema_history".equalsIgnoreCase(tabela)
                            && tabela.matches("[A-Za-z0-9_]+")) {
                        statement.execute("TRUNCATE TABLE `" + tabela + "`");
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }
}

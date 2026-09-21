package br.com.nhac.backend_nhac.domain.loja;

import br.com.nhac.backend_nhac.domain.loja.dto.LojaCreateDTO;
import br.com.nhac.backend_nhac.domain.loja.dto.LojaResumoDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LojaServiceFase4Test {

    @Mock
    private LojaRepository lojaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Spy
    private FreteService freteService = new FreteService();

    @InjectMocks
    private LojaService lojaService;

    @Test
    @DisplayName("Deve criar loja e gerar ID customizado seguro")
    void deveCriarLojaComIdCustomizado() {

        Loja lojaMock = new Loja();
        lojaMock.setId("loja_0006");
        lojaMock.setNome("Nova Loja");
        lojaMock.setImagemUrl("img.jpg");
        lojaMock.setUsuarioId("user_1");
        DadosOperacionais dadosMock = new DadosOperacionais();
        dadosMock.setAvaliacaoMedia(0.0f);
        lojaMock.setDadosOperacionais(dadosMock);

        when(lojaRepository.save(any(Loja.class))).thenReturn(lojaMock);

        LojaCreateDTO.DadosOperacionaisDTO dadosOp = new LojaCreateDTO.DadosOperacionaisDTO(new BigDecimal("5.0"), 30, 45, true, false, null);
        LojaCreateDTO.EnderecoDTO endereco = new LojaCreateDTO.EnderecoDTO("Rua X", "123", "Cidade", "SP", "01234-567", "Centro", null);
        LojaCreateDTO.HorariosDTO horarios = new LojaCreateDTO.HorariosDTO("F", "F", "F", "F", "F", "F", "F");
        LojaCreateDTO.FormasPagamentoDTO formasPagamento = new LojaCreateDTO.FormasPagamentoDTO(true, true, true, true, false, false);

        LojaCreateDTO dto = new LojaCreateDTO(
            "Nova Loja", "Desc", "Categoria", "img.jpg", true,
            dadosOp, endereco, horarios, formasPagamento
        );

        Usuario usuarioLogado = new Usuario();
        usuarioLogado.setId("user_1");
        usuarioLogado.setPapel(Papel.CLIENTE);

        LojaResumoDTO resumo = lojaService.criarLoja(dto, usuarioLogado);

        assertTrue(resumo.id().startsWith("loja_"));
        assertEquals("Nova Loja", resumo.nome());
        assertEquals(Papel.LOJISTA, usuarioLogado.getPapel());

        verify(lojaRepository).save(argThat(loja ->
                loja.getId() != null && loja.getId().startsWith("loja_") && "user_1".equals(loja.getUsuarioId())));
    }
}

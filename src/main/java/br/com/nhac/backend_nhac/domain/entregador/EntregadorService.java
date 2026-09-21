package br.com.nhac.backend_nhac.domain.entregador;

import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarLocalizacaoDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.AtualizarStatusDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.CadastroEntregadorDTO;
import br.com.nhac.backend_nhac.domain.entregador.dto.EntregadorResponseDTO;
import br.com.nhac.backend_nhac.domain.usuario.Papel;
import br.com.nhac.backend_nhac.domain.usuario.Usuario;
import br.com.nhac.backend_nhac.domain.usuario.UsuarioRepository;
import br.com.nhac.backend_nhac.exceptions.IdNaoEncontradoException;
import br.com.nhac.backend_nhac.exceptions.RegraDeNegocioException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class EntregadorService {

    private final EntregadorRepository entregadorRepository;
    private final UsuarioRepository usuarioRepository;

    @Value("${nhac.entrega.localizacao-max-age-seconds:120}")
    private long localizacaoMaxAgeSeconds = 120;

    public EntregadorService(EntregadorRepository entregadorRepository, UsuarioRepository usuarioRepository) {
        this.entregadorRepository = entregadorRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public EntregadorResponseDTO cadastrar(CadastroEntregadorDTO dto, Usuario usuario) {
        if (entregadorRepository.existsByUsuarioId(usuario.getId())) {
            throw new RegraDeNegocioException("Este usuário já possui cadastro como entregador.");
        }

        // LOJISTA/FUNCIONARIO/ADMIN não viram entregador pela mesma conta —
        // decisão de produto: essas contas já têm outro vínculo forte com o
        // sistema. CLIENTE é o único papel que pode "ganhar" o cadastro de
        // entregador sem perder nada (ver nota abaixo).
        if (usuario.getPapel() == Papel.LOJISTA || usuario.getPapel() == Papel.FUNCIONARIO) {
            throw new RegraDeNegocioException("Contas de loja não podem se cadastrar como entregador.");
        }

        Entregador entregador = Entregador.builder()
                .id(UUID.randomUUID().toString())
                .usuario(usuario)
                .cnh(dto.cnh())
                .placaVeiculo(dto.placaVeiculo())
                .tipoVeiculo(dto.tipoVeiculo())
                .statusOperacional(StatusOperacional.OFFLINE)
                .ativo(true)
                .criadoEm(Instant.now())
                .build();

        // CORREÇÃO: antes, isto sobrescrevia usuario.papel para ENTREGADOR —
        // uma conta CLIENTE que virava entregadora perdia o papel de
        // CLIENTE (e com ele, o acesso a qualquer rota que checasse
        // Papel.CLIENTE, como abrir conversa no chat do app do cliente).
        // O vínculo com tb_entregadores já é suficiente para saber que esta
        // conta também é entregadora — ver Usuario.getAuthorities() em
        // Usuario.java e o uso de existsByUsuarioIdAndAtivoTrue em
        // SecurityFilter/StompAuthChannelInterceptor, que somam
        // ROLE_ENTREGADOR às authorities sem depender deste campo.

        Entregador salvo = entregadorRepository.save(entregador);
        return new EntregadorResponseDTO(salvo);
    }

    @Transactional(readOnly = true)
    public EntregadorResponseDTO obterPerfil(Usuario usuario) {
        Entregador entregador = buscarPorUsuario(usuario);
        return new EntregadorResponseDTO(entregador);
    }

    @Transactional
    public EntregadorResponseDTO atualizarStatus(AtualizarStatusDTO dto, Usuario usuario) {
        Entregador entregador = buscarPorUsuario(usuario);

        if (!entregador.isAtivo()) {
            throw new RegraDeNegocioException("Entregador com cadastro inativo não pode alterar status.");
        }

        if (dto.statusOperacional() == StatusOperacional.EM_ENTREGA) {
            throw new RegraDeNegocioException(
                    "EM_ENTREGA é controlado pelo backend e não pode ser definido manualmente.");
        }

        if (entregador.getStatusOperacional() == StatusOperacional.EM_ENTREGA) {
            throw new RegraDeNegocioException(
                    "Não é possível alterar manualmente o status durante uma entrega ativa.");
        }

        entregador.setStatusOperacional(dto.statusOperacional());
        Entregador salvo = entregadorRepository.save(entregador);
        return new EntregadorResponseDTO(salvo);
    }

    @Transactional
    public EntregadorResponseDTO atualizarLocalizacao(AtualizarLocalizacaoDTO dto, Usuario usuario) {
        Entregador entregador = buscarPorUsuario(usuario);
        entregador.atualizarLocalizacao(dto.latitude(), dto.longitude());
        Entregador salvo = entregadorRepository.save(entregador);
        return new EntregadorResponseDTO(salvo);
    }

    @Transactional(readOnly = true)
    public List<EntregadorComDistancia> buscarEntregadoresProximos(Double lojaLat, Double lojaLng, double raioMaximoKm) {
        if (lojaLat == null || lojaLng == null) {
            return List.of();
        }

        List<Entregador> online = entregadorRepository.findByStatusOperacionalAndAtivoTrue(StatusOperacional.ONLINE);

        Instant limiteLocalizacao = Instant.now().minusSeconds(localizacaoMaxAgeSeconds);

        return online.stream()
                .filter(e -> e.getLatitudeAtual() != null && e.getLongitudeAtual() != null)
                .filter(e -> e.getUltimaAtualizacaoLocalizacao() != null
                        && !e.getUltimaAtualizacaoLocalizacao().isBefore(limiteLocalizacao))
                .map(e -> {
                    double dist = calcularDistanciaKm(lojaLat, lojaLng, e.getLatitudeAtual(), e.getLongitudeAtual());
                    return new EntregadorComDistancia(e, dist);
                })
                .filter(item -> item.distanciaKm() <= raioMaximoKm)
                .sorted(Comparator.comparingDouble(EntregadorComDistancia::distanciaKm))
                .toList();
    }

    @Transactional(readOnly = true)
    public Entregador buscarPorUsuario(Usuario usuario) {
        return entregadorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IdNaoEncontradoException("Perfil de entregador não encontrado para o usuário logado."));
    }

    /**
     * Variante sem exceção, usada em pontos que não podem lançar em cima de um
     * usuário que talvez nem seja entregador — como o StompAuthChannelInterceptor
     * checando dono de canal de ofertas (V039), onde levantar IdNaoEncontradoException
     * ali derrubaria a conexão WebSocket em vez de simplesmente negar o SUBSCRIBE.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Entregador> buscarPorUsuarioOuNulo(Usuario usuario) {
        return entregadorRepository.findByUsuarioId(usuario.getId());
    }

    public static double calcularDistanciaKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Raio médio da Terra em km
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public record EntregadorComDistancia(Entregador entregador, double distanciaKm) {
    }
}

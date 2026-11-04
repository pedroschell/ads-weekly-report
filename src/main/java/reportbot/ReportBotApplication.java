package reportbot;

import com.fasterxml.jackson.databind.ObjectMapper;

import reportbot.MetaClient.Campanha;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Coleta Meta + Google, monta o report, envia ao Discord, registra no HISTORICO.md e commita.
 * Uso: ./report.command [AAAA-MM-DD] [--previa]
 */
@SpringBootApplication
@EnableConfigurationProperties(ReportProperties.class)
public class ReportBotApplication implements CommandLineRunner {

    private static final int LIMITE_DISCORD = 1900;

    private final ReportProperties props;
    private final MetaClient meta;
    private final GoogleAdsClient google;

    public ReportBotApplication(ReportProperties props, MetaClient meta, GoogleAdsClient google) {
        this.props = props;
        this.meta = meta;
        this.google = google;
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext contexto = SpringApplication.run(ReportBotApplication.class, args);
        int codigoDeSaida = SpringApplication.exit(contexto);
        System.exit(codigoDeSaida);
    }

    @Override
    public void run(String... args) throws Exception {

        boolean previa = false;
        LocalDate hoje = LocalDate.now();
        for (String argumento : args) {
            if (argumento.equals("--previa")) {
                previa = true;
            } else {
                hoje = LocalDate.parse(argumento);
            }
        }

        Periodo periodo = Periodo.para(hoje);
        System.out.println("Periodo: " + periodo);

        String texto = montarTexto(periodo);

        if (previa) {
            System.out.println(texto);
            System.out.println();
            System.out.println("(previa: nada foi enviado)");
            return;
        }

        boolean enviado = false;
        try {
            enviarDiscord(cabecalho(periodo), texto);
            enviado = true;
            System.out.println("Enviado ao Discord.");
        } finally {
            registrarDisparo(periodo, enviado);
        }
    }

    /** Report do período, a partir das APIs. */
    private String montarTexto(Periodo periodo) throws Exception {
        List<Campanha> campanhasMeta = meta.campanhas(periodo);
        Map<String, Double> investimentoGoogle = google.investimentoPorCampanha(periodo);

        Map<String, Double> destaques = new LinkedHashMap<>();
        for (Map.Entry<String, String> destaque : props.destaques().entrySet()) {
            String rotulo = destaque.getKey();
            String filtro = destaque.getValue();
            destaques.put(rotulo, somarCampanhasComNome(campanhasMeta, filtro));
        }

        double totalGoogle = 0;
        for (double custo : investimentoGoogle.values()) {
            totalGoogle = totalGoogle + custo;
        }

        double pauaAtual = investimentoPaua(periodo);
        double pauaMesAnterior = investimentoPaua(periodo.deslocado(-1));
        double pauaAnoAnterior = investimentoPaua(periodo.deslocado(-12));

        Map<String, List<Campanha>> grupos = Relatorio.agrupar(campanhasMeta, props.grupos());

        return Relatorio.mensagem(grupos, props.planejado(), totalGoogle, destaques, periodo,
                pauaAtual, pauaMesAnterior, pauaAnoAnterior);
    }

    /** Investimento da conta do Pauá, sem as campanhas de "excluir". NaN se a conta não estiver configurada. */
    private double investimentoPaua(Periodo periodo) throws Exception {
        ReportProperties.Paua config = props.paua();
        if (config == null) {
            return Double.NaN;
        }
        if (config.adAccountId() == null || !config.adAccountId().matches("act_\\d+")) {
            return Double.NaN;
        }

        List<Campanha> campanhas = meta.campanhas(config.adAccountId(), config.vaultSlot(), periodo);

        double total = 0;
        for (Campanha campanha : campanhas) {
            boolean deveExcluir = nomeContemAlgum(campanha.nome(), config.excluir());
            if (!deveExcluir) {
                total = total + campanha.investimento();
            }
        }
        return total;
    }

    /** Nome contém algum dos termos (sem diferenciar maiúsculas). */
    private static boolean nomeContemAlgum(String nome, List<String> termos) {
        String nomeMinusculo = nome.toLowerCase();
        for (String termo : termos) {
            if (nomeMinusculo.contains(termo.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** Investimento das campanhas cujo nome contém o filtro. */
    private static double somarCampanhasComNome(List<Campanha> campanhas, String filtro) {
        double total = 0;
        for (Campanha campanha : campanhas) {
            if (campanha.nome().toLowerCase().contains(filtro.toLowerCase())) {
                total = total + campanha.investimento();
            }
        }
        return total;
    }

    /** Cabeçalho da mensagem no Discord. */
    private String cabecalho(Periodo periodo) {
        String descricaoPeriodo;
        if (periodo.fechamento()) {
            descricaoPeriodo = "fechamento de " + periodo.rotuloMes();
        } else {
            descricaoPeriodo = periodo.toString();
        }
        return "**" + props.cliente() + " · " + descricaoPeriodo + "**\n"
                + "Confira e encaminhe ao cliente (copie o bloco abaixo).";
    }

    /** Envia o report em bloco de código, dividido em mensagens de até LIMITE_DISCORD caracteres. */
    private void enviarDiscord(String cabecalho, String texto) throws IOException, InterruptedException {
        List<String> pedacos = new ArrayList<>();
        StringBuilder pedacoAtual = new StringBuilder();
        for (String linha : texto.split("\n")) {
            boolean naoCabe = pedacoAtual.length() + linha.length() + 1 > LIMITE_DISCORD;
            if (naoCabe) {
                pedacos.add(pedacoAtual.toString());
                pedacoAtual = new StringBuilder();
            }
            pedacoAtual.append(linha);
            pedacoAtual.append("\n");
        }
        pedacos.add(pedacoAtual.toString());

        HttpClient http = HttpClient.newHttpClient();
        ObjectMapper json = new ObjectMapper();
        for (int i = 0; i < pedacos.size(); i++) {
            String conteudo = "```\n" + pedacos.get(i) + "```";
            if (i == 0) {
                conteudo = cabecalho + "\n" + conteudo;
            }

            String corpo = json.writeValueAsString(Map.of("content", conteudo));
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(props.discordWebhook()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();

            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() >= 300) {
                throw new IOException("Discord respondeu " + resposta.statusCode() + ": " + resposta.body());
            }
        }
    }

    /** Registra o disparo no HISTORICO.md e commita se o envio deu certo. */
    private void registrarDisparo(Periodo periodo, boolean enviado) throws IOException, InterruptedException {
        Path historico = Path.of("HISTORICO.md");

        if (!Files.exists(historico)) {
            Files.writeString(historico, "# Historico de disparos\n\n| # | Data | Periodo | Status |\n|---|---|---|---|\n");
        }

        int numero = 1;
        for (String linha : Files.readAllLines(historico)) {
            if (linha.matches("\\| \\d+ .*")) {
                numero = numero + 1;
            }
        }

        String agora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String status;
        if (enviado) {
            status = "enviado";
        } else {
            status = "falhou";
        }
        String novaLinha = "| " + numero + " | " + agora + " | " + periodo + " | " + status + " |\n";
        Files.writeString(historico, novaLinha, StandardOpenOption.APPEND);
        System.out.println("Disparo #" + numero + " registrado.");

        if (!enviado || !props.commitAutomatico()) {
            return;
        }
        if (!Files.isDirectory(Path.of(".git"))) {
            System.out.println("Aviso: report-bot ainda nao tem repo proprio (git init); commit pulado.");
            return;
        }
        rodarGit("add", "HISTORICO.md");
        rodarGit("commit", "-m", "report: disparo #" + numero + " (" + periodo + ")");
        rodarGit("push");
    }

    /** Executa um comando git. */
    private static void rodarGit(String... argumentos) throws IOException, InterruptedException {
        List<String> comando = new ArrayList<>();
        comando.add("git");
        for (String argumento : argumentos) {
            comando.add(argumento);
        }

        Process processo = new ProcessBuilder(comando).inheritIO().start();
        int codigo = processo.waitFor();
        if (codigo != 0) {
            System.out.println("Aviso: git " + argumentos[0] + " terminou com codigo " + codigo);
        }
    }
}

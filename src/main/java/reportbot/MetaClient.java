package reportbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Investimento e leads por campanha na Graph API do Meta (somente leitura). */
@Component
public class MetaClient {

    public record Campanha(String nome, double investimento, double leads) {}

    private final ReportProperties props;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public MetaClient(ReportProperties props) {
        this.props = props;
    }

    /** Campanhas da conta principal do cliente. */
    public List<Campanha> campanhas(Periodo periodo) throws IOException, InterruptedException {
        String conta = props.meta().adAccountId();
        String slot = props.meta().vaultSlot();
        return campanhas(conta, slot, periodo);
    }

    /** Campanhas de qualquer conta. */
    public List<Campanha> campanhas(String conta, String slotDoToken, Periodo periodo) throws IOException, InterruptedException {
        ReportProperties.Meta config = props.meta();
        String token = lerTokenDoCofre(config.vaultModule(), slotDoToken);

        String intervalo = "{\"since\":\"" + periodo.inicio() + "\",\"until\":\"" + periodo.fim() + "\"}";
        String url = "https://graph.facebook.com/" + config.apiVersion() + "/" + conta + "/insights"
                + "?level=campaign"
                + "&fields=campaign_name,spend,actions"
                + "&limit=500"
                + "&time_range=" + URLEncoder.encode(intervalo, StandardCharsets.UTF_8);

        List<Campanha> resultado = new ArrayList<>();

        while (url != null) {
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            JsonNode pagina = json.readTree(resposta.body());

            if (pagina.has("error")) {
                String mensagemDeErro = pagina.path("error").path("message").asText();
                throw new IOException("Meta: " + mensagemDeErro);
            }

            for (JsonNode linha : pagina.path("data")) {
                String nome = linha.path("campaign_name").asText();
                double investimento = linha.path("spend").asDouble();
                double leads = contarLeads(linha.path("actions"), config.tiposDeLead());
                resultado.add(new Campanha(nome, investimento, leads));
            }

            url = pagina.path("paging").path("next").asText(null);
        }

        return resultado;
    }

    /** Soma as ações dos tipos configurados em tipos-de-lead. */
    private double contarLeads(JsonNode acoes, List<String> tiposDeLead) {
        double leads = 0;
        for (JsonNode acao : acoes) {
            String tipo = acao.path("action_type").asText();
            if (tiposDeLead.contains(tipo)) {
                leads = leads + acao.path("value").asDouble();
            }
        }
        return leads;
    }

    /** Token do cofre do MATRIX Pack, via pwsh. Fica só em memória. */
    private String lerTokenDoCofre(String moduloDoCofre, String slot) throws IOException, InterruptedException {
        String comandoPowerShell = "Import-Module '" + moduloDoCofre + "' -DisableNameChecking -WarningAction SilentlyContinue; "
                + "[Console]::Out.Write((Get-VaultSecret -Name '" + slot + "'))";

        ProcessBuilder construtor = new ProcessBuilder("pwsh", "-NoProfile", "-NonInteractive", "-Command", comandoPowerShell);
        construtor.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process processo = construtor.start();

        byte[] saida = processo.getInputStream().readAllBytes();
        String token = new String(saida, StandardCharsets.UTF_8).trim();
        int codigo = processo.waitFor();

        if (codigo != 0 || token.isEmpty()) {
            throw new IOException("Nao foi possivel ler o token Meta do cofre.");
        }
        return token;
    }
}

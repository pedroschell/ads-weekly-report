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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Investimento por campanha no Google Ads (somente leitura). */
@Component
public class GoogleAdsClient {

    private final ReportProperties props;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    public GoogleAdsClient(ReportProperties props) {
        this.props = props;
    }

    /** Nome da campanha -> investimento no período. */
    public Map<String, Double> investimentoPorCampanha(Periodo periodo) throws IOException, InterruptedException {
        ReportProperties.Google config = props.google();

        String caminho = config.credenciaisEnv().replaceFirst("^~", System.getProperty("user.home"));
        Map<String, String> credenciais = lerArquivoEnv(Path.of(caminho));
        String accessToken = pedirAccessToken(credenciais);

        String consulta = "SELECT campaign.name, metrics.cost_micros FROM campaign"
                + " WHERE segments.date BETWEEN '" + periodo.inicio() + "' AND '" + periodo.fim() + "'"
                + " AND metrics.cost_micros > 0";
        String corpo = json.writeValueAsString(Map.of("query", consulta));

        String url = "https://googleads.googleapis.com/" + config.apiVersion()
                + "/customers/" + config.customerId() + "/googleAds:searchStream";
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("login-customer-id", credenciais.get("LOGIN_CUSTOMER_ID"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        JsonNode blocos = json.readTree(resposta.body());

        if (!blocos.isArray()) {
            throw new IOException("Google Ads: " + blocos.toString());
        }

        Map<String, Double> resultado = new HashMap<>();
        for (JsonNode bloco : blocos) {
            for (JsonNode linha : bloco.path("results")) {
                String nome = linha.path("campaign").path("name").asText();
                double custo = linha.path("metrics").path("costMicros").asDouble() / 1_000_000.0;

                double jaSomado = resultado.getOrDefault(nome, 0.0);
                resultado.put(nome, jaSomado + custo);
            }
        }
        return resultado;
    }

    /** Troca o refresh token por um access token. */
    private String pedirAccessToken(Map<String, String> credenciais) throws IOException, InterruptedException {
        String formulario = "grant_type=refresh_token"
                + "&client_id=" + codificar(credenciais.get("CLIENT_ID"))
                + "&client_secret=" + codificar(credenciais.get("CLIENT_SECRET"))
                + "&refresh_token=" + codificar(credenciais.get("REFRESH_TOKEN"));

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formulario))
                .build();
        HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
        JsonNode corpo = json.readTree(resposta.body());

        if (!corpo.has("access_token")) {
            throw new IOException("Google OAuth recusou o refresh token.");
        }
        return corpo.get("access_token").asText();
    }

    /** Lê um arquivo CHAVE=valor. */
    private static Map<String, String> lerArquivoEnv(Path arquivo) throws IOException {
        Map<String, String> valores = new HashMap<>();

        for (String linha : Files.readAllLines(arquivo)) {
            linha = linha.strip();
            if (linha.isEmpty() || linha.startsWith("#")) {
                continue;
            }

            int posicaoDoIgual = linha.indexOf('=');
            if (posicaoDoIgual <= 0) {
                continue;
            }

            String chave = linha.substring(0, posicaoDoIgual).strip();
            String valor = linha.substring(posicaoDoIgual + 1).strip();
            valor = valor.replaceAll("^[\"']|[\"']$", "");

            valores.put(chave, valor);
        }
        return valores;
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }
}

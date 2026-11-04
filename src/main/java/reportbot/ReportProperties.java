package reportbot;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuração "report:" do application.yml e do config/local.yml. */
@ConfigurationProperties(prefix = "report")
public record ReportProperties(
        String cliente,
        String discordWebhook,
        Meta meta,
        Google google,
        List<Grupo> grupos,
        Map<String, String> destaques,
        Paua paua,
        Map<String, Double> planejado,
        boolean commitAutomatico) {
    /** Listas e mapas ausentes no YAML viram vazios. */
    public ReportProperties {
        if (grupos == null) {
            grupos = List.of();
        }
        if (destaques == null) {
            destaques = Map.of();
        }
        if (planejado == null) {
            planejado = Map.of();
        }
    }

    public record Meta(String adAccountId, String vaultSlot, String vaultModule, String apiVersion,
                       List<String> tiposDeLead) {}

    public record Google(String customerId, String credenciaisEnv, String apiVersion) {}

    public record Paua(String adAccountId, String vaultSlot, List<String> excluir) {
        public Paua {
            if (excluir == null) {
                excluir = List.of();
            }
        }
    }

    public record Grupo(String titulo, List<String> filtros) {}
}

package reportbot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import reportbot.MetaClient.Campanha;

/** Agrupamento das campanhas e texto do report. */
public final class Relatorio {

    private static final Locale BR = Locale.of("pt", "BR");
    private static final String OUTRAS = "Outras campanhas";

    private Relatorio() {
    }

    /** Campanhas por grupo, na ordem do config; vence o filtro mais longo contido no nome. */
    public static Map<String, List<Campanha>> agrupar(List<Campanha> campanhas, List<ReportProperties.Grupo> grupos) {
        Map<String, List<Campanha>> resultado = new LinkedHashMap<>();
        for (ReportProperties.Grupo grupo : grupos) {
            resultado.put(grupo.titulo(), new ArrayList<>());
        }
        resultado.put(OUTRAS, new ArrayList<>());

        for (Campanha campanha : campanhas) {
            if (campanha.investimento() <= 0) {
                continue;
            }

            String grupoEscolhido = grupoDaCampanha(campanha.nome(), grupos);
            resultado.get(grupoEscolhido).add(campanha);
        }

        Map<String, List<Campanha>> semVazios = new LinkedHashMap<>();
        for (Map.Entry<String, List<Campanha>> entrada : resultado.entrySet()) {
            List<Campanha> lista = entrada.getValue();
            if (!lista.isEmpty()) {
                lista.sort((a, b) -> Double.compare(b.investimento(), a.investimento()));
                semVazios.put(entrada.getKey(), lista);
            }
        }
        return semVazios;
    }

    /** Título do grupo da campanha, ou "Outras campanhas". */
    private static String grupoDaCampanha(String nomeDaCampanha, List<ReportProperties.Grupo> grupos) {
        String nome = nomeDaCampanha.toLowerCase(BR);
        String melhorGrupo = OUTRAS;
        int tamanhoDoMelhorFiltro = 0;

        for (ReportProperties.Grupo grupo : grupos) {
            for (String filtro : grupo.filtros()) {
                boolean contem = nome.contains(filtro.toLowerCase(BR));
                boolean maisEspecifico = filtro.length() > tamanhoDoMelhorFiltro;
                if (contem && maisEspecifico) {
                    melhorGrupo = grupo.titulo();
                    tamanhoDoMelhorFiltro = filtro.length();
                }
            }
        }
        return melhorGrupo;
    }

    /** Report completo, com formatação do WhatsApp. */
    public static String mensagem(Map<String, List<Campanha>> grupos, Map<String, Double> planejado,
                                  double totalGoogle, Map<String, Double> destaques, Periodo periodo,
                                  double pauaAtual, double pauaMesAnterior, double pauaAnoAnterior) {
        StringBuilder texto = new StringBuilder();
        texto.append("Resultados por campanha:\n");
        texto.append("\n");
        texto.append("*Meta Ads*\n");

        for (Map.Entry<String, List<Campanha>> grupo : grupos.entrySet()) {
            texto.append("\n");
            texto.append(textoDoGrupo(grupo.getKey(), grupo.getValue(), planejado, periodo));
        }

        texto.append("\n");
        texto.append("• Google Ads: ").append(reaisComCentavos(totalGoogle)).append("\n");

        for (Map.Entry<String, Double> destaque : destaques.entrySet()) {
            texto.append("• ").append(destaque.getKey()).append(": ").append(reaisComCentavos(destaque.getValue())).append("\n");
        }

        if (Double.isNaN(pauaAtual)) {
            texto.append("• Pauá: (conta ainda não configurada no bot)");
            return texto.toString();
        }

        texto.append("• Pauá:\n");
        texto.append("• ").append(periodo.rotuloMes()).append(": ").append(reaisCurto(pauaAtual)).append("\n");
        texto.append("• ").append(periodo.deslocado(-1).rotuloMes()).append(": ").append(reaisCurto(pauaMesAnterior)).append("\n");
        texto.append("• ").append(periodo.deslocado(-12).rotuloMes()).append(": ").append(reaisCurto(pauaAnoAnterior));
        return texto.toString();
    }

    /** Linha de total do grupo seguida de uma linha por campanha. */
    private static String textoDoGrupo(String titulo, List<Campanha> campanhas, Map<String, Double> planejado, Periodo periodo) {
        double investimentoTotal = 0;
        double leadsTotal = 0;
        for (Campanha campanha : campanhas) {
            investimentoTotal = investimentoTotal + campanha.investimento();
            leadsTotal = leadsTotal + campanha.leads();
        }

        StringBuilder texto = new StringBuilder();
        texto.append("*").append(titulo).append("* - ").append(reais(investimentoTotal));
        texto.append(textoLeads(leadsTotal));
        texto.append("\n");

        for (Campanha campanha : campanhas) {
            texto.append("- ").append(nomeCurto(campanha.nome())).append(": ").append(reais(campanha.investimento()));
            texto.append(textoLeads(campanha.leads()));
            texto.append("\n");
        }
        return texto.toString();
    }

    /** " - 1.435 leads - CPL 25,9" */
    private static String textoLeads(double leads) {
        if (leads <= 0) {
            return "";
        }

        String palavra = "leads";
        if (Math.round(leads) == 1) {
            palavra = "lead";
        }

        return " - " + inteiro(leads) + " " + palavra;
    }

    /** "pareto.in.fba.convs{Time SDR} - Advantage+" -> "Time SDR" */
    static String nomeCurto(String nome) {
        String curto = nome;
        curto = curto.replaceFirst("^pareto\\.[a-z]+\\.[a-z]+\\.[a-z]+\\s*", "");
        curto = curto.replace("{", "");
        curto = curto.replace("}", " ");
        curto = curto.replace(" - Advantage+", "");
        curto = curto.replace("- Advantage+", "");
        curto = curto.replaceAll("\\s+", " ");
        curto = curto.replaceFirst("^-\\s*", "");
        curto = curto.strip();

        if (curto.isEmpty()) {
            return nome;
        }
        return curto;
    }

    /** R$ 37.146 */
    static String reais(double valor) {
        NumberFormat formato = NumberFormat.getIntegerInstance(BR);
        return "R$ " + formato.format(Math.round(valor));
    }

    /** R$ 21.050,53 */
    static String reaisComCentavos(double valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(BR);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return "R$ " + formato.format(valor);
    }

    /** R$ 6k / R$ 5,8k / R$ 834 */
    static String reaisCurto(double valor) {
        if (valor < 1000) {
            return reais(valor);
        }

        String milhares = decimal(valor / 1000);
        if (milhares.endsWith(",0")) {
            milhares = milhares.substring(0, milhares.length() - 2);
        }
        return "R$ " + milhares + "k";
    }

    /** 25,9 */
    static String decimal(double valor) {
        NumberFormat formato = NumberFormat.getNumberInstance(BR);
        formato.setMinimumFractionDigits(1);
        formato.setMaximumFractionDigits(1);
        return formato.format(valor);
    }

    /** 1.435 */
    static String inteiro(double valor) {
        NumberFormat formato = NumberFormat.getIntegerInstance(BR);
        return formato.format(Math.round(valor));
    }
}

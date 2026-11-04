package reportbot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/** Janela do report: reunião na quinta feira, em caso de ser a primeira semana do mês, o report consolida o fechamento do mês anterior. */
public record Periodo(LocalDate inicio, LocalDate fim, boolean fechamento) {

    public static Periodo para(LocalDate hoje) {
        LocalDate ontem = hoje.minusDays(1);

        boolean ehQuinta = hoje.getDayOfWeek() == DayOfWeek.THURSDAY;
        boolean ehPrimeiraSemana = hoje.getDayOfMonth() <= 7;
        boolean ehPrimeiraQuinta = ehQuinta && ehPrimeiraSemana;
        boolean ontemFoiOutroMes;

        if (ontem.getMonthValue() != hoje.getMonthValue()) {
            ontemFoiOutroMes = true;
        }
        else ontemFoiOutroMes = false;

        if (ehPrimeiraSemana || ontemFoiOutroMes || ehPrimeiraQuinta) {
            YearMonth mesAnterior = YearMonth.from(hoje).minusMonths(1);
            LocalDate primeiroDia = mesAnterior.atDay(1);
            LocalDate ultimoDia = mesAnterior.atEndOfMonth();
            return new Periodo(primeiroDia, ultimoDia, true);
        }

        LocalDate diaPrimeiro = hoje.withDayOfMonth(1);
        return new Periodo(diaPrimeiro, ontem, false);
    }

    /** Mesma janela deslocada N meses. */
    public Periodo deslocado(int meses) {
        YearMonth mesDestino = YearMonth.from(inicio).plusMonths(meses);
        LocalDate novoInicio = mesDestino.atDay(1);

        LocalDate novoFim;
        if (fechamento) {
            novoFim = mesDestino.atEndOfMonth();
        } else {
            int diaFinal = Math.min(fim.getDayOfMonth(), mesDestino.lengthOfMonth());
            novoFim = mesDestino.atDay(diaFinal);
        }

        return new Periodo(novoInicio, novoFim, fechamento);
    }

    /** Dias do mês dentro do período. */
    public int diasCorridos() {
        return fim.getDayOfMonth();
    }

    /** Total de dias do mês. */
    public int diasNoMes() {
        return YearMonth.from(inicio).lengthOfMonth();
    }

    /** "Outubro/26" */
    public String rotuloMes() {
        String mes = inicio.getMonth().getDisplayName(TextStyle.FULL, Locale.of("pt", "BR"));
        String mesComMaiuscula = mes.substring(0, 1).toUpperCase() + mes.substring(1);
        String anoComDoisDigitos = String.valueOf(inicio.getYear()).substring(2);
        return mesComMaiuscula + "/" + anoComDoisDigitos;
    }

    @Override
    public String toString() {
        String texto = inicio + " a " + fim;
        if (fechamento) {
            texto = texto + " (fechamento)";
        }
        return texto;
    }
}

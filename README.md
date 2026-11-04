# report-bot

Bot de report semanal de mídia paga. Ele roda uma vez e fecha. Lê o Meta Ads e o Google Ads pelas APIs, monta as tabelas
de campanhas e o texto da mensagem, tudo em texto,, e posta tudo num canal privado do Discord para conferência antes do envio
ao cliente.

- **Período:** do dia 1º do mês até ontem. Na primeira quinta do mês, o mês anterior fechado.
- **Comparativos:** a mesma janela no mês anterior e no mesmo mês do ano anterior.
- **Histórico:** cada disparo vira uma linha em `HISTORICO.md` e um commit automático. Nenhum dado de cliente entra no repositório.

## Stack
Java 21 · Spring Boot 3 (sem servidor web) · `java.net.http` · webhook do Discord.

## Rodar
```bash
cp config/local.example.yml config/local.yml   # preencha
./report.command              # Mac (ou report.bat no Windows)
./report.command 2026-11-12   # simula outra data
```
Requisitos: JDK 21, Maven, `pwsh` com o cofre token do Meta + credenciais do Google Ads no arquivo
indicado em `application.yml`.

## Estrutura
| Classe | Papel |
|---|---|
| `Periodo` | regra de janela (semana corrente × fechamento) |
| `MetaClient` | insights por campanha (Graph API), token lido do cofre |
| `GoogleAdsClient` | investimento por campanha (GAQL) |
| `Relatorio` | agrupamento das campanhas e texto do report |
| `ReportBotApplication` | orquestra, envia ao Discord, registra e commita |

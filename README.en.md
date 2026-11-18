# report-bot

[Português](README.md) | **English**

Weekly paid media report bot. It runs once and exits. It reads Meta Ads and Google Ads through their APIs, builds the
campaign tables and the message text, all as plain text, and posts everything to a private Discord channel for review
before it goes to the client.

- **Period:** from the 1st of the month through yesterday. On the first Thursday of the month, the previous month, closed.
- **Comparisons:** the same window in the previous month and in the same month of the previous year.
- **History:** every run becomes a line in `HISTORICO.md` and an automatic commit. No client data goes into the repository.

## Stack
Java 21 · Spring Boot 3 (no web server) · `java.net.http` · Discord webhook.

## Run
```bash
cp config/local.example.yml config/local.yml   # fill it in
./report.command              # Mac (or report.bat on Windows)
./report.command 2026-11-12   # simulates another date
```
Requirements: JDK 21, Maven, `pwsh` with the Meta token in the vault, plus Google Ads credentials in the file
referenced in `application.yml`.

## Structure
| Class | Role |
|---|---|
| `Periodo` | window rule (current week × month close) |
| `MetaClient` | per-campaign insights (Graph API), token read from the vault |
| `GoogleAdsClient` | per-campaign spend (GAQL) |
| `Relatorio` | campaign grouping and report text |
| `ReportBotApplication` | orchestrates, sends to Discord, logs and commits |

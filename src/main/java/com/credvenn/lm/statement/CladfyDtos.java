package com.credvenn.lm.statement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CladfyDtos {

    private CladfyDtos() {
    }

    public record CreateClientRequest(
            String first_name,
            String last_name,
            String phone_number,
            String national_id) {
    }

    public record ClientResponse(
            Long id,
            String first_name,
            String last_name,
            String full_name,
            String phone_number) {
    }

    public record DocumentUploadResponse(
            Long id,
            String url,
            Long client_id,
            String provider,
            String status,
            Instant created_at) {
    }

    public record WebhookRequest(
            Long document_id,
            Long client_id,
            Long business_id) {
    }

    public record AnalysisResultsResponse(
            Document document,
            Client client,
            Summary summary,
            List<Transaction> transactions,
            Object cashflow,
            Object spending,
            Object loans) {
    }

    public record Document(
            Long id,
            String status,
            String provider,
            String currency,
            Instant created_at,
            Instant last_analyzed_on) {
    }

    public record Client(
            Long id,
            String full_name,
            String national_id) {
    }

    public record Summary(
            BigDecimal total_in,
            BigDecimal total_out,
            Integer transaction_count,
            BigDecimal zero_balance_rate_percentage) {
    }

    public record Transaction(
            Long id,
            String type,
            BigDecimal amount,
            String date,
            String narration,
            BigDecimal balance,
            String currency) {
    }

    public record CreditScoreResponse(
            Integer score,
            RiskTier risk_tier,
            Object features,
            Instant scored_at) {
    }

    public record RiskTier(
            String tier,
            String color,
            Integer min_score,
            String risk) {
    }
}

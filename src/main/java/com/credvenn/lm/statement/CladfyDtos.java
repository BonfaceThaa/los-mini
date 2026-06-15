package com.credvenn.lm.statement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClientResponse(
            Long id,
            String first_name,
            String last_name,
            String full_name,
            String phone_number) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateClientConflictResponse(
            String detail,
            String message,
            ExistingClient existing_client) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExistingClient(
            Long id,
            String first_name,
            String last_name,
            String full_name,
            String phone_number,
            String email,
            String national_id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentUploadResponse(
            Long id,
            String url,
            Long client_id,
            String provider,
            Integer status,
            String created_at) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookRequest(
            Long document_id,
            Long client_id,
            Long business_id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnalysisResultsResponse(
            Document document,
            Client client,
            Summary summary,
            List<Transaction> transactions,
            Object cashflow,
            Object spending,
            Object loans) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            Long id,
            String status,
            String provider,
            String currency,
            String created_at,
            String last_analyzed_on) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Client(
            Long id,
            String full_name,
            String national_id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            BigDecimal total_in,
            BigDecimal total_out,
            Integer transaction_count,
            BigDecimal zero_balance_rate_percentage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transaction(
            Long id,
            String type,
            BigDecimal amount,
            String date,
            String narration,
            BigDecimal balance,
            String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreditScoreResponse(
            Integer score,
            RiskTier risk_tier,
            Object features,
            String scored_at) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskTier(
            String tier,
            String color,
            Integer min_score,
            String risk) {
    }
}

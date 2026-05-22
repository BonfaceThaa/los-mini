package com.credvenn.lm.statement;

import com.credvenn.lm.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "cladfy_statement_transactions")
public class CladfyStatementTransaction extends AuditableEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "statement_analysis_id", nullable = false, length = 36)
    private String statementAnalysisId;

    @Column(name = "external_transaction_id", length = 100)
    private String externalTransactionId;

    @Column(name = "transaction_type", length = 50)
    private String transactionType;

    @Column(name = "transaction_amount", precision = 19, scale = 2)
    private BigDecimal transactionAmount;

    @Column(name = "transaction_date", length = 100)
    private String transactionDate;

    @Column(name = "narration", length = 1000)
    private String narration;

    @Column(name = "balance", precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "currency", length = 20)
    private String currency;

    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() { return id; }
    public String getStatementAnalysisId() { return statementAnalysisId; }
    public void setStatementAnalysisId(String statementAnalysisId) { this.statementAnalysisId = statementAnalysisId; }
    public String getExternalTransactionId() { return externalTransactionId; }
    public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
    public String getTransactionDate() { return transactionDate; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }
    public String getNarration() { return narration; }
    public void setNarration(String narration) { this.narration = narration; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}

package com.crm.dto.deal;

import com.crm.model.enums.DealStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DealResponse {

    private Long id;
    private String title;
    private BigDecimal amount;
    private String currency;
    private DealStatus status;
    private LocalDateTime closedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public DealStatus getStatus() {
        return status;
    }

    public void setStatus(DealStatus status) {
        this.status = status;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }
}

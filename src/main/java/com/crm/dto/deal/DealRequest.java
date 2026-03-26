package com.crm.dto.deal;

import com.crm.model.enums.DealStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DealRequest {

    @NotBlank
    private String title;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    private BigDecimal amount;

    @NotBlank
    // Формат ISO 4217, фактическая валидность кода проверяется при создании Money.
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    @NotNull
    private DealStatus status;

    private LocalDateTime closedAt;

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

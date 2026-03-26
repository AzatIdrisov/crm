package com.crm.mapper;

import com.crm.dto.deal.DealRequest;
import com.crm.dto.deal.DealResponse;
import com.crm.model.Deal;
import com.crm.model.value.Money;

public final class DealMapper {

    private DealMapper() {}

    public static Deal toDomain(DealRequest request) {
        Money amount = Money.of(request.getAmount(), request.getCurrency());
        return Deal.builder()
                .title(request.getTitle())
                .amount(amount)
                .status(request.getStatus())
                .closedAt(request.getClosedAt())
                .build();
    }

    public static DealResponse toResponse(Deal deal) {
        DealResponse response = new DealResponse();
        response.setId(deal.getId());
        response.setTitle(deal.getTitle());
        if (deal.getAmount() != null) {
            response.setAmount(deal.getAmount().amount());
            response.setCurrency(deal.getAmount().currency().getCurrencyCode());
        }
        response.setStatus(deal.getStatus());
        response.setClosedAt(deal.getClosedAt());
        return response;
    }
}

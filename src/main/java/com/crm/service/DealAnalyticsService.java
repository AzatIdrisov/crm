package com.crm.service;

import com.crm.model.Deal;
import com.crm.model.User;
import com.crm.model.enums.DealStatus;
import com.crm.model.value.Money;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DealAnalyticsService {

    public List<Deal> filterByStatus(List<Deal> deals, DealStatus status) {
        return deals.stream().filter(deal -> deal.getStatus() == status).toList();
    }

    public List<String> getTitles(List<Deal> deals) {
        return deals.stream().map(Deal::getTitle).toList();
    }

    public Map<DealStatus, List<Deal>> groupByStatus(List<Deal> deals) {
        return deals.stream().collect(Collectors.groupingBy(Deal::getStatus));
    }

    public Map<DealStatus, Long> countByStatus(List<Deal> deals) {
        return deals.stream().collect(Collectors.groupingBy(Deal::getStatus, Collectors.counting()));
    }

    public Map<User, List<Deal>> groupByManager(List<Deal> deals) {
        return deals.stream()
                .filter(deal -> deal.getAssignedTo() != null)
                .collect(Collectors.groupingBy(Deal::getAssignedTo));
    }

    public Optional<Money> totalAmount(List<Deal> deals) {
        return deals.stream()
                .map(Deal::getAmount)
                .filter(Objects::nonNull)
                .reduce(Money::add);
    }

    public Map<DealStatus, Double> totalAmountByStatus(List<Deal> deals) {
        return deals.stream()
                .filter(deal -> deal.getAmount() != null)
                .collect(Collectors.groupingBy(Deal::getStatus,
                        Collectors.summingDouble(deal -> deal.getAmount().amount().doubleValue())));
    }

    public List<Deal> getTopDeals(List<Deal> deals, int n) {
        return deals.stream()
                .filter(d -> d.getAmount() != null)
                .sorted(Comparator.naturalOrder())
                .limit(n)
                .toList();
    }

    public Optional<Deal> findLargest(List<Deal> deals) {
        return deals.stream()
                .filter(d -> d.getAmount() != null)
                .max(Comparator.comparing(d -> d.getAmount().amount()));
    }

    public boolean hasWonDeals(List<Deal> deals) {
        return deals.stream()
                .anyMatch(deal -> deal.getStatus() == DealStatus.WON);
    }

    public boolean allDealsAssigned(List<Deal> deals) {
        return deals.stream()
                .allMatch(deal -> deal.getAssignedTo() != null);
    }

    public boolean noDealsWithoutAmount(List<Deal> deals) {
        return deals.stream()
                .noneMatch(deal -> deal.getAmount() == null);
    }

    public String summaryReport(List<Deal> deals) {
        return deals.stream()
                .map(d -> "%s [%s] — %s".formatted(d.getTitle(), d.getStatus(), d.getAmount()))
                .collect(Collectors.joining("\n"));
    }

    public List<User> getDistinctManagers(List<List<Deal>> dealGroups) {
        return dealGroups.stream()
                .flatMap(deals -> deals.stream().filter(deal -> deal.getAssignedTo() != null)
                        .map(Deal::getAssignedTo))
                .distinct()
                .toList();
    }

    public Map<Long, Deal> indexById(List<Deal> deals) {
        return deals.stream()
                .filter(deal -> deal.getId() != null)
                .collect(Collectors.toMap(Deal::getId, deal -> deal));
    }

    public Map<Boolean, List<Deal>> partitionByWon(List<Deal> deals) {
        return deals.stream()
                .collect(Collectors.partitioningBy(deal -> deal.getStatus() == DealStatus.WON));
    }
}

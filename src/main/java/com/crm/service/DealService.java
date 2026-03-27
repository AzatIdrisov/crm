package com.crm.service;

import com.crm.event.DealStatusChangedEvent;
import com.crm.exception.ResourceNotFoundException;
import com.crm.model.Deal;
import com.crm.model.enums.DealStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DealService {

    // In-memory хранилище — заменится на репозиторий в Фазе 5
    private final Map<Long, Deal> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    private final ApplicationEventPublisher publisher;

    public DealService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public Optional<Deal> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Deal> findAll() {
        return new ArrayList<>(store.values());
    }

    public Deal save(Deal deal) {
        if (deal.getId() == null) {
            deal.setId(idSequence.getAndIncrement());
        }
        store.put(deal.getId(), deal);
        return deal;
    }

    public boolean deleteById(Long id) {
        return store.remove(id) != null;
    }

    public Deal changeStatus(Long id, DealStatus newStatus) {
        Deal deal = findById(id).orElseThrow(() -> new ResourceNotFoundException("Deal", id));
        DealStatus oldStatus = deal.getStatus();
        deal.setStatus(newStatus);
        save(deal);
        publisher.publishEvent(new DealStatusChangedEvent(this, id, oldStatus, newStatus));
        return deal;
    }
}

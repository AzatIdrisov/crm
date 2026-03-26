package com.crm.service;

import com.crm.model.Deal;
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
}

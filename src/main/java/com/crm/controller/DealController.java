package com.crm.controller;

import com.crm.model.Deal;
import com.crm.service.DealService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deals")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @GetMapping
    public List<Deal> findAll() {
        return dealService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Deal> findById(@PathVariable Long id) {
        return dealService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Deal> create(@RequestBody Deal deal) {
        Deal saved = dealService.save(deal);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Deal> update(@PathVariable Long id, @RequestBody Deal deal) {
        if (dealService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        deal.setId(id);
        return ResponseEntity.ok(dealService.save(deal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!dealService.deleteById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

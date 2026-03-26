package com.crm.controller;

import com.crm.dto.deal.DealRequest;
import com.crm.dto.deal.DealResponse;
import com.crm.mapper.DealMapper;
import com.crm.model.Deal;
import com.crm.service.DealService;
import jakarta.validation.Valid;
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
    public List<DealResponse> findAll() {
        return dealService.findAll()
                .stream()
                .map(DealMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DealResponse> findById(@PathVariable Long id) {
        return dealService.findById(id)
                .map(DealMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<DealResponse> create(@Valid @RequestBody DealRequest request) {
        Deal saved = dealService.save(DealMapper.toDomain(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(DealMapper.toResponse(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DealResponse> update(@PathVariable Long id, @Valid @RequestBody DealRequest request) {
        if (dealService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Deal updated = DealMapper.toDomain(request);
        updated.setId(id);
        return ResponseEntity.ok(DealMapper.toResponse(dealService.save(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!dealService.deleteById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

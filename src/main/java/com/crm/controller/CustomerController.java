package com.crm.controller;

import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.mapper.CustomerMapper;
import com.crm.model.Customer;
import com.crm.service.CustomerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
// @Validated включает валидацию @PathVariable/@RequestParam с Bean Validation аннотациями.
@Validated
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public List<CustomerResponse> findAll() {
        return customerService.findAll()
                .stream()
                .map(CustomerMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> findById(@PathVariable @NotNull Long id) {
        return customerService.findById(id)
                .map(CustomerMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    // @Valid -> MethodArgumentNotValidException при нарушении ограничений DTO.
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        Customer saved = customerService.save(CustomerMapper.toDomain(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomerMapper.toResponse(saved));
    }

    @PutMapping("/{id}")
    // Для @PathVariable/@RequestParam нужна @Validated на классе, здесь валидируем тело.
    public ResponseEntity<CustomerResponse> update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        if (customerService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Customer updated = CustomerMapper.toDomain(request);
        updated.setId(id);
        return ResponseEntity.ok(CustomerMapper.toResponse(customerService.save(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!customerService.deleteById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}

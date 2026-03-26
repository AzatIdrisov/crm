package com.crm.mapper;

import com.crm.dto.customer.CustomerRequest;
import com.crm.dto.customer.CustomerResponse;
import com.crm.model.Customer;
import com.crm.model.value.Email;
import com.crm.model.value.PhoneNumber;

public final class CustomerMapper {

    private CustomerMapper() {}

    public static Customer toDomain(CustomerRequest request) {
        Email email = request.getEmail() != null ? new Email(request.getEmail()) : null;
        PhoneNumber phone = request.getPhone() != null ? new PhoneNumber(request.getPhone()) : null;
        return Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(email)
                .phone(phone)
                .company(request.getCompany())
                .build();
    }

    public static CustomerResponse toResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setId(customer.getId());
        response.setFirstName(customer.getFirstName());
        response.setLastName(customer.getLastName());
        response.setEmail(customer.getEmail() != null ? customer.getEmail().toString() : null);
        response.setPhone(customer.getPhone() != null ? customer.getPhone().toString() : null);
        response.setCompany(customer.getCompany());
        return response;
    }
}

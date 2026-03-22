package com.crm.model;

import com.crm.model.value.Email;
import com.crm.model.value.PhoneNumber;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Customer extends BaseEntity<Long> {

    private String firstName;
    private String lastName;
    private Email email;
    private PhoneNumber phone;
    private String company;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}

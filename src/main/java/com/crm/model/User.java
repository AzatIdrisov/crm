package com.crm.model;

import com.crm.model.enums.UserRole;
import com.crm.model.value.Email;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User extends BaseEntity<Long> {

    private String firstName;
    private String lastName;
    private Email email;
    private String password;
    private UserRole role;

    public String getFullName() {
        return firstName + " " + lastName;
    }
}

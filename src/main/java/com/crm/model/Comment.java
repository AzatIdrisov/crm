package com.crm.model;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class Comment extends BaseEntity<Long> {

    private String content;
    private User author;
    private Deal deal;
}

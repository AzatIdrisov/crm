package com.crm.model;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment extends BaseEntity<Long> {

    private String content;
    private User author;
    private Deal deal;
}

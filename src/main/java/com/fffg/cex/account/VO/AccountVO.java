package com.fffg.cex.account.VO;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccountVO {
    private Long accountId;
    private String userName;
    private Integer status;
    private LocalDateTime createAt;
}

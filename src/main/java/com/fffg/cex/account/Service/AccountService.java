package com.fffg.cex.account.Service;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.VO.AccountVO;

public interface AccountService {
    AccountVO createAccount(CreateAccountRequestDTO createAccountRequestDTO);
}

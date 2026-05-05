package com.fffg.cex.account.Service.Impl;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.Mapper.AccountMapper;
import com.fffg.cex.account.Service.AccountService;
import com.fffg.cex.account.VO.AccountVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountMapper accountMapper;


    @Override
    public AccountVO createAccount(CreateAccountRequestDTO createAccountRequestDTO) {
         accountMapper.createAccount(createAccountRequestDTO);
         return accountMapper.getAccountById(createAccountRequestDTO.getId());
    }
}

package com.fffg.cex.account.Controller;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.Service.AccountService;
import com.fffg.cex.account.VO.AccountVO;
import com.fffg.cex.common.result.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController("/accounts")
public class AccountController {

    @Autowired
    private AccountService accountService;
    @PostMapping("/")
    public ApiResponse<AccountVO> createAccount(@RequestBody CreateAccountRequestDTO createAccountRequestDTO){
        return ApiResponse.success(accountService.createAccount(createAccountRequestDTO)) ;
    }
}

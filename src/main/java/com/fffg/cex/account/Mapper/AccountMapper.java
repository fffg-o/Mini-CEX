package com.fffg.cex.account.Mapper;

import com.fffg.cex.account.DTO.CreateAccountRequestDTO;
import com.fffg.cex.account.VO.AccountVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountMapper {


    @Insert("insert into account (username,status,created_at,updated_at) values (#{username},1,now(),now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void createAccount(CreateAccountRequestDTO createAccountRequestDTO);

    @Select("select id as accountId, username as userName, status, created_at as createdAt, updated_at from account where id = #{id}")
    AccountVO getAccountById(Long id);
}

package com.fffg.cex.account.Mapper;

import com.fffg.cex.account.VO.AccountBalanceVO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AccountBalanceMapper {

    @Select("select asset_symbol, available_balance, frozen_balance from account_balance where account_id = #{accountId}")
    List<AccountBalanceVO> selectByAccountId(Long accountId);

    @Select("select asset_symbol, available_balance, frozen_balance from account_balance where account_id = #{accountId} and asset_symbol = #{assetSymbol}")
    AccountBalanceVO selectByAccountIdAndAsset(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol);

    @Insert("insert into account_balance(account_id, asset_symbol, available_balance, frozen_balance, created_at, updated_at) " +
            "values(#{accountId}, #{assetSymbol}, 0, 0, now(), now())")
    void insertBalance(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol);

    @Update("update account_balance set available_balance = available_balance + #{amount}, updated_at = now() " +
            "where account_id = #{accountId} and asset_symbol = #{assetSymbol}")
    int addAvailableBalance(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol, @Param("amount") BigDecimal amount);
}

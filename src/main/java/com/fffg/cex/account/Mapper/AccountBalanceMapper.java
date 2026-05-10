package com.fffg.cex.account.Mapper;

import com.fffg.cex.account.VO.AccountBalanceVO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AccountBalanceMapper {

    @Select("select asset_symbol, available_balance, frozen_balance from account_balance " +
            "where account_id = #{accountId}")
    List<AccountBalanceVO> selectByAccountId(Long accountId);

    @Select("select asset_symbol, available_balance, frozen_balance from account_balance " +
            "where account_id = #{accountId} and asset_symbol = #{assetSymbol}")
    AccountBalanceVO selectByAccountIdAndAsset(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol);

    @Insert("insert into account_balance(account_id, asset_symbol, available_balance, frozen_balance, created_at, updated_at) " +
            "values(#{accountId}, #{assetSymbol}, 0, 0, now(), now())")
    void insertBalance(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol);

    /**
     * 增加可用余额（无安全检查 - 用于充值等确保有前置校验的场景）
     */
    @Update("update account_balance set available_balance = available_balance + #{amount}, updated_at = now() " +
            "where account_id = #{accountId} and asset_symbol = #{assetSymbol}")
    int addAvailableBalance(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol, @Param("amount") BigDecimal amount);

    /**
     * 条件更新：冻结时减少可用余额并增加冻结余额。
     * 通过 WHERE available_balance >= #{amount} 防止超扣。
     *
     * @return 影响行数，1表示成功，0表示余额不足
     */
    @Update("update account_balance " +
            "set available_balance = available_balance - #{amount}, " +
            "    frozen_balance = frozen_balance + #{amount}, " +
            "    updated_at = now() " +
            "where account_id = #{accountId} " +
            "  and asset_symbol = #{assetSymbol} " +
            "  and available_balance >= #{amount}")
    int freezeBalance(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol, @Param("amount") BigDecimal amount);

    /**
     * 条件更新：解冻时减少冻结余额并增加可用余额。
     */
    @Update("update account_balance " +
            "set available_balance = available_balance + #{amount}, " +
            "    frozen_balance = frozen_balance - #{amount}, " +
            "    updated_at = now() " +
            "where account_id = #{accountId} " +
            "  and asset_symbol = #{assetSymbol} " +
            "  and frozen_balance >= #{amount}")
    int unfreezeBalance(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol, @Param("amount") BigDecimal amount);

    /**
     * 条件更新：增加可用余额（带检查）。用于充值等场景，确保数据一致性。
     */
    @Update("update account_balance set available_balance = available_balance + #{amount}, updated_at = now() " +
            "where account_id = #{accountId} and asset_symbol = #{assetSymbol}")
    int addAvailableBalanceWithCheck(@Param("accountId") Long accountId, @Param("assetSymbol") String assetSymbol, @Param("amount") BigDecimal amount);
}

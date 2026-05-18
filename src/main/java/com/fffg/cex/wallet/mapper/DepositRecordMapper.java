package com.fffg.cex.wallet.mapper;

import com.fffg.cex.wallet.vo.DepositRecordVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 充值记录 Mapper
 */
@Mapper
public interface DepositRecordMapper {

    @Insert("INSERT INTO deposit_record(account_id, asset_symbol, chain, tx_hash, amount, " +
            "confirmations, required_confirmations, status, created_at) " +
            "VALUES(#{accountId}, #{assetSymbol}, #{chain}, #{txHash}, #{amount}, " +
            "#{confirmations}, #{requiredConfirmations}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "depositId")
    void insert(DepositRecordVO record);

    @Select("SELECT id AS depositId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, tx_hash AS txHash, amount, confirmations, " +
            "required_confirmations AS requiredConfirmations, status, " +
            "created_at AS createdAt, confirmed_at AS confirmedAt " +
            "FROM deposit_record " +
            "WHERE account_id = #{accountId} " +
            "ORDER BY id DESC")
    List<DepositRecordVO> selectByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT id AS depositId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, tx_hash AS txHash, amount, confirmations, " +
            "required_confirmations AS requiredConfirmations, status, " +
            "created_at AS createdAt, confirmed_at AS confirmedAt " +
            "FROM deposit_record " +
            "WHERE account_id = #{accountId} AND asset_symbol = #{assetSymbol} " +
            "ORDER BY id DESC")
    List<DepositRecordVO> selectByAccountAndAsset(@Param("accountId") Long accountId,
                                                   @Param("assetSymbol") String assetSymbol);

    @Select("SELECT id AS depositId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, tx_hash AS txHash, amount, confirmations, " +
            "required_confirmations AS requiredConfirmations, status, " +
            "created_at AS createdAt, confirmed_at AS confirmedAt " +
            "FROM deposit_record " +
            "WHERE account_id = #{accountId} AND status = #{status} " +
            "ORDER BY id DESC")
    List<DepositRecordVO> selectByAccountAndStatus(@Param("accountId") Long accountId,
                                                    @Param("status") String status);

    @Select("SELECT id AS depositId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, tx_hash AS txHash, amount, confirmations, " +
            "required_confirmations AS requiredConfirmations, status, " +
            "created_at AS createdAt, confirmed_at AS confirmedAt " +
            "FROM deposit_record " +
            "WHERE account_id = #{accountId} AND asset_symbol = #{assetSymbol} AND status = #{status} " +
            "ORDER BY id DESC")
    List<DepositRecordVO> selectByAllParams(@Param("accountId") Long accountId,
                                             @Param("assetSymbol") String assetSymbol,
                                             @Param("status") String status);

    @Select("SELECT id AS depositId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, tx_hash AS txHash, amount, confirmations, " +
            "required_confirmations AS requiredConfirmations, status, " +
            "created_at AS createdAt, confirmed_at AS confirmedAt " +
            "FROM deposit_record " +
            "WHERE status = 'PENDING' " +
            "ORDER BY id ASC")
    List<DepositRecordVO> selectPendingRecords();

    @Update("UPDATE deposit_record SET confirmations = #{confirmations}, " +
            "status = #{status}, confirmed_at = #{confirmedAt} " +
            "WHERE id = #{depositId}")
    void updateStatus(@Param("depositId") Long depositId,
                     @Param("confirmations") Integer confirmations,
                     @Param("status") String status,
                     @Param("confirmedAt") java.time.LocalDateTime confirmedAt);
}

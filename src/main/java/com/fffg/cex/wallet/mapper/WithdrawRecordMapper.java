package com.fffg.cex.wallet.mapper;

import com.fffg.cex.wallet.vo.WithdrawRecordVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 提现记录 Mapper
 */
@Mapper
public interface WithdrawRecordMapper {

    @Insert("INSERT INTO withdraw_record(account_id, asset_symbol, chain, to_address, amount, fee, " +
            "status, business_id, created_at, updated_at) " +
            "VALUES(#{accountId}, #{assetSymbol}, #{chain}, #{toAddress}, #{amount}, #{fee}, " +
            "#{status}, #{businessId}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "withdrawId")
    void insert(WithdrawRecordVO record);

    @Select("SELECT id AS withdrawId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, to_address AS toAddress, amount, fee, status, " +
            "tx_hash AS txHash, created_at AS createdAt " +
            "FROM withdraw_record " +
            "WHERE id = #{withdrawId}")
    WithdrawRecordVO selectById(@Param("withdrawId") Long withdrawId);

    @Select("SELECT id AS withdrawId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, to_address AS toAddress, amount, fee, status, " +
            "tx_hash AS txHash, created_at AS createdAt " +
            "FROM withdraw_record " +
            "WHERE account_id = #{accountId} " +
            "ORDER BY id DESC")
    List<WithdrawRecordVO> selectByAccountId(@Param("accountId") Long accountId);

    @Select("SELECT id AS withdrawId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, to_address AS toAddress, amount, fee, status, " +
            "tx_hash AS txHash, created_at AS createdAt " +
            "FROM withdraw_record " +
            "WHERE account_id = #{accountId} AND status = #{status} " +
            "ORDER BY id DESC")
    List<WithdrawRecordVO> selectByAccountAndStatus(@Param("accountId") Long accountId,
                                                     @Param("status") String status);

    @Select("SELECT id AS withdrawId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, to_address AS toAddress, amount, fee, status, " +
            "tx_hash AS txHash, created_at AS createdAt " +
            "FROM withdraw_record " +
            "WHERE status = #{status} " +
            "ORDER BY id ASC")
    List<WithdrawRecordVO> selectByStatus(@Param("status") String status);

    @Select("SELECT id AS withdrawId, account_id AS accountId, asset_symbol AS assetSymbol, " +
            "chain, to_address AS toAddress, amount, fee, status, " +
            "tx_hash AS txHash, created_at AS createdAt " +
            "FROM withdraw_record " +
            "WHERE business_id = #{businessId}")
    WithdrawRecordVO selectByBusinessId(@Param("businessId") String businessId);

    @Update("UPDATE withdraw_record SET status = #{newStatus}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = #{oldStatus}")
    int updateStatus(@Param("id") Long id,
                    @Param("oldStatus") String oldStatus,
                    @Param("newStatus") String newStatus);

    @Update("UPDATE withdraw_record SET status = #{newStatus}, tx_hash = #{txHash}, updated_at = NOW() " +
            "WHERE id = #{id} AND status = #{oldStatus}")
    int updateStatusWithTxHash(@Param("id") Long id,
                              @Param("oldStatus") String oldStatus,
                              @Param("newStatus") String newStatus,
                              @Param("txHash") String txHash);
}

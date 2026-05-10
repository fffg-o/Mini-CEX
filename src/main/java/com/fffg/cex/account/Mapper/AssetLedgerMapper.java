package com.fffg.cex.account.Mapper;

import com.fffg.cex.account.VO.AssetLedgerVO;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AssetLedgerMapper {

    @Insert("insert into asset_ledger(account_id, asset_symbol, business_type, business_id, " +
            "change_available, change_frozen, before_available, after_available, before_frozen, after_frozen, created_at) " +
            "values(#{accountId}, #{assetSymbol}, #{businessType}, #{businessId}, " +
            "#{changeAvailable}, #{changeFrozen}, #{beforeAvailable}, #{afterAvailable}, #{beforeFrozen}, #{afterFrozen}, now())")
    void insert(AssetLedgerRecord record);

    @Select("<script>" +
            "select id, asset_symbol, business_type, business_id, " +
            "change_available, change_frozen, before_available, after_available, before_frozen, after_frozen, created_at " +
            "from asset_ledger " +
            "where account_id = #{accountId} " +
            "<if test='assetSymbol != null and assetSymbol != \"\"'> and asset_symbol = #{assetSymbol} </if>" +
            "<if test='businessType != null and businessType != \"\"'> and business_type = #{businessType} </if>" +
            "order by id desc " +
            "limit #{offset}, #{limit}" +
            "</script>")
    List<AssetLedgerVO> selectPage(@Param("accountId") Long accountId,
                                    @Param("assetSymbol") String assetSymbol,
                                    @Param("businessType") String businessType,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Select("<script>" +
            "select count(*) from asset_ledger " +
            "where account_id = #{accountId} " +
            "<if test='assetSymbol != null and assetSymbol != \"\"'> and asset_symbol = #{assetSymbol} </if>" +
            "<if test='businessType != null and businessType != \"\"'> and business_type = #{businessType} </if>" +
            "</script>")
    long countByCondition(@Param("accountId") Long accountId,
                          @Param("assetSymbol") String assetSymbol,
                          @Param("businessType") String businessType);
}

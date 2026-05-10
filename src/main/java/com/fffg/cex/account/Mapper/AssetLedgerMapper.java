package com.fffg.cex.account.Mapper;

import com.fffg.cex.account.VO.AssetLedgerVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AssetLedgerMapper {

    @Insert("insert into asset_ledger(account_id, asset_symbol, business_type, business_id, " +
            "change_available, change_frozen, before_available, after_available, before_frozen, after_frozen, created_at) " +
            "values(#{accountId}, #{assetSymbol}, #{businessType}, #{businessId}, " +
            "#{changeAvailable}, #{changeFrozen}, #{beforeAvailable}, #{afterAvailable}, #{beforeFrozen}, #{afterFrozen}, now())")
    void insert(AssetLedgerRecord record);

    /**
     * 根据 businessId 查询流水（用于幂等性校验）
     */
    @Select("select id, asset_symbol as assetSymbol, business_type as businessType, business_id as businessId, " +
            "change_available as changeAvailable, change_frozen as changeFrozen, " +
            "before_available as beforeAvailable, after_available as afterAvailable, " +
            "before_frozen as beforeFrozen, after_frozen as afterFrozen, created_at as createdAt " +
            "from asset_ledger where business_id = #{businessId}")
    AssetLedgerVO selectByBusinessId(String businessId);

    @Select("<script>" +
            "select id, asset_symbol as assetSymbol, business_type as businessType, business_id as businessId, " +
            "change_available as changeAvailable, change_frozen as changeFrozen, " +
            "before_available as beforeAvailable, after_available as afterAvailable, " +
            "before_frozen as beforeFrozen, after_frozen as afterFrozen, created_at as createdAt " +
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

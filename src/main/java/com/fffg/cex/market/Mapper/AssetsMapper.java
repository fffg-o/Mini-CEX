package com.fffg.cex.market.Mapper;

import com.fffg.cex.market.VO.AssetVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetsMapper {

    /**
     * 查询所有启用的币种列表（status = 1）
     */
    @Select("select symbol, name, scale_num, status from asset where status = 1")
    List<AssetVO> selectAssetsList();

    /**
     * 根据 symbol 查询币种（包含已禁用的）
     */
    @Select("select symbol, name, scale_num, status from asset where symbol = #{symbol}")
    AssetVO selectBySymbol(@Param("symbol") String symbol);
}

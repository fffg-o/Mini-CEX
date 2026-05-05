package com.fffg.cex.market.Mapper;

import com.fffg.cex.market.VO.AssetVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AssetsMapper {

    @Select("select symbol,name,scale_num,status from asset ")
    List<AssetVO> selectAssetsList();
}

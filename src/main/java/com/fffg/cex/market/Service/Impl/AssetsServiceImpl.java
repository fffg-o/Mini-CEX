package com.fffg.cex.market.Service.Impl;

import com.fffg.cex.market.Mapper.AssetsMapper;
import com.fffg.cex.market.Service.AssetsService;
import com.fffg.cex.market.VO.AssetVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssetsServiceImpl implements AssetsService {
    @Autowired
    private AssetsMapper assetsMapper;
    @Override
    public List<AssetVO> getAssetsList() {
        return assetsMapper.selectAssetsList();
    }
}

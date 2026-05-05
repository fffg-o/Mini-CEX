package com.fffg.cex.market.VO;

import lombok.Data;

@Data
public class AssetVO {
    private String symbol;
    private String name;
    private Integer scaleNum;
    private Integer status;
}


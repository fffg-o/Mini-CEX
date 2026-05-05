package com.fffg.cex.market.VO;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SymbolPairVO {
    private String symbol;
    private String baseAsset;
    private String quoteAsset;
    private Integer priceScale;
    private Integer quantityScale;
    private BigDecimal minOrderAmount;
    private Integer status;
}

package com.fffg.cex.account.VO;

import lombok.Data;

import java.util.List;

@Data
public class PageVO<T> {
    private List<T> records;
    private int pageNum;
    private int pageSize;
    private long total;

    public PageVO(List<T> records, int pageNum, int pageSize, long total) {
        this.records = records;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.total = total;
    }
}

package com.aihub.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private Long total;
    private Long page;
    private Long size;
    private List<T> records;

    public static <T> PageResult<T> of(Long total, Long page, Long size, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.setTotal(total);
        r.setPage(page);
        r.setSize(size);
        r.setRecords(records);
        return r;
    }
}

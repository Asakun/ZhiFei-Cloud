package com.dji.sample.component.oss.model;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StorageStat {

    private BigDecimal freeBytes;

    private BigDecimal totalBytes;

    private BigDecimal usedBytes;
}

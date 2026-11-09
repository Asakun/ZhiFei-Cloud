package com.dji.sample.component.oss.model;

import lombok.Data;

import java.time.Instant;

@Data
public class BucketStat {

    private String bucketName;

    private int objectCount;

    private long totalSize;

    private Instant latestModified;

    private int imageCount;

    private long imageSize;

    private int videoCount;

    private long videoSize;

    private int otherCount;

    private long otherSize;
}

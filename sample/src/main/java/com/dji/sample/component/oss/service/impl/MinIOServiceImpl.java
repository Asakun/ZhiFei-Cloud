package com.dji.sample.component.oss.service.impl;

import com.dji.sample.component.oss.model.BucketStat;
import com.dji.sample.component.oss.model.OssConfiguration;
import com.dji.sample.component.oss.model.StorageStat;
import com.dji.sample.component.oss.service.IOssService;
import com.dji.sdk.cloudapi.storage.CredentialsToken;
import com.dji.sdk.cloudapi.storage.OssTypeEnum;
import io.minio.*;
import io.minio.admin.MinioAdminClient;
import io.minio.admin.messages.info.Disk;
import io.minio.admin.messages.info.Message;
import io.minio.admin.messages.info.ServerProperties;
import io.minio.credentials.AssumeRoleProvider;
import io.minio.credentials.Credentials;
import io.minio.errors.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author sean
 * @version 0.3
 * @date 2021/12/23
 */
@Service
@Slf4j
public class MinIOServiceImpl implements IOssService {

    private MinioClient client;
    
    @Override
    public OssTypeEnum getOssType() {
        return OssTypeEnum.MINIO;
    }

    @Override
    public CredentialsToken getCredentials() {
        try {
            AssumeRoleProvider provider = new AssumeRoleProvider(OssConfiguration.endpoint, OssConfiguration.accessKey,
                    OssConfiguration.secretKey, Math.toIntExact(OssConfiguration.expire),
                    null, OssConfiguration.region, null, null, null, null);
            Credentials credential = provider.fetch();
            return new CredentialsToken(credential.accessKey(), credential.secretKey(), credential.sessionToken(), OssConfiguration.expire);
        } catch (NoSuchAlgorithmException e) {
            log.debug("Failed to obtain sts.");
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public URL getObjectUrl(String bucket, String objectKey) {
        try {
            return new URL(
                    client.getPresignedObjectUrl(
                                    GetPresignedObjectUrlArgs.builder()
                                            .method(Method.GET)
                                            .bucket(bucket)
                                            .object(objectKey)
                                            .expiry(Math.toIntExact(OssConfiguration.expire))
                                            .build()));
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                InvalidKeyException | InvalidResponseException | IOException |
                NoSuchAlgorithmException | XmlParserException | ServerException e) {
            throw new RuntimeException("The file does not exist on the OssConfiguration.");
        }
    }

    @Override
    public Boolean deleteObject(String bucket, String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (MinioException | NoSuchAlgorithmException | IOException | InvalidKeyException e) {
            log.error("Failed to delete file.");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        try {
            GetObjectResponse object = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return new ByteArrayInputStream(object.readAllBytes());
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException | XmlParserException e) {
            e.printStackTrace();
        }
        return InputStream.nullInputStream();
    }

    @Override
    public BucketStat getBucketStat(String bucket) {
        BucketStat stat = new BucketStat();
        stat.setBucketName(bucket);
        long totalSize = 0L;
        int count = 0;
        ZonedDateTime latestModified = null;
        try {
            Iterable<Result<Item>> results = client.listObjects(
                    ListObjectsArgs.builder().bucket(bucket).recursive(true).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                long size = item.size();
                String objectKey = item.objectName();
                totalSize += size;
                count++;
                ZonedDateTime itemModified = item.lastModified();
                if (latestModified == null || itemModified.isAfter(latestModified)) {
                    latestModified = itemModified;
                }
                categorize(stat, objectKey, size);
            }
        } catch (Exception e) {
            log.error("Failed to get bucket stat.");
            e.printStackTrace();
            throw new RuntimeException("Failed to get bucket stat.", e);
        }
        stat.setObjectCount(count);
        stat.setTotalSize(totalSize);
        if (latestModified != null) {
            stat.setLatestModified(latestModified.toInstant());
        }
        return stat;
    }

    @Override
    public StorageStat getStorageStat() {
        StorageStat stat = new StorageStat();
        try {
            MinioAdminClient adminClient = MinioAdminClient.builder()
                    .endpoint(OssConfiguration.endpoint)
                    .credentials(OssConfiguration.accessKey, OssConfiguration.secretKey)
                    .build();

            Message info = adminClient.getServerInfo();
            parseStorageInfo(info, stat);
        } catch (Exception e) {
            log.error("Failed to get storage stat.");
            e.printStackTrace();
            throw new RuntimeException("Failed to get storage stat.", e);
        }
        return stat;
    }

    @Override
    public void putObject(String bucket, String objectKey, InputStream input) {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            throw new RuntimeException("The filename already exists.");
        } catch (MinioException | InvalidKeyException | IOException | NoSuchAlgorithmException e) {
            log.info("The file does not exist, start uploading.");
            overwritePutObject(bucket, objectKey, input);
        }
    }

    @Override
    public void overwritePutObject(String bucket, String objectKey, InputStream input) {
        try {
            ObjectWriteResponse response = client.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(input, input.available(), 0).build());
            log.info("Upload FlighttaskCreateFile: {}", response.etag());
        } catch (MinioException | IOException | InvalidKeyException | NoSuchAlgorithmException ex) {
            log.error("Failed to upload FlighttaskCreateFile {}.", objectKey);
            ex.printStackTrace();
        }
    }

    @Override
    public List<String> listBuckets() {
        try {
            return client.listBuckets().stream()
                    .map(io.minio.messages.Bucket::name)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list buckets.");
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @Override
    public Boolean createBucket(String bucket) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (exists) {
                return true;
            }
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            return true;
        } catch (Exception e) {
            log.error("Failed to create bucket {}.", bucket);
            e.printStackTrace();
            return false;
        }
    }

    public void createClient() {
        if (Objects.nonNull(this.client)) {
            return;
        }
        this.client = MinioClient.builder()
                .endpoint(OssConfiguration.endpoint)
                .credentials(OssConfiguration.accessKey, OssConfiguration.secretKey)
                .region(OssConfiguration.region)
                .build();
    }

    private void categorize(BucketStat stat, String objectKey, long size) {
        String ext = getExtension(objectKey).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "webp":
            case "svg":
            case "ico":
                stat.setImageCount(stat.getImageCount() + 1);
                stat.setImageSize(stat.getImageSize() + size);
                break;
            case "mp4":
            case "avi":
            case "mov":
            case "mkv":
            case "flv":
            case "wmv":
            case "m4v":
            case "3gp":
                stat.setVideoCount(stat.getVideoCount() + 1);
                stat.setVideoSize(stat.getVideoSize() + size);
                break;
            default:
                stat.setOtherCount(stat.getOtherCount() + 1);
                stat.setOtherSize(stat.getOtherSize() + size);
        }
    }

    private String getExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1 || dotIndex == filename.length() - 1) ? "" : filename.substring(dotIndex + 1);
    }

    private void parseStorageInfo(Message root, StorageStat stat) {
        BigDecimal total = BigDecimal.valueOf(0);
        BigDecimal used = BigDecimal.valueOf(0);
        BigDecimal free = BigDecimal.valueOf(0);
        for (ServerProperties server : root.servers()) {
            for (Disk drive : server.disks()) {
                total = total.add(drive.totalspace());
                used = used.add(drive.usedspace());
                free = free.add(drive.availspace());
            }
        }
        stat.setTotalBytes(total);
        stat.setUsedBytes(used);
        stat.setFreeBytes(free);
    }
}

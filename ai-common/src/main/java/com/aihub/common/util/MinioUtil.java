package com.aihub.common.util;

import cn.hutool.core.util.IdUtil;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "io.minio.MinioClient")
public class MinioUtil {

    private final MinioClient minioClient;

    private static final String BUCKET_IMAGE = "aihub-images";
    private static final String BUCKET_VIDEO = "aihub-videos";
    private static final String BUCKET_AUDIO = "aihub-audio";
    private static final String BUCKET_FILE = "aihub-files";

    public String uploadImage(InputStream inputStream, long size, String contentType, String originalName) {
        return upload(BUCKET_IMAGE, inputStream, size, contentType, originalName);
    }

    public String uploadVideo(InputStream inputStream, long size, String originalName) {
        return upload(BUCKET_VIDEO, inputStream, size, "video/mp4", originalName);
    }

    public String uploadFile(InputStream inputStream, long size, String originalName) {
        return upload(BUCKET_FILE, inputStream, size, "application/octet-stream", originalName);
    }

    public String getImageUrl(String objectName) {
        return getPresignedUrl(BUCKET_IMAGE, objectName, 24);
    }

    public String getVideoUrl(String objectName) {
        return getPresignedUrl(BUCKET_VIDEO, objectName, 24);
    }

    private String upload(String bucket, InputStream inputStream, long size, String contentType, String originalName) {
        try {
            ensureBucket(bucket);

            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String objectName = IdUtil.fastSimpleUUID() + ext;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build());

            log.info("MinIO上传成功: bucket={}, object={}", bucket, objectName);
            return bucket + "/" + objectName;
        } catch (Exception e) {
            log.error("MinIO上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private String getPresignedUrl(String bucket, String objectName, int hours) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(hours, TimeUnit.HOURS)
                            .build());
        } catch (Exception e) {
            log.error("获取MinIO预签名URL失败", e);
            return "";
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}

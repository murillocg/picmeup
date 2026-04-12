package com.picmeup.photo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.StorageClass;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3StorageService(S3Client s3Client,
                            S3Presigner s3Presigner,
                            @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    public void uploadFile(String key, InputStream inputStream, long contentLength, String contentType) {
        uploadFile(key, inputStream, contentLength, contentType, null);
    }

    public void uploadFile(String key, InputStream inputStream, long contentLength, String contentType, StorageClass storageClass) {
        var builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);

        if (storageClass != null) {
            builder.storageClass(storageClass);
        }

        s3Client.putObject(builder.build(), RequestBody.fromInputStream(inputStream, contentLength));
    }

    public void uploadFile(String key, byte[] data, String contentType) {
        uploadFile(key, data, contentType, null);
    }

    public void uploadFile(String key, byte[] data, String contentType, StorageClass storageClass) {
        var builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);

        if (storageClass != null) {
            builder.storageClass(storageClass);
        }

        s3Client.putObject(builder.build(), RequestBody.fromBytes(data));
    }

    public String generatePresignedUrl(String key, Duration expiration) {
        return generatePresignedUrl(key, expiration, null);
    }

    public String generatePresignedUrl(String key, Duration expiration, String downloadFilename) {
        var presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(builder -> {
                    builder.bucket(bucket).key(key);
                    if (downloadFilename != null) {
                        builder.responseContentDisposition("attachment; filename=\"" + downloadFilename + "\"");
                    }
                })
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public InputStream getObject(String key) {
        var request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.getObject(request);
    }

    public void deleteFile(String key) {
        var request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.deleteObject(request);
    }

    public void deleteByPrefix(String prefix) {
        var listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        var response = s3Client.listObjectsV2(listRequest);

        while (true) {
            for (S3Object object : response.contents()) {
                deleteFile(object.key());
            }

            if (!response.isTruncated()) {
                break;
            }

            listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(response.nextContinuationToken())
                    .build();

            response = s3Client.listObjectsV2(listRequest);
        }
    }
}

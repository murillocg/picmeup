package com.picmeup.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.StorageClass;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

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

    public String generatePresignedUploadUrl(String key, Duration expiration, String contentType, StorageClass storageClass) {
        var builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType);

        if (storageClass != null) {
            builder.storageClass(storageClass);
        }

        var presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(builder.build())
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    /**
     * An output stream that uploads to S3 in parts as it is written, so an object larger
     * than the heap can be produced without staging it anywhere. See
     * {@link S3MultipartOutputStream} for the threading contract.
     */
    public S3MultipartOutputStream multipartOutputStream(String key, String contentType) {
        return new S3MultipartOutputStream(s3Client, bucket, key, contentType);
    }

    /** Whether an object exists, without transferring it. */
    public boolean exists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
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

    public long getBytesUsedByPrefix(String prefix) {
        long totalBytes = 0;
        var listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        var response = s3Client.listObjectsV2(listRequest);
        while (true) {
            for (S3Object object : response.contents()) {
                totalBytes += object.size();
            }
            if (!response.isTruncated()) break;
            listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(response.nextContinuationToken())
                    .build();
            response = s3Client.listObjectsV2(listRequest);
        }
        return totalBytes;
    }

    public void deleteByPrefix(String prefix) {
        var listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        var response = s3Client.listObjectsV2(listRequest);

        while (true) {
            deleteBatch(response.contents());

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

    /**
     * One request per listing page rather than one per object. Deleting an event used to
     * issue a round trip per key, which ran past seven minutes for a large event and timed
     * the caller out long before it finished. A listing page and a batch delete are both
     * capped at 1,000 keys, so each page costs exactly one delete call.
     */
    private void deleteBatch(List<S3Object> objects) {
        if (objects.isEmpty()) {
            return;
        }

        var keys = objects.stream()
                .map(object -> ObjectIdentifier.builder().key(object.key()).build())
                .toList();

        var result = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(keys).build())
                .build());

        // A batch reports per-key failures in its response instead of throwing. Anything
        // left behind is swept by the bucket lifecycle, so this is worth recording but not
        // worth abandoning the remaining pages over.
        if (!result.errors().isEmpty()) {
            var first = result.errors().get(0);
            log.warn("Failed to delete {} of {} objects; first failure: {} ({})",
                    result.errors().size(), keys.size(), first.key(), first.message());
        }
    }
}

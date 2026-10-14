package com.picmeup.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes to S3 as the data arrives, so an object of any size can be produced without
 * holding it in memory or staging it on disk.
 *
 * <p>Content accumulates in a single fixed buffer until it reaches S3's five-megabyte
 * minimum part size, then goes up as a part and the buffer is refilled. Memory is exactly
 * one part for the lifetime of the stream, whatever the finished object weighs — which is
 * what makes it safe to build a 400 MB download on an instance with a 512 MB heap. The
 * buffer is deliberately reused rather than copied out on each flush, so the figure a
 * caller budgets against is the real one.
 *
 * <p>One instance serves one upload, written by one thread from construction to
 * {@link #close()}. Part numbering and the buffer flush are sequences rather than atomic
 * operations, so sharing an instance across threads would interleave parts. Locking here
 * would not help anyway: the invariant that matters is one builder per object, and two
 * threads would simply create two instances aimed at the same key. That is enforced by
 * the caller.
 */
public class S3MultipartOutputStream extends OutputStream {

    private static final Logger log = LoggerFactory.getLogger(S3MultipartOutputStream.class);

    /** S3 rejects parts below this, except the last one. */
    private static final int PART_SIZE = 5 * 1024 * 1024;

    private final S3Client s3Client;
    private final String bucket;
    private final String key;
    private final String uploadId;

    private final byte[] buffer = new byte[PART_SIZE];
    private int filled;
    private final List<CompletedPart> completedParts = new ArrayList<>();
    private int partNumber = 1;
    private boolean finished;

    S3MultipartOutputStream(S3Client s3Client, String bucket, String key, String contentType) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.key = key;
        this.uploadId = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build()).uploadId();
    }

    @Override
    public void write(int b) {
        buffer[filled++] = (byte) b;
        if (filled == PART_SIZE) {
            uploadPart();
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        while (length > 0) {
            int chunk = Math.min(length, PART_SIZE - filled);
            System.arraycopy(bytes, offset, buffer, filled, chunk);
            filled += chunk;
            offset += chunk;
            length -= chunk;

            if (filled == PART_SIZE) {
                uploadPart();
            }
        }
    }

    private void uploadPart() {
        // A ByteArrayInputStream view rather than a copy: the SDK streams from it, and it
        // supports reset, which the SDK needs if it retries the part.
        var response = s3Client.uploadPart(UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) filled)
                        .build(),
                RequestBody.fromInputStream(new ByteArrayInputStream(buffer, 0, filled), filled));

        filled = 0;

        completedParts.add(CompletedPart.builder()
                .partNumber(partNumber)
                .eTag(response.eTag())
                .build());
        partNumber++;
    }

    /**
     * Discards the upload. Parts already sent are billed but do not appear in a bucket
     * listing, so abandoning them is a silent cost — this is how they are reclaimed when
     * a build fails part-way. Safe to call after {@link #close()}, and safe to call twice.
     */
    public void abort() {
        if (finished) {
            return;
        }
        finished = true;
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(bucket).key(key).uploadId(uploadId).build());
        } catch (RuntimeException e) {
            // The bucket's lifecycle rule reclaims it if this fails.
            log.warn("Could not abort multipart upload for {}", key, e);
        }
    }

    @Override
    public void close() throws IOException {
        if (finished) {
            return;
        }

        // Whatever remains is the final part, which may legitimately be under the
        // minimum size.
        if (filled > 0) {
            uploadPart();
        }

        if (completedParts.isEmpty()) {
            abort();
            throw new IOException("Refusing to complete an empty upload for " + key);
        }

        try {
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build())
                    .build());
            finished = true;
        } catch (RuntimeException e) {
            abort();
            throw new IOException("Failed to complete upload for " + key, e);
        }
    }
}

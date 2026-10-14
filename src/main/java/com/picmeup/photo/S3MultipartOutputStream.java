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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes to S3 as the data arrives, so an object of any size can be produced without
 * holding it in memory or staging it on disk.
 *
 * <p>Content accumulates until it reaches S3's five-megabyte minimum part size, then goes
 * up as a part and the buffer is reused. Peak memory is one part regardless of how large
 * the finished object is — which is what makes it safe to build a 400 MB download on an
 * instance with a 512 MB heap.
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

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream(PART_SIZE);
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
        buffer.write(b);
        flushPartIfFull();
    }

    @Override
    public void write(byte[] bytes, int offset, int length) {
        buffer.write(bytes, offset, length);
        flushPartIfFull();
    }

    private void flushPartIfFull() {
        if (buffer.size() >= PART_SIZE) {
            uploadPart();
        }
    }

    private void uploadPart() {
        byte[] part = buffer.toByteArray();
        buffer.reset();

        var response = s3Client.uploadPart(UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) part.length)
                        .build(),
                RequestBody.fromBytes(part));

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
        if (buffer.size() > 0) {
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

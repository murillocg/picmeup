package com.picmeup.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new S3StorageService(s3Client, s3Presigner, "test-bucket");
    }

    @Test
    void uploadFile_withBytes_shouldPutObject() {
        byte[] data = "test content".getBytes();

        storageService.uploadFile("photos/test.jpg", data, "image/jpeg");

        var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        var request = captor.getValue();
        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo("photos/test.jpg");
        assertThat(request.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void generatePresignedUrl_shouldCallPresigner() throws Exception {
        var presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(URI.create("https://test-bucket.s3.amazonaws.com/photos/test.jpg?signed=true").toURL());
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        var url = storageService.generatePresignedUrl("photos/test.jpg", Duration.ofHours(24));

        assertThat(url).contains("test-bucket");
        assertThat(url).contains("photos/test.jpg");
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

    @Test
    void deleteFile_shouldDeleteObject() {
        storageService.deleteFile("photos/test.jpg");

        var captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());

        assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(captor.getValue().key()).isEqualTo("photos/test.jpg");
    }

    @Test
    void deleteByPrefix_shouldDeleteAllMatchingObjects() {
        var objects = List.of(
                S3Object.builder().key("originals/event1/photo1.jpg").build(),
                S3Object.builder().key("originals/event1/photo2.jpg").build()
        );

        var response = ListObjectsV2Response.builder()
                .contents(objects)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

        storageService.deleteByPrefix("originals/event1/");

        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
    }
}

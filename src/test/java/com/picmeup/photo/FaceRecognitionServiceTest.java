package com.picmeup.photo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionResponse;
import software.amazon.awssdk.services.rekognition.model.Face;
import software.amazon.awssdk.services.rekognition.model.FaceMatch;
import software.amazon.awssdk.services.rekognition.model.FaceRecord;
import software.amazon.awssdk.services.rekognition.model.IndexFacesRequest;
import software.amazon.awssdk.services.rekognition.model.IndexFacesResponse;
import software.amazon.awssdk.services.rekognition.model.ResourceNotFoundException;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageRequest;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaceRecognitionServiceTest {

    @Mock
    private RekognitionClient rekognitionClient;

    private FaceRecognitionService faceRecognitionService;

    private final UUID eventId = UUID.randomUUID();
    private final UUID photoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        faceRecognitionService = new FaceRecognitionService(rekognitionClient, "picmeup-photos-test", 80.0f);
    }

    @Test
    void createCollection_shouldCallRekognition() {
        when(rekognitionClient.createCollection(any(CreateCollectionRequest.class)))
                .thenReturn(CreateCollectionResponse.builder().statusCode(200).build());

        faceRecognitionService.createCollection(eventId);

        var captor = ArgumentCaptor.forClass(CreateCollectionRequest.class);
        verify(rekognitionClient).createCollection(captor.capture());
        assertThat(captor.getValue().collectionId()).isEqualTo("picmeup-event-" + eventId);
    }

    @Test
    void indexFaces_shouldReturnFaceIds() {
        var face = Face.builder().faceId("face-123").build();
        var faceRecord = FaceRecord.builder().face(face).build();
        var response = IndexFacesResponse.builder().faceRecords(faceRecord).build();

        when(rekognitionClient.indexFaces(any(IndexFacesRequest.class))).thenReturn(response);

        var faceIds = faceRecognitionService.indexFaces(eventId, photoId, "originals/" + eventId + "/" + photoId + ".jpg");

        assertThat(faceIds).containsExactly("face-123");

        var captor = ArgumentCaptor.forClass(IndexFacesRequest.class);
        verify(rekognitionClient).indexFaces(captor.capture());
        assertThat(captor.getValue().externalImageId()).isEqualTo(photoId.toString());
    }

    @Test
    void indexFaces_shouldCreateCollectionIfNotFound() {
        when(rekognitionClient.indexFaces(any(IndexFacesRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("not found").build())
                .thenReturn(IndexFacesResponse.builder().faceRecords(java.util.List.of()).build());
        when(rekognitionClient.createCollection(any(CreateCollectionRequest.class)))
                .thenReturn(CreateCollectionResponse.builder().statusCode(200).build());

        var faceIds = faceRecognitionService.indexFaces(eventId, photoId, "originals/" + eventId + "/" + photoId + ".jpg");

        assertThat(faceIds).isEmpty();
        verify(rekognitionClient).createCollection(any(CreateCollectionRequest.class));
        verify(rekognitionClient, times(2)).indexFaces(any(IndexFacesRequest.class));
    }

    @Test
    void searchByFace_shouldReturnMatchedPhotoIds() {
        var face = Face.builder().faceId("face-1").externalImageId(photoId.toString()).build();
        var match = FaceMatch.builder().face(face).similarity(95.0f).build();
        var response = SearchFacesByImageResponse.builder().faceMatches(match).build();

        when(rekognitionClient.searchFacesByImage(any(SearchFacesByImageRequest.class)))
                .thenReturn(response);

        var result = faceRecognitionService.searchByFace(eventId, new byte[]{1, 2, 3});

        assertThat(result).containsExactly(photoId);

        var captor = ArgumentCaptor.forClass(SearchFacesByImageRequest.class);
        verify(rekognitionClient).searchFacesByImage(captor.capture());
        assertThat(captor.getValue().faceMatchThreshold()).isEqualTo(80.0f);
    }

    @Test
    void searchByFace_shouldDeduplicatePhotoIds() {
        var face1 = Face.builder().faceId("face-1").externalImageId(photoId.toString()).build();
        var face2 = Face.builder().faceId("face-2").externalImageId(photoId.toString()).build();
        var response = SearchFacesByImageResponse.builder()
                .faceMatches(
                        FaceMatch.builder().face(face1).similarity(95.0f).build(),
                        FaceMatch.builder().face(face2).similarity(90.0f).build()
                ).build();

        when(rekognitionClient.searchFacesByImage(any(SearchFacesByImageRequest.class)))
                .thenReturn(response);

        var result = faceRecognitionService.searchByFace(eventId, new byte[]{1, 2, 3});

        assertThat(result).hasSize(1);
    }

    @Test
    void deleteCollection_shouldCallRekognition() {
        when(rekognitionClient.deleteCollection(any(DeleteCollectionRequest.class)))
                .thenReturn(DeleteCollectionResponse.builder().statusCode(200).build());

        faceRecognitionService.deleteCollection(eventId);

        verify(rekognitionClient).deleteCollection(any(DeleteCollectionRequest.class));
    }

    @Test
    void deleteCollection_shouldHandleNotFound() {
        when(rekognitionClient.deleteCollection(any(DeleteCollectionRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("not found").build());

        faceRecognitionService.deleteCollection(eventId);

        verify(rekognitionClient).deleteCollection(any(DeleteCollectionRequest.class));
    }
}

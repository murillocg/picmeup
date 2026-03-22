package com.picmeup.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.Attribute;
import software.amazon.awssdk.services.rekognition.model.CreateCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.DeleteCollectionRequest;
import software.amazon.awssdk.services.rekognition.model.FaceMatch;
import software.amazon.awssdk.services.rekognition.model.Image;
import software.amazon.awssdk.services.rekognition.model.IndexFacesRequest;
import software.amazon.awssdk.services.rekognition.model.QualityFilter;
import software.amazon.awssdk.services.rekognition.model.ResourceNotFoundException;
import software.amazon.awssdk.services.rekognition.model.SearchFacesByImageRequest;

import java.util.List;
import java.util.UUID;

@Service
public class FaceRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(FaceRecognitionService.class);

    private final RekognitionClient rekognitionClient;
    private final float confidenceThreshold;

    public FaceRecognitionService(RekognitionClient rekognitionClient,
                                  @Value("${aws.rekognition.confidence-threshold}") float confidenceThreshold) {
        this.rekognitionClient = rekognitionClient;
        this.confidenceThreshold = confidenceThreshold;
    }

    private String collectionId(UUID eventId) {
        return "picmeup-event-" + eventId;
    }

    public void createCollection(UUID eventId) {
        var request = CreateCollectionRequest.builder()
                .collectionId(collectionId(eventId))
                .build();

        var response = rekognitionClient.createCollection(request);
        log.info("Created Rekognition collection {} (status: {})", collectionId(eventId), response.statusCode());
    }

    public String[] indexFaces(UUID eventId, UUID photoId, byte[] imageBytes) {
        var image = Image.builder()
                .bytes(SdkBytes.fromByteArray(imageBytes))
                .build();

        var request = IndexFacesRequest.builder()
                .collectionId(collectionId(eventId))
                .image(image)
                .externalImageId(photoId.toString())
                .detectionAttributes(Attribute.DEFAULT)
                .qualityFilter(QualityFilter.AUTO)
                .build();

        try {
            var response = rekognitionClient.indexFaces(request);
            var faceIds = response.faceRecords().stream()
                    .map(record -> record.face().faceId())
                    .toArray(String[]::new);

            log.info("Indexed {} faces for photo {} in event {}", faceIds.length, photoId, eventId);
            return faceIds;
        } catch (ResourceNotFoundException e) {
            log.warn("Collection not found for event {}, creating it", eventId);
            createCollection(eventId);
            return indexFaces(eventId, photoId, imageBytes);
        }
    }

    public List<UUID> searchByFace(UUID eventId, byte[] selfieBytes) {
        var image = Image.builder()
                .bytes(SdkBytes.fromByteArray(selfieBytes))
                .build();

        var request = SearchFacesByImageRequest.builder()
                .collectionId(collectionId(eventId))
                .image(image)
                .faceMatchThreshold(confidenceThreshold)
                .maxFaces(100)
                .build();

        var response = rekognitionClient.searchFacesByImage(request);

        return response.faceMatches().stream()
                .map(FaceMatch::face)
                .map(face -> UUID.fromString(face.externalImageId()))
                .distinct()
                .toList();
    }

    public void deleteCollection(UUID eventId) {
        try {
            var request = DeleteCollectionRequest.builder()
                    .collectionId(collectionId(eventId))
                    .build();

            rekognitionClient.deleteCollection(request);
            log.info("Deleted Rekognition collection {}", collectionId(eventId));
        } catch (ResourceNotFoundException e) {
            log.warn("Collection {} not found, skipping deletion", collectionId(eventId));
        }
    }
}

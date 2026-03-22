package com.picmeup.common.interfaces;

import java.util.List;
import java.util.UUID;

/**
 * Interface for payment-service to look up photo details without
 * depending directly on photo-service JPA entities.
 * When services are split, this becomes a REST client.
 */
public interface PhotoLookupService {

    boolean allPhotosExistAndActive(List<UUID> photoIds);

    String getOriginalS3Key(UUID photoId);
}

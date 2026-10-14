package com.picmeup.payment;

import com.picmeup.photo.PhotoRepository;
import com.picmeup.photo.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the "download everything" archive for an order once, into S3, and hands out a
 * presigned link to it.
 *
 * <p>Previously the archive was generated on the fly and streamed through this
 * application on every request. Because its size could not be known in advance the
 * response carried no {@code Content-Length}, so a browser could not tell a complete
 * download from a truncated one: any interruption during a multi-minute transfer produced
 * a corrupt file that still looked like a successful download. Serving it from S3 means
 * the browser gets a length and range support, so truncation is detected and an
 * interrupted download resumes.
 */
@Service
public class OrderZipService {

    private static final Logger log = LoggerFactory.getLogger(OrderZipService.class);

    private final String siteUrl;

    /**
     * How many archives may be built at once.
     *
     * <p>Virtual threads are enabled, so {@code @Async} hands every call its own thread
     * with no pool and no ceiling — the same unbounded concurrency that exhausted the heap
     * twice before. Each build holds one five-megabyte upload buffer, so three is roughly
     * fifteen megabytes of a 512 MB heap, and at the measured peak of three requests a
     * second it is far more capacity than the site needs.
     *
     * <p>Waiting for a permit is cheap here: a blocked virtual thread parks without
     * holding an operating-system thread, so callers queue rather than pile up work.
     */
    private static final int MAX_CONCURRENT_BUILDS = 3;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PhotoRepository photoRepository;
    private final S3StorageService s3StorageService;

    /**
     * Orders currently being built, so a second request does not start the work again.
     * A duplicate build would be wasteful rather than harmful — both produce a valid
     * archive and S3 keeps whichever finishes last — so in-memory tracking on a single
     * instance is enough, and losing it on restart only risks one redundant rebuild.
     */
    private final Set<UUID> building = ConcurrentHashMap.newKeySet();

    private final Semaphore buildPermits = new Semaphore(MAX_CONCURRENT_BUILDS);

    public OrderZipService(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           PhotoRepository photoRepository,
                           S3StorageService s3StorageService,
                           @Value("${app.site-url:https://www.elitesportphotos.com}") String siteUrl) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.photoRepository = photoRepository;
        this.s3StorageService = s3StorageService;
        this.siteUrl = siteUrl;
    }

    public static String zipKey(UUID orderId) {
        return "zips/" + orderId + ".zip";
    }

    /**
     * A link to the finished archive, or empty while it is still being built.
     *
     * <p>Served through CloudFront rather than as a presigned S3 link. A presigned link
     * sends the viewer straight to the bucket in Sydney over the public internet, which
     * measured five times slower from Brazil than the same bytes through the edge. The
     * path is protected by the order's own identifier, the same secret that protects the
     * order page itself.
     */
    public java.util.Optional<String> downloadUrl(UUID orderId) {
        if (!s3StorageService.exists(zipKey(orderId))) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(siteUrl + "/" + zipKey(orderId));
    }

    /**
     * Starts a build unless the archive already exists or one is already running.
     *
     * <p>The whole method is asynchronous rather than delegating to an inner one: Spring
     * applies {@code @Async} through a proxy, so a call from inside this class would be
     * invisible to it and would run the build on the caller's request thread.
     */
    @Async
    public void requestBuild(UUID orderId) {
        if (s3StorageService.exists(zipKey(orderId)) || !building.add(orderId)) {
            return;
        }

        try {
            buildPermits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            building.remove(orderId);
            return;
        }

        String key = zipKey(orderId);
        long started = System.currentTimeMillis();
        var stream = s3StorageService.multipartOutputStream(key, "application/zip");

        try (var zip = new ZipOutputStream(stream)) {
            // The photos are JPEGs, which do not compress. Deflating them costs real CPU
            // on a small instance and saves essentially nothing, so the entries are stored
            // as-is. NO_COMPRESSION keeps the deflate method, which — unlike STORED — does
            // not require knowing each entry's size and checksum up front.
            zip.setLevel(Deflater.NO_COMPRESSION);

            int written = 0;
            for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
                var photo = photoRepository.findById(item.getPhotoId()).orElse(null);
                if (photo == null || photo.getOriginalS3Key() == null) {
                    continue;
                }

                String filename = photo.getOriginalFilename() != null
                        ? photo.getOriginalFilename()
                        : "photo-" + (written + 1) + ".jpg";

                zip.putNextEntry(new ZipEntry(filename));
                try (var source = s3StorageService.getObject(photo.getOriginalS3Key())) {
                    source.transferTo(zip);
                }
                zip.closeEntry();
                written++;
            }

            if (written == 0) {
                throw new IOException("Order " + orderId + " has no downloadable photos");
            }

            log.info("Built archive for order {} with {} photos in {} ms",
                    orderId, written, System.currentTimeMillis() - started);
        } catch (IOException | RuntimeException e) {
            // Parts already uploaded are billed but invisible to a bucket listing, so
            // they are discarded rather than left behind. The bucket's lifecycle rule
            // covers the case where this process dies before reaching here.
            stream.abort();
            log.error("Failed to build archive for order {}", orderId, e);
        } finally {
            buildPermits.release();
            building.remove(orderId);
        }
    }

    /** Whether the order may be downloaded at all. */
    public boolean isPaid(UUID orderId) {
        return orderRepository.findById(orderId)
                .map(order -> order.getStatus() == Order.Status.PAID)
                .orElse(false);
    }
}

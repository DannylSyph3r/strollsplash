package dev.slethware.strollsplash.service.storage;

import dev.slethware.strollsplash.exception.InternalServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final S3Client s3Client;

    @Value("${storage.bucket-name}")
    private String bucketName;

    @Value("${storage.public-url}")
    private String publicUrl;

    @Override
    public void upload(String key, String contentType, byte[] data) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(data));
            log.info("Video uploaded to storage: key={}", key);
        } catch (SdkException e) {
            log.error("Failed to upload video to storage: key={}", key, e);
            throw new InternalServerException("Failed to upload video to storage", e);
        }
    }

    @Override
    public String buildPublicUrl(String key) {
        String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        return base + "/" + key;
    }
}

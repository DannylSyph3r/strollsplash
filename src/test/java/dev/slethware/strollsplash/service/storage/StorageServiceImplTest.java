package dev.slethware.strollsplash.service.storage;

import dev.slethware.strollsplash.exception.InternalServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    @Mock
    private S3Client s3Client;

    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageServiceImpl(s3Client);
        ReflectionTestUtils.setField(storageService, "bucketName", "splash-bucket");
        ReflectionTestUtils.setField(storageService, "publicUrl", "https://cdn.test");
    }

    @Test
    void buildPublicUrlJoinsBaseAndKey() {
        assertThat(storageService.buildPublicUrl("splashes/a.mp4")).isEqualTo("https://cdn.test/splashes/a.mp4");
    }

    @Test
    void buildPublicUrlIgnoresATrailingSlashOnTheBase() {
        ReflectionTestUtils.setField(storageService, "publicUrl", "https://cdn.test/");

        assertThat(storageService.buildPublicUrl("splashes/a.mp4")).isEqualTo("https://cdn.test/splashes/a.mp4");
    }

    @Test
    void uploadSendsBucketKeyContentTypeAndLength() {
        storageService.upload("splashes/a.mp4", "video/mp4", new byte[]{1, 2, 3});

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("splash-bucket");
        assertThat(request.getValue().key()).isEqualTo("splashes/a.mp4");
        assertThat(request.getValue().contentType()).isEqualTo("video/mp4");
        assertThat(request.getValue().contentLength()).isEqualTo(3L);
    }

    @Test
    void storageFailureBecomesAnInternalServerExceptionWithTheContractMessage() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("connection refused"));

        assertThatThrownBy(() -> storageService.upload("splashes/a.mp4", "video/mp4", new byte[]{1}))
                .isInstanceOf(InternalServerException.class)
                .hasMessage("Failed to upload video to storage");
    }
}

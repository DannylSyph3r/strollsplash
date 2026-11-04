package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.dto.request.UpdateSplashRequest;
import dev.slethware.strollsplash.dto.response.AdminSplashResponse;
import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.enums.SplashKind;
import dev.slethware.strollsplash.exception.BadRequestException;
import dev.slethware.strollsplash.exception.ResourceNotFoundException;
import dev.slethware.strollsplash.repository.SplashVideoRepository;
import dev.slethware.strollsplash.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import dev.slethware.strollsplash.exception.InternalServerException;
import dev.slethware.strollsplash.utility.ChecksumUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminSplashServiceImplTest {

    private static final UUID ID = UUID.fromString("7d9c3f0e-5b1a-4c55-9d3e-0a1f6b2c8e11");
    private static final Instant START = Instant.parse("2026-12-24T00:00:00Z");
    private static final Instant END = Instant.parse("2026-12-27T00:00:00Z");

    @Mock
    private SplashVideoRepository repository;

    @Mock
    private SplashPoolCache splashPoolCache;

    @Mock
    private StorageService storageService;

    private AdminSplashServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminSplashServiceImpl(repository, new SplashValidator(), splashPoolCache, storageService);
        lenient().when(repository.save(any(SplashVideo.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(storageService.buildPublicUrl(any())).thenAnswer(call -> "https://cdn.test/" + call.getArgument(0));
    }

    private SplashVideo standardRow() {
        return SplashVideo.builder().id(ID).title("Original").kind(SplashKind.STANDARD)
                .objectKey("splashes/a.mp4").checksum("sum").sizeBytes(10L).active(true).build();
    }

    private SplashVideo eventRow() {
        return SplashVideo.builder().id(ID).title("Christmas").kind(SplashKind.EVENT)
                .objectKey("splashes/c.mp4").checksum("sum").sizeBytes(10L).active(true)
                .startsAt(START).endsAt(END).build();
    }

    private UpdateSplashRequest request() {
        return new UpdateSplashRequest();
    }

    private void rowExists(SplashVideo row) {
        when(repository.findById(ID)).thenReturn(Optional.of(row));
    }

    @Test
    void listMapsEveryRowOfThePageAndReportsTheTotal() {
        SplashVideo newer = standardRow();
        newer.setTitle("newer");
        SplashVideo older = eventRow();
        older.setTitle("older");
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(newer, older), PageRequest.of(0, 2), 5));

        Page<AdminSplashResponse> page = service.getAllSplashes(0, 2);

        assertThat(page.getContent()).extracting(AdminSplashResponse::getTitle).containsExactly("newer", "older");
        assertThat(page.getContent().getFirst().getUrl()).isEqualTo("https://cdn.test/splashes/a.mp4");
        assertThat(page.getContent().get(1).getStartsAt()).isEqualTo(START);
        assertThat(page.getTotalElements()).isEqualTo(5);
    }

    @Test
    void listAsksForTheRequestedPageNewestFirstWithTheIdAsTieBreak() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);

        service.getAllSplashes(3, 25);

        verify(repository).findAll(asked.capture());
        assertThat(asked.getValue().getPageNumber()).isEqualTo(3);
        assertThat(asked.getValue().getPageSize()).isEqualTo(25);
        assertThat(asked.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void listAcceptsTheSmallestAndLargestPageSize(int size) {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.getAllSplashes(0, size);

        verify(repository).findAll(any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void listRejectsASizeOutsideOneToOneHundred(int size) {
        assertThatThrownBy(() -> service.getAllSplashes(0, size))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("size must be between 1 and 100");
        verifyNoInteractions(repository);
    }

    @Test
    void listRejectsANegativePage() {
        assertThatThrownBy(() -> service.getAllSplashes(-1, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("page must be 0 or more");
        verifyNoInteractions(repository);
    }

    @Test
    void listReportsBothBadValuesTogether() {
        assertThatThrownBy(() -> service.getAllSplashes(-1, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("page must be 0 or more; size must be between 1 and 100");
    }

    @Test
    void pinnedOnlyChangesPinned() {
        SplashVideo row = eventRow();
        rowExists(row);
        UpdateSplashRequest request = request();
        request.setPinned(true);

        AdminSplashResponse response = service.updateSplash(ID, request);

        assertThat(response.isPinned()).isTrue();
        assertThat(response.getTitle()).isEqualTo("Christmas");
        assertThat(response.getKind()).isEqualTo(SplashKind.EVENT);
        assertThat(response.isActive()).isTrue();
        assertThat(response.getStartsAt()).isEqualTo(START);
        assertThat(response.getEndsAt()).isEqualTo(END);
    }

    @Test
    void killSwitchOnAnEventNeedsNoTimestamps() {
        rowExists(eventRow());
        UpdateSplashRequest request = request();
        request.setActive(false);

        AdminSplashResponse response = service.updateSplash(ID, request);

        assertThat(response.isActive()).isFalse();
        assertThat(response.getStartsAt()).isEqualTo(START);
        assertThat(response.getEndsAt()).isEqualTo(END);
    }

    @Test
    void switchingAnEventToStandardClearsTheWindow() {
        rowExists(eventRow());
        UpdateSplashRequest request = request();
        request.setKind(SplashKind.STANDARD);

        AdminSplashResponse response = service.updateSplash(ID, request);

        assertThat(response.getKind()).isEqualTo(SplashKind.STANDARD);
        assertThat(response.getStartsAt()).isNull();
        assertThat(response.getEndsAt()).isNull();
    }

    @Test
    void switchingAnEventToStandardWhileSendingAWindowIsRejected() {
        rowExists(eventRow());
        UpdateSplashRequest request = request();
        request.setKind(SplashKind.STANDARD);
        request.setStartsAt(START);

        assertThatThrownBy(() -> service.updateSplash(ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("startsAt and endsAt must not be set when kind is STANDARD");
    }

    @Test
    void switchingStandardToEventWithoutTimestampsGetsBothRequiredMessages() {
        rowExists(standardRow());
        UpdateSplashRequest request = request();
        request.setKind(SplashKind.EVENT);

        assertThatThrownBy(() -> service.updateSplash(ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("startsAt is required when kind is EVENT; endsAt is required when kind is EVENT");
    }

    @Test
    void switchingStandardToEventWithBothTimestampsWorks() {
        rowExists(standardRow());
        UpdateSplashRequest request = request();
        request.setKind(SplashKind.EVENT);
        request.setStartsAt(START);
        request.setEndsAt(END);

        AdminSplashResponse response = service.updateSplash(ID, request);

        assertThat(response.getKind()).isEqualTo(SplashKind.EVENT);
        assertThat(response.getStartsAt()).isEqualTo(START);
        assertThat(response.getEndsAt()).isEqualTo(END);
    }

    @Test
    void endsAtBeforeTheStoredStartsAtIsRejected() {
        rowExists(eventRow());
        UpdateSplashRequest request = request();
        request.setEndsAt(START.minusSeconds(60));

        assertThatThrownBy(() -> service.updateSplash(ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("endsAt must be after startsAt");
    }

    @Test
    void startsAtOnAStandardRowIsRejected() {
        rowExists(standardRow());
        UpdateSplashRequest request = request();
        request.setStartsAt(START);

        assertThatThrownBy(() -> service.updateSplash(ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("startsAt and endsAt must not be set when kind is STANDARD");
    }

    @Test
    void blankTitleIsRejected() {
        rowExists(standardRow());
        UpdateSplashRequest request = request();
        request.setTitle("  ");

        assertThatThrownBy(() -> service.updateSplash(ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("title must not be blank");
    }

    @Test
    void emptyRequestIsRejectedBeforeTheRowIsLoaded() {
        assertThatThrownBy(() -> service.updateSplash(ID, request()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least one field must be provided");

        verifyNoInteractions(repository, splashPoolCache);
    }

    @Test
    void unknownIdGives404() {
        when(repository.findById(ID)).thenReturn(Optional.empty());
        UpdateSplashRequest request = request();
        request.setPinned(true);

        assertThatThrownBy(() -> service.updateSplash(ID, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Splash video not found");
    }

    @Test
    void nullFieldsBehaveLikeAbsentOnes() {
        rowExists(eventRow());
        UpdateSplashRequest request = request();
        request.setEndsAt(null);
        request.setTitle(null);
        request.setPinned(true);

        AdminSplashResponse response = service.updateSplash(ID, request);

        assertThat(response.getEndsAt()).isEqualTo(END);
        assertThat(response.getTitle()).isEqualTo("Christmas");
    }

    @Test
    void cacheIsInvalidatedAfterTheSave() {
        rowExists(standardRow());
        UpdateSplashRequest request = request();
        request.setActive(false);

        service.updateSplash(ID, request);

        InOrder order = inOrder(repository, splashPoolCache);
        order.verify(repository).save(any(SplashVideo.class));
        order.verify(splashPoolCache).invalidate();
    }

    @Test
    void cacheIsNotInvalidatedWhenValidationFails() {
        rowExists(standardRow());
        UpdateSplashRequest request = request();
        request.setStartsAt(START);

        assertThatThrownBy(() -> service.updateSplash(ID, request)).isInstanceOf(BadRequestException.class);

        verify(repository, never()).save(any());
        verify(splashPoolCache, never()).invalidate();
    }

    @Test
    void cacheIsNotInvalidatedWhenTheSaveFails() {
        rowExists(standardRow());
        when(repository.save(any(SplashVideo.class))).thenThrow(new IllegalStateException("db down"));
        UpdateSplashRequest request = request();
        request.setPinned(true);

        assertThatThrownBy(() -> service.updateSplash(ID, request)).isInstanceOf(IllegalStateException.class);

        verify(splashPoolCache, never()).invalidate();
    }

    private static final byte[] VIDEO_BYTES = {1, 2, 3, 4, 5};

    private MockMultipartFile mp4() {
        return new MockMultipartFile("file", "clip.mp4", "video/mp4", VIDEO_BYTES);
    }

    @Test
    void uploadStoresChecksumAndSizeAndStartsInactiveAndUnpinned() {
        service.uploadSplash(mp4(), "Original", SplashKind.STANDARD, null, null);

        ArgumentCaptor<SplashVideo> saved = ArgumentCaptor.forClass(SplashVideo.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getChecksum()).isEqualTo(ChecksumUtil.sha256Hex(VIDEO_BYTES));
        assertThat(saved.getValue().getSizeBytes()).isEqualTo(5L);
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(saved.getValue().isPinned()).isFalse();
        assertThat(saved.getValue().getTitle()).isEqualTo("Original");
        assertThat(saved.getValue().getKind()).isEqualTo(SplashKind.STANDARD);
    }

    @Test
    void uploadKeyIsGeneratedAndIgnoresTheClientFileName() {
        service.uploadSplash(new MockMultipartFile("file", "../../evil.mp4", "video/mp4", VIDEO_BYTES),
                "Original", SplashKind.STANDARD, null, null);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storageService).upload(key.capture(), eq("video/mp4"), eq(VIDEO_BYTES));
        assertThat(key.getValue()).matches("splashes/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.mp4");
    }

    @Test
    void uploadReturnsTheAdminResponseWithAPublicUrl() {
        AdminSplashResponse response = service.uploadSplash(mp4(), "Original", SplashKind.STANDARD, null, null);

        assertThat(response.getTitle()).isEqualTo("Original");
        assertThat(response.getUrl()).startsWith("https://cdn.test/splashes/").endsWith(".mp4");
        assertThat(response.isActive()).isFalse();
    }

    @Test
    void uploadedEventKeepsItsWindow() {
        AdminSplashResponse response = service.uploadSplash(mp4(), "Christmas", SplashKind.EVENT, START, END);

        assertThat(response.getKind()).isEqualTo(SplashKind.EVENT);
        assertThat(response.getStartsAt()).isEqualTo(START);
        assertThat(response.getEndsAt()).isEqualTo(END);
    }

    @Test
    void storageIsCalledBeforeTheRowIsSaved() {
        service.uploadSplash(mp4(), "Original", SplashKind.STANDARD, null, null);

        InOrder order = inOrder(storageService, repository);
        order.verify(storageService).upload(any(), any(), any());
        order.verify(repository).save(any(SplashVideo.class));
    }

    @Test
    void storageFailureSavesNoRow() {
        doThrow(new InternalServerException("Failed to upload video to storage"))
                .when(storageService).upload(any(), any(), any());

        assertThatThrownBy(() -> service.uploadSplash(mp4(), "Original", SplashKind.STANDARD, null, null))
                .isInstanceOf(InternalServerException.class)
                .hasMessage("Failed to upload video to storage");

        verify(repository, never()).save(any());
    }

    @Test
    void uploadDoesNotTouchThePoolCache() {
        service.uploadSplash(mp4(), "Original", SplashKind.STANDARD, null, null);

        verifyNoInteractions(splashPoolCache);
    }

    @Test
    void wrongContentTypeIsRejectedAndNothingIsStored() {
        MockMultipartFile text = new MockMultipartFile("file", "clip.mp4", "text/plain", VIDEO_BYTES);

        assertThatThrownBy(() -> service.uploadSplash(text, "Original", SplashKind.STANDARD, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only video/mp4 files are accepted");

        verifyNoInteractions(storageService, repository);
    }

    @Test
    void missingFileIsRejected() {
        assertThatThrownBy(() -> service.uploadSplash(null, "Original", SplashKind.STANDARD, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("file is required");
    }

    @Test
    void emptyFileIsRejected() {
        MockMultipartFile empty = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[0]);

        assertThatThrownBy(() -> service.uploadSplash(empty, "Original", SplashKind.STANDARD, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("file is required");
    }

    @Test
    void eventWithoutBothTimestampsGetsTheTwoRequiredMessages() {
        assertThatThrownBy(() -> service.uploadSplash(mp4(), "Christmas", SplashKind.EVENT, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("startsAt is required when kind is EVENT; endsAt is required when kind is EVENT");
    }

    @Test
    void everyViolationIsReportedTogether() {
        assertThatThrownBy(() -> service.uploadSplash(null, " ", SplashKind.EVENT, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("title must not be blank; startsAt is required when kind is EVENT; "
                        + "endsAt is required when kind is EVENT; file is required");
    }

    @Test
    void unreadableFileBecomesABadRequest() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getContentType()).thenReturn("video/mp4");
        when(broken.getBytes()).thenThrow(new IOException("disk gone"));

        assertThatThrownBy(() -> service.uploadSplash(broken, "Original", SplashKind.STANDARD, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Failed to read uploaded file");

        verifyNoInteractions(storageService);
    }
}

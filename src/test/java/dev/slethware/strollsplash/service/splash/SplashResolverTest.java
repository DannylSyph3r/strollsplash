package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.enums.SplashKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SplashResolverTest {

    private static final Instant NOW = Instant.parse("2026-12-25T12:00:00Z");
    private static final Instant WINDOW_START = Instant.parse("2026-12-24T00:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-12-27T00:00:00Z");

    private final SplashResolver resolver = new SplashResolver();

    private SplashVideo standard(String title) {
        return SplashVideo.builder().title(title).kind(SplashKind.STANDARD).active(true).build();
    }

    private SplashVideo event(String title, Instant startsAt, Instant endsAt) {
        return SplashVideo.builder().title(title).kind(SplashKind.EVENT).active(true)
                .startsAt(startsAt).endsAt(endsAt).build();
    }

    private List<String> titles(List<SplashVideo> videos) {
        return videos.stream().map(SplashVideo::getTitle).toList();
    }

    @Test
    void liveEventBeatsStandard() {
        List<SplashVideo> rows = List.of(standard("a"), standard("b"), event("christmas", WINDOW_START, WINDOW_END));

        assertThat(titles(resolver.resolve(rows, NOW))).containsExactly("christmas");
    }

    @Test
    void eventIsLiveAtTheExactStartInstant() {
        List<SplashVideo> rows = List.of(standard("a"), event("christmas", WINDOW_START, WINDOW_END));

        assertThat(titles(resolver.resolve(rows, WINDOW_START))).containsExactly("christmas");
    }

    @Test
    void eventIsOverAtTheExactEndInstant() {
        List<SplashVideo> rows = List.of(standard("a"), event("christmas", WINDOW_START, WINDOW_END));

        assertThat(titles(resolver.resolve(rows, WINDOW_END))).containsExactly("a");
    }

    @Test
    void eventIsNotLiveOneInstantBeforeItStarts() {
        List<SplashVideo> rows = List.of(standard("a"), event("christmas", WINDOW_START, WINDOW_END));

        assertThat(titles(resolver.resolve(rows, WINDOW_START.minusNanos(1)))).containsExactly("a");
    }

    @Test
    void pinnedStandardNarrowsTheStandardTier() {
        SplashVideo pinned = standard("pinned");
        pinned.setPinned(true);

        assertThat(titles(resolver.resolve(List.of(standard("a"), pinned, standard("b")), NOW)))
                .containsExactly("pinned");
    }

    @Test
    void pinnedStandardDoesNotBeatLiveEvent() {
        SplashVideo pinned = standard("pinned");
        pinned.setPinned(true);
        List<SplashVideo> rows = List.of(pinned, standard("a"), event("christmas", WINDOW_START, WINDOW_END));

        assertThat(titles(resolver.resolve(rows, NOW))).containsExactly("christmas");
    }

    @Test
    void pinnedEventNarrowsAmongLiveEvents() {
        SplashVideo pinned = event("pinned-event", WINDOW_START, WINDOW_END);
        pinned.setPinned(true);
        List<SplashVideo> rows = List.of(event("other-event", WINDOW_START, WINDOW_END), pinned);

        assertThat(titles(resolver.resolve(rows, NOW))).containsExactly("pinned-event");
    }

    @Test
    void inactiveRowsAreExcludedEvenWhenPassedIn() {
        SplashVideo off = standard("off");
        off.setActive(false);
        SplashVideo offEvent = event("off-event", WINDOW_START, WINDOW_END);
        offEvent.setActive(false);

        assertThat(titles(resolver.resolve(List.of(off, offEvent, standard("a")), NOW))).containsExactly("a");
    }

    @Test
    void inactivePinnedRowDoesNotNarrowThePool() {
        SplashVideo offPinned = standard("off-pinned");
        offPinned.setActive(false);
        offPinned.setPinned(true);

        assertThat(titles(resolver.resolve(List.of(offPinned, standard("a"), standard("b")), NOW)))
                .containsExactly("a", "b");
    }

    @Test
    void eventMissingEitherTimestampIsSkipped() {
        List<SplashVideo> rows = List.of(
                standard("a"),
                event("no-start", null, WINDOW_END),
                event("no-end", WINDOW_START, null),
                event("no-window", null, null));

        assertThat(titles(resolver.resolve(rows, NOW))).containsExactly("a");
    }

    @Test
    void overlappingEventsAreBothReturned() {
        List<SplashVideo> rows = List.of(
                event("first", WINDOW_START, WINDOW_END),
                event("second", WINDOW_START.plusSeconds(3600), WINDOW_END),
                standard("a"));

        assertThat(titles(resolver.resolve(rows, NOW))).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    void pastEventIsIgnored() {
        List<SplashVideo> rows = List.of(standard("a"), event("halloween", WINDOW_START.minusSeconds(86400 * 60), WINDOW_START));

        assertThat(titles(resolver.resolve(rows, NOW))).containsExactly("a");
    }

    @Test
    void futureEventIsIgnored() {
        List<SplashVideo> rows = List.of(standard("a"), event("new-year", WINDOW_END, WINDOW_END.plusSeconds(86400)));

        assertThat(titles(resolver.resolve(rows, NOW))).containsExactly("a");
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertThat(resolver.resolve(List.of(), NOW)).isEmpty();
    }

    @Test
    void onlyInactiveRowsGiveAnEmptyPool() {
        SplashVideo off = standard("off");
        off.setActive(false);

        assertThat(resolver.resolve(List.of(off), NOW)).isEmpty();
    }
}

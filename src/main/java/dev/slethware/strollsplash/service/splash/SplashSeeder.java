package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.repository.SplashVideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SplashSeeder implements CommandLineRunner {

    private static final String SEED_FILE = "seed/splashes.json";

    private final SplashVideoRepository splashVideoRepository;
    private final SplashValidator splashValidator;
    private final JsonMapper jsonMapper;

    @Override
    public void run(String... args) throws IOException {
        if (splashVideoRepository.count() > 0) {
            return;
        }

        List<SplashVideo> rows;
        try (InputStream seedFile = new ClassPathResource(SEED_FILE).getInputStream()) {
            rows = jsonMapper.readValue(seedFile, new TypeReference<List<SplashVideo>>() {});
        }

        // Every row is checked before any is saved, so one bad row leaves the table empty
        rows.forEach(row -> splashValidator.validateOrThrow(
                row.getTitle(), row.getKind(), row.getStartsAt(), row.getEndsAt()));
        splashVideoRepository.saveAll(rows);

        log.info("Seeded {} splash videos", rows.size());
    }
}

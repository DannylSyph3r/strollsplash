package dev.slethware.strollsplash.service.splash;

import dev.slethware.strollsplash.entity.SplashVideo;
import dev.slethware.strollsplash.exception.BadRequestException;
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
        long existing = splashVideoRepository.count();
        if (existing > 0) {
            log.info("splash.seed.skipped rows={}", existing);
            return;
        }

        List<SplashVideo> rows;
        try (InputStream seedFile = new ClassPathResource(SEED_FILE).getInputStream()) {
            rows = jsonMapper.readValue(seedFile, new TypeReference<List<SplashVideo>>() {});
        }

        // Every row is checked before any is saved, so one bad row leaves the table empty
        for (SplashVideo row : rows) {
            try {
                splashValidator.validateOrThrow(row.getTitle(), row.getKind(), row.getStartsAt(), row.getEndsAt());
            } catch (BadRequestException e) {
                throw new BadRequestException("Invalid seed row '" + row.getTitle() + "': " + e.getMessage());
            }
        }
        splashVideoRepository.saveAll(rows);

        log.info("splash.seed.done rows={}", rows.size());
    }
}

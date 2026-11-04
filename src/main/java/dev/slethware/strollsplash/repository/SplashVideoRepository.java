package dev.slethware.strollsplash.repository;

import dev.slethware.strollsplash.entity.SplashVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SplashVideoRepository extends JpaRepository<SplashVideo, UUID> {

    List<SplashVideo> findAllByActiveTrue();
}

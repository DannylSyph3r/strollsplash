package dev.slethware.strollsplash.service.storage;

public interface StorageService {

    void upload(String key, String contentType, byte[] data);

    String buildPublicUrl(String key);
}

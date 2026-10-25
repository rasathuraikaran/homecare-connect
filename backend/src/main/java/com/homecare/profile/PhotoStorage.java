package com.homecare.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stores profile photos on local disk under generated names; the type is decided by file content, not the client's claim. */
@Component
public class PhotoStorage {

    static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png|webp)$");

    private final Path dir;

    public PhotoStorage(@Value("${app.uploads.dir}") String base) throws IOException {
        this.dir = Path.of(base).toAbsolutePath().normalize().resolve("photos");
        Files.createDirectories(dir);
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a photo to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo is too large (max 2 MB)");
        }
        try {
            byte[] bytes = file.getBytes();
            String ext = detectExtension(bytes);
            if (ext == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be a JPG, PNG or WebP image");
            }
            String name = UUID.randomUUID() + "." + ext;
            Files.write(dir.resolve(name), bytes);
            return name;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the photo");
        }
    }

    public void delete(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) return;
        try {
            Files.deleteIfExists(dir.resolve(name));
        } catch (IOException ignored) {
            // an orphaned file is harmless; do not fail the request
        }
    }

    public Resource load(String name) {
        if (!SAFE_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Path file = dir.resolve(name);
        if (!Files.isReadable(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new PathResource(file);
    }

    public static MediaType mediaType(String name) {
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".webp")) return MediaType.valueOf("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    private static String detectExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }
}

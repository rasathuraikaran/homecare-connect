package com.homecare.payment;

import com.homecare.verification.DocumentStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores payment receipts (a screenshot or a PDF) on local disk under generated names, in their own folder.
 * Nothing here is served from a public address: only an admin can read a file back, and it is attached to the admin's email.
 */
@Component
public class ReceiptStorage {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png|webp|pdf)$");

    private final Path dir;

    public ReceiptStorage(@Value("${app.uploads.dir}") String base) throws IOException {
        this.dir = Path.of(base).toAbsolutePath().normalize().resolve("receipts");
        Files.createDirectories(dir);
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose the receipt to send");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The receipt is too large (max 5 MB)");
        }
        try {
            byte[] bytes = file.getBytes();
            String ext = DocumentStorage.detectExtension(bytes);   // decided by the file's content, not by what the browser claims
            if (ext == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The receipt must be a JPG, PNG, WebP or PDF file");
            }
            String name = UUID.randomUUID() + "." + ext;
            Files.write(dir.resolve(name), bytes);
            return name;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the receipt");
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

    /** The file's bytes, or null when it is gone, for attaching to an email. */
    public byte[] read(String name) {
        if (name == null || !SAFE_NAME.matcher(name).matches()) return null;
        try {
            Path file = dir.resolve(name);
            return Files.isReadable(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    boolean exists(String name) {
        return SAFE_NAME.matcher(name).matches() && Files.exists(dir.resolve(name));
    }
}

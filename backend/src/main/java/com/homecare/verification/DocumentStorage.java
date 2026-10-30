package com.homecare.verification;

import com.homecare.profile.PhotoStorage;
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

/**
 * Stores identity and qualification documents on local disk under generated names, in their own folder.
 * Unlike photos, nothing here is ever served from a public address: only an admin can read a file back.
 */
@Component
public class DocumentStorage {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Pattern SAFE_NAME = Pattern.compile("^[a-f0-9-]{36}\\.(jpg|png|webp|pdf)$");

    private final Path dir;

    public DocumentStorage(@Value("${app.uploads.dir}") String base) throws IOException {
        this.dir = Path.of(base).toAbsolutePath().normalize().resolve("documents");
        Files.createDirectories(dir);
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Document is too large (max 5 MB)");
        }
        try {
            byte[] bytes = file.getBytes();
            String ext = detectExtension(bytes);   // decided by the file's content, not by what the browser claims
            if (ext == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document must be a JPG, PNG, WebP or PDF file");
            }
            String name = UUID.randomUUID() + "." + ext;
            Files.write(dir.resolve(name), bytes);
            return name;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the document");
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

    boolean exists(String name) {
        return SAFE_NAME.matcher(name).matches() && Files.exists(dir.resolve(name));
    }

    public static MediaType mediaType(String name) {
        if (name.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (name.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (name.endsWith(".webp")) return MediaType.valueOf("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    public static String detectExtension(byte[] b) {
        String image = PhotoStorage.detectExtension(b);
        if (image != null) return image;
        boolean pdf = b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
        return pdf ? "pdf" : null;
    }
}

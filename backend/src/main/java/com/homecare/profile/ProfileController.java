package com.homecare.profile;

import com.homecare.profile.ProfileDtos.*;
import com.homecare.user.User;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService service;
    private final PhotoStorage photos;

    public ProfileController(ProfileService service, PhotoStorage photos) {
        this.service = service;
        this.photos = photos;
    }

    @GetMapping("/caregivers/me/profile")
    public ProfileResponse get(@AuthenticationPrincipal User user) {
        return service.get(user);
    }

    @PutMapping("/caregivers/me/profile")
    public ProfileResponse save(@AuthenticationPrincipal User user, @Valid @RequestBody ProfileRequest req) {
        return service.save(user, req);
    }

    @PostMapping("/caregivers/me/photo")
    public ProfileResponse uploadPhoto(@AuthenticationPrincipal User user, @RequestParam("file") MultipartFile file) {
        return service.setPhoto(user, file);
    }

    @PutMapping("/caregivers/me/availability")
    public ProfileResponse availability(@AuthenticationPrincipal User user, @Valid @RequestBody AvailabilityRequest req) {
        return service.setAvailable(user, req.available());
    }

    @DeleteMapping("/caregivers/me/photo")
    public ProfileResponse removePhoto(@AuthenticationPrincipal User user) {
        return service.removePhoto(user);
    }

    /** Public: caregiver photos are shown to care seekers browsing profiles. File names are unguessable UUIDs. */
    @GetMapping("/files/photos/{name}")
    public ResponseEntity<Resource> photo(@PathVariable String name) {
        Resource file = photos.load(name);
        return ResponseEntity.ok()
                .contentType(PhotoStorage.mediaType(name))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(file);
    }
}

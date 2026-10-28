package com.homecare.verification;

import com.homecare.user.User;
import com.homecare.verification.VerificationDtos.VerificationView;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** A caregiver's own documents. The /api/caregivers/me/** rule in SecurityConfig already limits this to caregivers. */
@RestController
@RequestMapping("/api/caregivers/me")
public class VerificationController {

    private final VerificationService service;

    public VerificationController(VerificationService service) {
        this.service = service;
    }

    @GetMapping("/verification")
    public VerificationView view(@AuthenticationPrincipal User user) {
        return service.view(user);
    }

    @PostMapping("/documents/{type}")
    public VerificationView upload(@AuthenticationPrincipal User user, @PathVariable DocumentType type,
                                   @RequestParam("file") MultipartFile file) {
        return service.upload(user, type, file);
    }

    @DeleteMapping("/documents/{type}")
    public VerificationView remove(@AuthenticationPrincipal User user, @PathVariable DocumentType type) {
        return service.remove(user, type);
    }
}

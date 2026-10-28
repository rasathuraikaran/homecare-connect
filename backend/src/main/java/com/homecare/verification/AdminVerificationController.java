package com.homecare.verification;

import com.homecare.user.User;
import com.homecare.verification.VerificationDtos.AdminCaregiver;
import com.homecare.verification.VerificationDtos.ReviewRequest;
import com.homecare.verification.VerificationDtos.Summary;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The /api/admin/** rule in SecurityConfig already limits this to admins. */
@RestController
@RequestMapping("/api/admin")
public class AdminVerificationController {

    private final AdminVerificationService service;

    public AdminVerificationController(AdminVerificationService service) {
        this.service = service;
    }

    @GetMapping("/verifications")
    public List<AdminCaregiver> list(@RequestParam(defaultValue = "false") boolean needsReview) {
        return service.list(needsReview);
    }

    @GetMapping("/verifications/summary")
    public Summary summary() {
        return service.summary();
    }

    @PatchMapping("/documents/{id}")
    public AdminCaregiver review(@AuthenticationPrincipal User admin, @PathVariable Long id, @Valid @RequestBody ReviewRequest req) {
        return service.review(admin, id, req);
    }

    /** Never cached and never sniffed: these are identity documents. */
    @GetMapping("/documents/{id}/file")
    public ResponseEntity<Resource> file(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        AdminVerificationService.StoredFile stored = service.file(admin, id);
        return ResponseEntity.ok()
                .contentType(stored.type())
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "inline")
                .body(stored.resource());
    }
}

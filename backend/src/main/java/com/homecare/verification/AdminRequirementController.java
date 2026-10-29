package com.homecare.verification;

import com.homecare.profile.JobType;
import com.homecare.user.User;
import com.homecare.verification.VerificationDtos.Impact;
import com.homecare.verification.VerificationDtos.RequirementChange;
import com.homecare.verification.VerificationDtos.RequirementView;
import com.homecare.verification.VerificationDtos.SetRequirementRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AdminRequirementController {

    private final AdminRequirementService service;

    public AdminRequirementController(AdminRequirementService service) {
        this.service = service;
    }

    /** Any signed-in user may read what each job is asked for: the caregiver's profile form shows it. */
    @GetMapping("/api/document-requirements")
    public List<RequirementView> list() {
        return service.list();
    }

    /** The /api/admin/** rule in SecurityConfig limits the two below to admins. */
    @GetMapping("/api/admin/document-requirements/impact")
    public Impact impact(@RequestParam JobType job, @RequestParam DocumentType type, @RequestParam boolean required) {
        return service.impact(job, type, required);
    }

    @PutMapping("/api/admin/document-requirements")
    public RequirementChange set(@AuthenticationPrincipal User admin, @Valid @RequestBody SetRequirementRequest req) {
        return service.set(admin, req);
    }
}

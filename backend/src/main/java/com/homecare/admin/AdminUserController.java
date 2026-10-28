package com.homecare.admin;

import com.homecare.admin.AdminUserDtos.AdminUserRow;
import com.homecare.admin.AdminUserDtos.DeletionPreview;
import com.homecare.user.Role;
import com.homecare.user.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The /api/admin/** rule in SecurityConfig already limits this to admins. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserRow> list(@RequestParam(required = false) Role role, @RequestParam(defaultValue = "") String q) {
        return service.list(role, q);
    }

    @GetMapping("/{id}/deletion-preview")
    public DeletionPreview preview(@PathVariable Long id) {
        return service.preview(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        service.delete(admin, id);
        return ResponseEntity.noContent().build();
    }
}

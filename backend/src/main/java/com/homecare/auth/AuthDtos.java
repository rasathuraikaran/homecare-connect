package com.homecare.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.homecare.user.Role;
import com.homecare.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            Role role) {}

    /** role: used only when a new account must be created; expectedRole: must match an existing account. */
    public record GoogleLoginRequest(@NotBlank String idToken, Role role, Role expectedRole) {}

    public record UserResponse(Long id, String fullName, String email, String phone, Role role,
                               boolean profileCompleted, String photoUrl, boolean admin) {
        public static UserResponse from(User u, boolean profileCompleted, String photoUrl) {
            return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                    profileCompleted, photoUrl, u.isAdmin());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}

    /** message is always set; errors lists each problem separately when several fields failed validation. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String message, List<String> errors) {
        public ErrorResponse(String message) {
            this(message, null);
        }
    }
}

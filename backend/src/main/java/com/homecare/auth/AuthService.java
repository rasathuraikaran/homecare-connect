package com.homecare.auth;

import com.homecare.auth.AuthDtos.*;
import com.homecare.profile.CaregiverProfile;
import com.homecare.profile.CaregiverProfileRepository;
import com.homecare.user.Role;
import com.homecare.user.User;
import com.homecare.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final GoogleTokenVerifier google;
    private final CaregiverProfileRepository profiles;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, GoogleTokenVerifier google,
                       CaregiverProfileRepository profiles) {
        this.profiles = profiles;
        this.google = google;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = users.save(new User(email, encoder.encode(req.password()),
                req.fullName().trim(), req.phone(), req.role()));
        return new AuthResponse(jwt.generate(user), toResponse(user));
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> u.getPasswordHash() != null)
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                // If a role tab was chosen on the login page, it must match the account's role
                .filter(u -> req.role() == null || u.getRole() == req.role())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        return new AuthResponse(jwt.generate(user), toResponse(user));
    }

    public AuthResponse googleLogin(GoogleLoginRequest req) {
        GoogleTokenVerifier.Identity id = google.verify(req.idToken());
        String email = id.email().toLowerCase();

        User user = users.findByGoogleSub(id.subject())
                .or(() -> users.findByEmailIgnoreCase(email).map(existing -> {
                    existing.linkGoogle(id.subject());
                    return users.save(existing);
                }))
                .orElse(null);

        if (user == null) {
            if (req.role() == null) {
                throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, "Choose an account type to continue");
            }
            user = users.save(User.fromGoogle(email, id.name() != null ? id.name() : email, id.subject(), req.role()));
        } else if (req.expectedRole() != null && user.getRole() != req.expectedRole()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "This account is registered as a " + (user.getRole().name().equals("CAREGIVER") ? "caregiver" : "care seeker"));
        }
        return new AuthResponse(jwt.generate(user), toResponse(user));
    }

    /** Caregivers are "complete" once they have saved their profile; care seekers are always complete. */
    public UserResponse toResponse(User user) {
        if (user.getRole() != Role.CAREGIVER) {
            return UserResponse.from(user, true, null);
        }
        var profile = profiles.findById(user.getId());
        return UserResponse.from(user, profile.isPresent(), profile.map(CaregiverProfile::photoUrl).orElse(null));
    }
}

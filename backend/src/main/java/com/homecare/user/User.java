package com.homecare.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected User() {}

    public User(String email, String passwordHash, String fullName, String phone, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
    }

    public static User fromGoogle(String email, String fullName, String googleSub, Role role) {
        User u = new User(email, null, fullName, null, role);
        u.googleSub = googleSub;
        return u;
    }

    public void linkGoogle(String googleSub) { this.googleSub = googleSub; }

    public void setFullName(String fullName) { this.fullName = fullName; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getGoogleSub() { return googleSub; }
    public Role getRole() { return role; }
    public boolean isAdmin() { return admin; }
    public Instant getCreatedAt() { return createdAt; }
}

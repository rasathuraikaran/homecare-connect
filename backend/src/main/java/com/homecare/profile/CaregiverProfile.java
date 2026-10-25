package com.homecare.profile;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "caregiver_profiles")
public class CaregiverProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private Integer age;

    @Column(name = "experience_years")
    private Integer experienceYears;

    private String qualification;

    @Column(name = "photo_file")
    private String photoFile;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CaregiverProfile() {}

    public CaregiverProfile(Long userId) {
        this.userId = userId;
    }

    public void update(Integer age, Integer experienceYears, String qualification) {
        this.age = age;
        this.experienceYears = experienceYears;
        this.qualification = qualification;
        this.updatedAt = Instant.now();
    }

    public void setPhotoFile(String photoFile) {
        this.photoFile = photoFile;
        this.updatedAt = Instant.now();
    }

    public String photoUrl() {
        return photoFile == null ? null : "/api/files/photos/" + photoFile;
    }

    public Long getUserId() { return userId; }
    public Integer getAge() { return age; }
    public Integer getExperienceYears() { return experienceYears; }
    public String getQualification() { return qualification; }
    public String getPhotoFile() { return photoFile; }
}

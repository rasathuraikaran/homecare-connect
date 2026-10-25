package com.homecare.profile;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type")
    private JobType jobType;

    @ElementCollection
    @CollectionTable(name = "caregiver_rates", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "rate_type")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    @BatchSize(size = 50)
    private Map<RateType, BigDecimal> rates = new HashMap<>();

    @Column(name = "photo_file")
    private String photoFile;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected CaregiverProfile() {}

    public CaregiverProfile(Long userId) {
        this.userId = userId;
    }

    public void update(Integer age, Integer experienceYears, String qualification, JobType jobType,
                       Map<RateType, BigDecimal> newRates) {
        this.age = age;
        this.experienceYears = experienceYears;
        this.qualification = qualification;
        this.jobType = jobType;
        this.rates.clear();
        if (newRates != null) this.rates.putAll(newRates);
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
    /** Charges in a stable (declaration) order. Call inside a transaction. */
    public Map<RateType, BigDecimal> getRates() { return new TreeMap<>(rates); }
    public JobType getJobType() { return jobType; }
    public String getPhotoFile() { return photoFile; }
}

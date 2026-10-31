package com.homecare.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

@Embeddable
public class NursingServiceOffering {

    @Column(name = "service_code", nullable = false, length = 80)
    private String code;

    @Column(name = "service_name", nullable = false, length = 120)
    private String name;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "expected_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal expectedRate;

    @Column(name = "duration_minutes", length = 40)
    private String duration;

    @Column(name = "experience_level", length = 80)
    private String experienceLevel;

    @Column(name = "times_performed", length = 40)
    private String timesPerformed;

    @Column(name = "last_performed", length = 60)
    private String lastPerformed;

    @Column(name = "brings_equipment", nullable = false)
    private boolean bringsEquipment;

    @Column(name = "provides_consumables", nullable = false)
    private boolean providesConsumables;

    protected NursingServiceOffering() {}

    public NursingServiceOffering(String code, String name, String category, BigDecimal expectedRate,
                                  String duration, String experienceLevel, String timesPerformed,
                                  String lastPerformed, boolean bringsEquipment, boolean providesConsumables) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.expectedRate = expectedRate;
        this.duration = duration;
        this.experienceLevel = experienceLevel;
        this.timesPerformed = timesPerformed;
        this.lastPerformed = lastPerformed;
        this.bringsEquipment = bringsEquipment;
        this.providesConsumables = providesConsumables;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getExpectedRate() { return expectedRate; }
    public String getDuration() { return duration; }
    public String getExperienceLevel() { return experienceLevel; }
    public String getTimesPerformed() { return timesPerformed; }
    public String getLastPerformed() { return lastPerformed; }
    public boolean isBringsEquipment() { return bringsEquipment; }
    public boolean isProvidesConsumables() { return providesConsumables; }
}

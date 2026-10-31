package com.homecare.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CaretakerWorkEntry {

    @Column(name = "organisation", length = 150)
    private String org;

    @Column(name = "job_title", length = 120)
    private String title;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "reason_for_leaving", length = 150)
    private String reasonForLeaving;

    @Column(name = "responsibilities", length = 255)
    private String responsibilities;

    protected CaretakerWorkEntry() {}

    public CaretakerWorkEntry(String org, String title, String duration, String reasonForLeaving, String responsibilities) {
        this.org = org;
        this.title = title;
        this.duration = duration;
        this.reasonForLeaving = reasonForLeaving;
        this.responsibilities = responsibilities;
    }

    public String getOrg() { return org; }
    public String getTitle() { return title; }
    public String getDuration() { return duration; }
    public String getReasonForLeaving() { return reasonForLeaving; }
    public String getResponsibilities() { return responsibilities; }
}

package com.homecare.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class NursingWorkEntry {

    @Column(name = "organisation", length = 150)
    private String org;

    @Column(name = "job_title", length = 120)
    private String title;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "responsibilities", length = 255)
    private String responsibilities;

    protected NursingWorkEntry() {}

    public NursingWorkEntry(String org, String title, String department, String duration, String responsibilities) {
        this.org = org;
        this.title = title;
        this.department = department;
        this.duration = duration;
        this.responsibilities = responsibilities;
    }

    public String getOrg() { return org; }
    public String getTitle() { return title; }
    public String getDepartment() { return department; }
    public String getDuration() { return duration; }
    public String getResponsibilities() { return responsibilities; }
}

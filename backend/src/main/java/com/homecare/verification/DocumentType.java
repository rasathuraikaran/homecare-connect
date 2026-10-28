package com.homecare.verification;

import com.homecare.profile.JobType;

import java.util.ArrayList;
import java.util.List;

/** The documents a caregiver can upload. What each job needs is decided here and nowhere else. */
public enum DocumentType {
    NIC("National Identity Card (NIC)",
            "A clear photo or scan of the front, showing your photo and NIC number. A PDF with both sides is fine."),
    DEGREE_CERTIFICATE("Degree certificate", "A photo or scan of your physiotherapy degree certificate."),
    NURSING_CERTIFICATE("Nursing qualification certificate", "A photo or scan of your nursing diploma or degree certificate."),
    GS_LETTER("Grama Niladhari (GS) letter", "A photo or scan of the letter from your Grama Niladhari officer.");

    private final String label;
    private final String hint;

    DocumentType(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String label() { return label; }
    public String hint() { return hint; }

    /** Without these approved, the caregiver is not shown to families. */
    public static List<DocumentType> requiredFor(JobType job) {
        if (job == null) return List.of();
        return switch (job) {
            case CARETAKER -> List.of(NIC);
            case NURSING -> List.of(NURSING_CERTIFICATE);
            case PHYSIOTHERAPIST -> List.of(DEGREE_CERTIFICATE);
        };
    }

    /** The admin reviews these too, but they never decide whether the caregiver is shown. */
    public static List<DocumentType> optionalFor(JobType job) {
        if (job == null) return List.of();
        return switch (job) {
            case CARETAKER -> List.of(GS_LETTER);
            case NURSING, PHYSIOTHERAPIST -> List.of(NIC);
        };
    }

    public static List<DocumentType> applicableTo(JobType job) {
        List<DocumentType> all = new ArrayList<>(requiredFor(job));
        all.addAll(optionalFor(job));
        return all;
    }
}

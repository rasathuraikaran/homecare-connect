package com.homecare.verification;

import com.homecare.profile.JobType;

import java.util.List;

/**
 * The documents a caregiver can upload, and which of them each job can take. Whether a document is mandatory is not decided
 * here: the admin sets that per job (see {@link DocumentRules}), and until then every document is optional.
 */
public enum DocumentType {
    NIC("National Identity Card (NIC)",
            "A clear photo or scan of the front, showing your photo and NIC number. A PDF with both sides is fine.",
            "NIC verified"),
    DEGREE_CERTIFICATE("Degree certificate", "A photo or scan of your physiotherapy degree certificate.", "Degree verified"),
    NURSING_CERTIFICATE("Nursing qualification certificate", "A photo or scan of your nursing diploma or degree certificate.",
            "Nursing certificate verified"),
    GS_LETTER("Grama Niladhari (GS) letter", "A photo or scan of the letter from your Grama Niladhari officer.", "GS letter verified");

    private final String label;
    private final String hint;
    private final String badge;

    DocumentType(String label, String hint, String badge) {
        this.label = label;
        this.hint = hint;
        this.badge = badge;
    }

    public String label() { return label; }
    public String hint() { return hint; }
    /** What families read on a caregiver's card once the admin approved this document. */
    public String badge() { return badge; }

    /** The documents a caregiver with this job can upload, most important first. */
    public static List<DocumentType> applicableTo(JobType job) {
        if (job == null) return List.of();
        return switch (job) {
            case CARETAKER -> List.of(NIC, GS_LETTER);
            case NURSING -> List.of(NURSING_CERTIFICATE, NIC);
            case PHYSIOTHERAPIST -> List.of(DEGREE_CERTIFICATE, NIC);
        };
    }
}

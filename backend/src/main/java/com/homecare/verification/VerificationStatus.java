package com.homecare.verification;

import com.homecare.profile.JobType;

import java.util.Collection;
import java.util.Map;

/** Where a caregiver stands. Only the documents the admin made mandatory for the job count; optional ones never change it. */
public enum VerificationStatus {
    NOT_REQUIRED,     // nothing is mandatory for this job: shown to families, documents are a bonus
    INCOMPLETE,       // a mandatory document is missing
    IN_REVIEW,        // everything mandatory is uploaded, the admin has not finished
    ACTION_NEEDED,    // a mandatory document was rejected
    VERIFIED;         // every mandatory document is approved

    public static VerificationStatus of(JobType job, Map<DocumentType, CaregiverDocument> uploaded, Collection<DocumentType> required) {
        if (job == null) return INCOMPLETE;   // no job yet: not shown anyway
        if (required.isEmpty()) return NOT_REQUIRED;
        boolean rejected = false, missing = false, pending = false;
        for (DocumentType type : required) {
            CaregiverDocument doc = uploaded.get(type);
            if (doc == null) missing = true;
            else if (doc.getStatus() == DocumentStatus.REJECTED) rejected = true;
            else if (doc.getStatus() == DocumentStatus.PENDING) pending = true;
        }
        if (rejected) return ACTION_NEEDED;
        if (missing) return INCOMPLETE;
        return pending ? IN_REVIEW : VERIFIED;
    }

    /** True when families may see the caregiver. */
    public boolean listed() {
        return this == NOT_REQUIRED || this == VERIFIED;
    }
}

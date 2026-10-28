package com.homecare.verification;

import com.homecare.profile.JobType;

import java.util.Map;

/** Where a caregiver stands. Only the documents their job requires count; optional ones never change it. */
public enum VerificationStatus {
    INCOMPLETE,       // a required document is missing
    IN_REVIEW,        // everything required is uploaded, the admin has not finished
    ACTION_NEEDED,    // a required document was rejected
    VERIFIED;         // every required document is approved

    public static VerificationStatus of(JobType job, Map<DocumentType, CaregiverDocument> uploaded) {
        if (job == null) return INCOMPLETE;
        boolean rejected = false, missing = false, pending = false;
        for (DocumentType type : DocumentType.requiredFor(job)) {
            CaregiverDocument doc = uploaded.get(type);
            if (doc == null) missing = true;
            else if (doc.getStatus() == DocumentStatus.REJECTED) rejected = true;
            else if (doc.getStatus() == DocumentStatus.PENDING) pending = true;
        }
        if (rejected) return ACTION_NEEDED;
        if (missing) return INCOMPLETE;
        return pending ? IN_REVIEW : VERIFIED;
    }
}

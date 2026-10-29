package com.homecare.verification;

import com.homecare.profile.CaregiverProfile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The "NIC verified" style marks families see on a caregiver's card: one for each document an admin approved. */
@Component
public class VerificationBadges {

    private final CaregiverDocumentRepository documents;

    public VerificationBadges(CaregiverDocumentRepository documents) {
        this.documents = documents;
    }

    /** Badge texts by caregiver id, in a steady order. A caregiver with none is left out. One query for all of them. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> forCaregivers(Collection<CaregiverProfile> caregivers) {
        if (caregivers.isEmpty()) return Map.of();
        Map<Long, CaregiverProfile> byId = new HashMap<>();
        caregivers.forEach(p -> byId.put(p.getUserId(), p));

        Map<Long, Set<DocumentType>> approved = new HashMap<>();
        for (CaregiverDocument d : documents.findByUserIdInAndStatus(byId.keySet(), DocumentStatus.APPROVED)) {
            CaregiverProfile p = byId.get(d.getUserId());
            if (p != null && DocumentType.applicableTo(p.getJobType()).contains(d.getDocType())) {   // not a document of a job they left
                approved.computeIfAbsent(d.getUserId(), k -> EnumSet.noneOf(DocumentType.class)).add(d.getDocType());
            }
        }
        Map<Long, List<String>> result = new HashMap<>();
        approved.forEach((id, types) -> result.put(id, types.stream().map(DocumentType::badge).toList()));
        return result;
    }
}

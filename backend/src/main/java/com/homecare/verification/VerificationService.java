package com.homecare.verification;

import com.homecare.profile.CaregiverProfile;
import com.homecare.profile.CaregiverProfileRepository;
import com.homecare.profile.JobType;
import com.homecare.profile.ProfileJobChanged;
import com.homecare.user.User;
import com.homecare.verification.VerificationDtos.DocumentView;
import com.homecare.verification.VerificationDtos.Impact;
import com.homecare.verification.VerificationDtos.VerificationView;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The caregiver's side: see what is asked, upload or remove a document. Keeps caregiver_profiles.listed up to date. */
@Service
public class VerificationService {

    /** How many caregivers a change hid from, or showed to, families. */
    public record Change(int hidden, int shown) {}

    private final CaregiverProfileRepository profiles;
    private final CaregiverDocumentRepository documents;
    private final DocumentStorage storage;
    private final DocumentRules rules;

    public VerificationService(CaregiverProfileRepository profiles, CaregiverDocumentRepository documents, DocumentStorage storage,
                               DocumentRules rules) {
        this.profiles = profiles;
        this.documents = documents;
        this.storage = storage;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public VerificationView view(User user) {
        JobType job = profiles.findById(user.getId()).map(CaregiverProfile::getJobType).orElse(null);
        return toView(job, byType(documents.findByUserId(user.getId())));
    }

    @Transactional
    public VerificationView upload(User user, DocumentType type, MultipartFile file) {
        CaregiverProfile profile = profileWithJob(user);
        if (!DocumentType.applicableTo(profile.getJobType()).contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You do not need to upload that document for your job");
        }
        String name = storage.save(file);
        CaregiverDocument existing = documents.findByUserIdAndDocType(user.getId(), type).orElse(null);
        if (existing == null) {
            documents.save(new CaregiverDocument(user.getId(), type, name));
        } else {
            deleteAfterCommit(existing.getFileName());
            existing.replaceFile(name);
        }
        return refresh(profile);
    }

    @Transactional
    public VerificationView remove(User user, DocumentType type) {
        CaregiverProfile profile = profileWithJob(user);
        documents.findByUserIdAndDocType(user.getId(), type).ifPresent(doc -> {
            deleteAfterCommit(doc.getFileName());
            documents.delete(doc);
        });
        return refresh(profile);
    }

    /** What the job asks for changed, so the caregiver may have become listed or unlisted. Runs in the profile save's transaction. */
    @EventListener
    public void onJobChanged(ProfileJobChanged event) {
        profiles.findById(event.userId()).ifPresent(this::refresh);
    }

    /** Recomputes the stored flag from the documents. Call inside a transaction. */
    VerificationView refresh(CaregiverProfile profile) {
        VerificationView view = toView(profile.getJobType(), byType(documents.findByUserId(profile.getUserId())));
        profile.setListed(view.status().listed());
        return view;
    }

    /** The admin changed what is mandatory for a job: bring every caregiver of that job up to date. */
    @Transactional
    public Change refreshJob(JobType job) {
        List<DocumentType> required = rules.required(job);
        int hidden = 0, shown = 0;
        List<CaregiverProfile> caregivers = profiles.findByJobType(job);
        Map<Long, Map<DocumentType, CaregiverDocument>> uploaded = uploadedBy(caregivers);
        for (CaregiverProfile p : caregivers) {
            boolean before = p.isListed();
            boolean after = VerificationStatus.of(job, uploaded.getOrDefault(p.getUserId(), Map.of()), required).listed();
            p.setListed(after);
            if (before && !after) hidden++;
            if (!before && after) shown++;
        }
        return new Change(hidden, shown);
    }

    /** What would change if a document became mandatory (or optional) for a job. Changes nothing. */
    @Transactional(readOnly = true)
    public Impact impact(JobType job, DocumentType type, boolean makeRequired) {
        Set<DocumentType> required = new LinkedHashSet<>(rules.required(job));
        if (makeRequired) required.add(type); else required.remove(type);
        int willHide = 0, willShow = 0;
        List<CaregiverProfile> caregivers = profiles.findByJobType(job);
        Map<Long, Map<DocumentType, CaregiverDocument>> uploaded = uploadedBy(caregivers);
        for (CaregiverProfile p : caregivers) {
            boolean after = VerificationStatus.of(job, uploaded.getOrDefault(p.getUserId(), Map.of()), required).listed();
            if (p.isListed() && !after) willHide++;
            if (!p.isListed() && after) willShow++;
        }
        return new Impact(willHide, willShow);
    }

    private Map<Long, Map<DocumentType, CaregiverDocument>> uploadedBy(List<CaregiverProfile> caregivers) {
        Map<Long, Map<DocumentType, CaregiverDocument>> result = new HashMap<>();
        if (caregivers.isEmpty()) return result;
        for (CaregiverDocument d : documents.findByUserIdIn(caregivers.stream().map(CaregiverProfile::getUserId).toList())) {
            result.computeIfAbsent(d.getUserId(), k -> new EnumMap<>(DocumentType.class)).put(d.getDocType(), d);
        }
        return result;
    }

    private CaregiverProfile profileWithJob(User user) {
        return profiles.findById(user.getId())
                .filter(p -> p.getJobType() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Save your profile and choose your job first"));
    }

    /** Mandatory documents first, then the optional ones. */
    VerificationView toView(JobType job, Map<DocumentType, CaregiverDocument> uploaded) {
        List<DocumentType> required = rules.required(job);
        List<DocumentView> rows = new ArrayList<>();
        for (DocumentType type : required) rows.add(row(type, true, uploaded.get(type)));
        for (DocumentType type : DocumentType.applicableTo(job)) {
            if (!required.contains(type)) rows.add(row(type, false, uploaded.get(type)));
        }
        return new VerificationView(VerificationStatus.of(job, uploaded, required), job, rows);
    }

    private static DocumentView row(DocumentType type, boolean required, CaregiverDocument doc) {
        return new DocumentView(type, type.label(), type.hint(), required,
                doc == null ? null : doc.getStatus(), doc == null ? null : doc.getRejectReason(), doc == null ? null : doc.getUploadedAt());
    }

    static Map<DocumentType, CaregiverDocument> byType(List<CaregiverDocument> list) {
        Map<DocumentType, CaregiverDocument> map = new EnumMap<>(DocumentType.class);
        for (CaregiverDocument doc : list) map.put(doc.getDocType(), doc);
        return map;
    }

    /** Delete the old file only once the database change is safely saved. */
    private void deleteAfterCommit(String fileName) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    storage.delete(fileName);
                }
            });
        } else {
            storage.delete(fileName);
        }
    }
}

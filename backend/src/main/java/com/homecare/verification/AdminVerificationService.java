package com.homecare.verification;

import com.homecare.profile.CaregiverProfile;
import com.homecare.profile.CaregiverProfileRepository;
import com.homecare.profile.JobType;
import com.homecare.user.User;
import com.homecare.user.UserRepository;
import com.homecare.verification.VerificationDtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The admin's side: see who uploaded what, open a file, approve or reject it. */
@Service
public class AdminVerificationService {

    private static final Logger log = LoggerFactory.getLogger(AdminVerificationService.class);
    private static final int LIST_LIMIT = 200;

    public record StoredFile(Resource resource, MediaType type) {}

    private final CaregiverDocumentRepository documents;
    private final CaregiverProfileRepository profiles;
    private final UserRepository users;
    private final VerificationService verification;
    private final DocumentStorage storage;

    public AdminVerificationService(CaregiverDocumentRepository documents, CaregiverProfileRepository profiles,
                                    UserRepository users, VerificationService verification, DocumentStorage storage) {
        this.documents = documents;
        this.profiles = profiles;
        this.users = users;
        this.verification = verification;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<AdminCaregiver> list(boolean needsReviewOnly) {
        PageRequest page = PageRequest.of(0, LIST_LIMIT);
        List<Long> ids = needsReviewOnly
                ? documents.userIdsWithStatus(DocumentStatus.PENDING, page)
                : documents.userIdsWithDocuments(page);
        List<AdminCaregiver> result = new ArrayList<>(build(ids));
        if (needsReviewOnly) {
            result.removeIf(c -> c.documents().stream().noneMatch(d -> d.status() == DocumentStatus.PENDING));
            result.sort(Comparator.comparing(AdminVerificationService::oldestPending));   // longest waiting first
        } else {
            result.sort(Comparator.comparing(c -> c.name().toLowerCase()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return new Summary(list(true).size());
    }

    @Transactional
    public AdminCaregiver review(User admin, Long documentId, ReviewRequest req) {
        CaregiverDocument doc = documents.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (doc.getUserId().equals(admin.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot review your own documents");
        }
        if (doc.getRevision() != req.revision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The caregiver uploaded a new file. Please open it and check it again.");
        }
        switch (req.status()) {
            case APPROVED -> doc.approve(admin.getId());
            case REJECTED -> {
                String reason = req.reason() == null ? "" : req.reason().trim();
                if (reason.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Say why the document is rejected, so the caregiver knows what to fix");
                }
                doc.reject(admin.getId(), reason);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose approve or reject");
        }
        CaregiverProfile profile = profiles.findById(doc.getUserId()).orElseThrow();
        verification.refresh(profile);
        log.info("Admin {} {} document {}", admin.getId(), doc.getStatus().name().toLowerCase(), doc.getId());
        return build(List.of(doc.getUserId())).get(0);
    }

    @Transactional(readOnly = true)
    public StoredFile file(User admin, Long documentId) {
        CaregiverDocument doc = documents.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        Resource resource = storage.load(doc.getFileName());
        log.info("Admin {} opened document {}", admin.getId(), doc.getId());   // who looked at what, never the content
        return new StoredFile(resource, DocumentStorage.mediaType(doc.getFileName()));
    }

    private List<AdminCaregiver> build(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        Map<Long, User> people = new HashMap<>();
        users.findAllById(ids).forEach(u -> people.put(u.getId(), u));
        Map<Long, CaregiverProfile> profileById = new HashMap<>();
        profiles.findAllById(ids).forEach(p -> profileById.put(p.getUserId(), p));
        Map<Long, Map<DocumentType, CaregiverDocument>> uploaded = new HashMap<>();
        for (CaregiverDocument d : documents.findByUserIdIn(ids)) {
            uploaded.computeIfAbsent(d.getUserId(), k -> new EnumMap<>(DocumentType.class)).put(d.getDocType(), d);
        }

        List<AdminCaregiver> result = new ArrayList<>();
        for (Long id : ids) {
            User person = people.get(id);
            if (person == null) continue;
            CaregiverProfile profile = profileById.get(id);
            JobType job = profile == null ? null : profile.getJobType();
            Map<DocumentType, CaregiverDocument> mine = uploaded.getOrDefault(id, Map.of());
            List<AdminDocument> rows = new ArrayList<>();
            for (DocumentType type : DocumentType.requiredFor(job)) rows.add(row(type, true, mine.get(type)));
            for (DocumentType type : DocumentType.optionalFor(job)) rows.add(row(type, false, mine.get(type)));
            result.add(new AdminCaregiver(id, person.getFullName(), person.getEmail(), job, VerificationStatus.of(job, mine), rows));
        }
        return result;
    }

    private static AdminDocument row(DocumentType type, boolean required, CaregiverDocument d) {
        return new AdminDocument(d == null ? null : d.getId(), type, type.label(), required,
                d == null ? null : d.getStatus(), d == null ? 0 : d.getRevision(),
                d == null ? null : d.getRejectReason(), d == null ? null : d.getUploadedAt());
    }

    private static Instant oldestPending(AdminCaregiver c) {
        return c.documents().stream().filter(d -> d.status() == DocumentStatus.PENDING)
                .map(AdminDocument::uploadedAt).min(Comparator.naturalOrder()).orElse(Instant.MAX);
    }
}

package com.homecare.verification;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "caregiver_documents")
public class CaregiverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false)
    private DocumentType docType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(nullable = false)
    private int revision = 1;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();

    protected CaregiverDocument() {}

    public CaregiverDocument(Long userId, DocumentType docType, String fileName) {
        this.userId = userId;
        this.docType = docType;
        this.fileName = fileName;
    }

    /** A new file starts again as pending, and the old review no longer counts. */
    public void replaceFile(String newFileName) {
        this.fileName = newFileName;
        this.status = DocumentStatus.PENDING;
        this.revision++;
        this.rejectReason = null;
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.uploadedAt = Instant.now();
    }

    public void approve(Long adminId) {
        this.status = DocumentStatus.APPROVED;
        this.rejectReason = null;
        stamp(adminId);
    }

    public void reject(Long adminId, String reason) {
        this.status = DocumentStatus.REJECTED;
        this.rejectReason = reason;
        stamp(adminId);
    }

    private void stamp(Long adminId) {
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public DocumentType getDocType() { return docType; }
    public String getFileName() { return fileName; }
    public DocumentStatus getStatus() { return status; }
    public int getRevision() { return revision; }
    public String getRejectReason() { return rejectReason; }
    public Instant getUploadedAt() { return uploadedAt; }
}

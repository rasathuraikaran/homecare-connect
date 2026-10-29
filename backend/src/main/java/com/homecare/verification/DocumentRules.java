package com.homecare.verification;

import com.homecare.profile.JobType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Which documents the admin made mandatory for which job. Nothing is mandatory until the admin says so. */
@Component
public class DocumentRules {

    private final JdbcTemplate jdbc;

    public DocumentRules(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The mandatory documents of a job, in the order a caregiver is shown them. */
    public List<DocumentType> required(JobType job) {
        Set<DocumentType> marked = markedRequired(job);
        return DocumentType.applicableTo(job).stream().filter(marked::contains).toList();
    }

    public boolean isRequired(JobType job, DocumentType type) {
        return markedRequired(job).contains(type);
    }

    /** One entry for every document each job can take, with whether it is mandatory now. */
    public List<Rule> all() {
        List<Rule> rules = new ArrayList<>();
        for (JobType job : JobType.values()) {
            Set<DocumentType> marked = markedRequired(job);
            for (DocumentType type : DocumentType.applicableTo(job)) rules.add(new Rule(job, type, marked.contains(type)));
        }
        return rules;
    }

    /** Saves the setting. Does not touch anyone's listing: the caller refreshes the caregivers of that job. */
    public void set(JobType job, DocumentType type, boolean required, Long adminId) {
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.update("update document_requirements set required = ?, updated_at = ?, updated_by = ? where job_type = ? and doc_type = ?",
                required, now, adminId, job.name(), type.name());
        if (updated == 0) {
            jdbc.update("insert into document_requirements (job_type, doc_type, required, updated_at, updated_by) values (?, ?, ?, ?, ?)",
                    job.name(), type.name(), required, now, adminId);
        }
    }

    public record Rule(JobType job, DocumentType type, boolean required) {}

    private Set<DocumentType> markedRequired(JobType job) {
        Set<DocumentType> marked = EnumSet.noneOf(DocumentType.class);
        if (job == null) return marked;
        for (String name : jdbc.queryForList("select doc_type from document_requirements where job_type = ? and required = true", String.class, job.name())) {
            try {
                marked.add(DocumentType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // a document type that no longer exists in the code
            }
        }
        return marked;
    }
}

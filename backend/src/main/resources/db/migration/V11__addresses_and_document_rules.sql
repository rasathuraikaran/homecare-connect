-- Where a caregiver lives, and where the care of each request is needed. Only the admin sees them.
-- Rows saved before the question was asked have none, so the columns can be empty.
ALTER TABLE caregiver_profiles ADD COLUMN address VARCHAR(300);
ALTER TABLE bookings ADD COLUMN address VARCHAR(300);

-- "verified" meant "has the documents the job requires". Documents are optional unless an admin makes one mandatory,
-- so the flag now says whether families may see the caregiver, and is true when nothing is mandatory.
ALTER TABLE caregiver_profiles RENAME COLUMN verified TO listed;
UPDATE caregiver_profiles SET listed = TRUE WHERE job_type IS NOT NULL;

-- Which document an admin made mandatory for which job. No row, or required = false, means optional.
CREATE TABLE document_requirements (
    job_type   VARCHAR(30) NOT NULL,
    doc_type   VARCHAR(30) NOT NULL,
    required   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    PRIMARY KEY (job_type, doc_type)
);

-- Role-specific onboarding details for CARETAKER only.
-- Nursing and Physiotherapist caregivers use caregiver_profiles and may have their own role tables later.
CREATE TABLE caretaker_onboarding (
    user_id                    BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    dob                        VARCHAR(20),
    gender                     VARCHAR(30),
    preferred_language         VARCHAR(40),
    spoken_languages           VARCHAR(255),
    emergency_contact_name     VARCHAR(150),
    emergency_contact_relation VARCHAR(80),
    emergency_contact_phone    VARCHAR(30),
    caretaker_category         VARCHAR(60),
    is_new_caregiver           BOOLEAN NOT NULL DEFAULT FALSE,
    reference_name             VARCHAR(150),
    reference_phone            VARCHAR(30),
    education_level            VARCHAR(80),
    first_aid_certified        VARCHAR(60),
    available_days             VARCHAR(100),
    preferred_shift            VARCHAR(80),
    work_type                  VARCHAR(40),
    living_arrangement         VARCHAR(40),
    preferred_locations        VARCHAR(200),
    start_when                 VARCHAR(60),
    call_whatsapp_phone        VARCHAR(30),
    call_method                VARCHAR(60),
    call_available_times       VARCHAR(300),
    call_notes                 VARCHAR(200),
    consent_accurate_info      BOOLEAN NOT NULL DEFAULT FALSE,
    consent_background_check   BOOLEAN NOT NULL DEFAULT FALSE,
    consent_code_of_conduct    BOOLEAN NOT NULL DEFAULT FALSE,
    consent_terms_privacy      BOOLEAN NOT NULL DEFAULT FALSE,
    consented                  BOOLEAN NOT NULL DEFAULT FALSE,
    background_step_saved      BOOLEAN NOT NULL DEFAULT FALSE,
    skills_step_saved          BOOLEAN NOT NULL DEFAULT FALSE,
    experience_step_saved      BOOLEAN NOT NULL DEFAULT FALSE,
    identity_step_saved        BOOLEAN NOT NULL DEFAULT FALSE,
    certificates_reviewed      BOOLEAN NOT NULL DEFAULT FALSE,
    availability_step_saved    BOOLEAN NOT NULL DEFAULT FALSE,
    interview_scheduled        BOOLEAN NOT NULL DEFAULT FALSE,
    application_submitted      BOOLEAN NOT NULL DEFAULT FALSE,
    last_step                  VARCHAR(40),
    updated_at                 TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE caretaker_skill_sources (
    user_id    BIGINT       NOT NULL REFERENCES caretaker_onboarding (user_id) ON DELETE CASCADE,
    skill_name VARCHAR(100) NOT NULL,
    source     VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, skill_name)
);

CREATE TABLE caretaker_work_history (
    user_id            BIGINT  NOT NULL REFERENCES caretaker_onboarding (user_id) ON DELETE CASCADE,
    entry_order        INTEGER NOT NULL,
    organisation       VARCHAR(150),
    job_title          VARCHAR(120),
    duration           VARCHAR(100),
    reason_for_leaving VARCHAR(150),
    responsibilities   VARCHAR(255),
    PRIMARY KEY (user_id, entry_order)
);

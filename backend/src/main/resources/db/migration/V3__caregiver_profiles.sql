CREATE TABLE caregiver_profiles (
    user_id          BIGINT PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    age              INTEGER CHECK (age BETWEEN 18 AND 100),
    experience_years INTEGER CHECK (experience_years BETWEEN 0 AND 60),
    qualification    VARCHAR(255),
    photo_file       VARCHAR(80),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE caregiver_rates (
    user_id   BIGINT        NOT NULL REFERENCES caregiver_profiles (user_id) ON DELETE CASCADE,
    rate_type VARCHAR(20)   NOT NULL,
    amount    NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    PRIMARY KEY (user_id, rate_type)
);

-- The admin chooses how seekers may pay: online through PayHere, by bank transfer, both, or neither (then completing a visit asks for no fee).
CREATE TABLE payment_settings (
    id              BIGINT PRIMARY KEY CHECK (id = 1),   -- one row only
    payhere_enabled BOOLEAN NOT NULL,
    bank_enabled    BOOLEAN NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by      BIGINT REFERENCES users (id) ON DELETE SET NULL
);
-- PayHere stays on, as it was before this table existed (it still needs PAYHERE_MODE and the merchant keys); bank transfer starts off
INSERT INTO payment_settings (id, payhere_enabled, bank_enabled, updated_at) VALUES (1, TRUE, FALSE, CURRENT_TIMESTAMP);

-- The part of the amount that is the caregiver's own charge. The seeker pays it together with the platform fee (bookings.fee_amount).
-- Empty means the seeker pays the fee only.
ALTER TABLE bookings ADD COLUMN caregiver_amount NUMERIC(10,2);

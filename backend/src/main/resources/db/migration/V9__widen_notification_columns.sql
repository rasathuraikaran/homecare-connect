-- Email alerts need more room than WhatsApp ones: a list of addresses, and a full note and email address.
ALTER TABLE notifications ALTER COLUMN recipient SET DATA TYPE VARCHAR(500);
ALTER TABLE notifications ALTER COLUMN params SET DATA TYPE VARCHAR(4000);

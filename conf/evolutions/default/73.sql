# --- !Ups

BEGIN;

ALTER TABLE file ADD COLUMN contents_size BIGINT;
ALTER TABLE file ADD COLUMN view_size BIGINT;

COMMIT;

# --- !Downs

BEGIN;

ALTER TABLE file DROP COLUMN view_size;
ALTER TABLE file DROP COLUMN contents_size;

COMMIT;



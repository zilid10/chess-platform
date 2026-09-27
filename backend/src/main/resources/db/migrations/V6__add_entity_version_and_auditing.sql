ALTER TABLE users ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE match_records ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE match_records ALTER COLUMN start_time SET NOT NULL;
ALTER TABLE match_records ALTER COLUMN end_time SET NOT NULL;

ALTER TABLE friend_request RENAME COLUMN requested_at TO created_at;
UPDATE friend_request SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE friend_request ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE friend_request ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE friend_request ADD COLUMN version bigint NOT NULL DEFAULT 0;

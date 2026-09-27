-- Rating snapshot for each archived match: each player's rating after the game and the change it caused.
-- Matches archived before ratings existed keep NULLs.
ALTER TABLE match_records ADD COLUMN time_control text;
ALTER TABLE match_records ADD COLUMN white_rating int;
ALTER TABLE match_records ADD COLUMN black_rating int;
ALTER TABLE match_records ADD COLUMN white_rating_change int;
ALTER TABLE match_records ADD COLUMN black_rating_change int;

ALTER TABLE match_records ADD CONSTRAINT chk_match_records_time_control
    CHECK (time_control IN ('BULLET', 'BLITZ', 'RAPID', 'CLASSICAL', 'CORRESPONDENCE'));

-- Users registered before V5 have no rating rows yet.
INSERT INTO player_ratings (user_id, time_control)
SELECT u.id, tc.time_control
FROM users u
         CROSS JOIN (VALUES ('BULLET'), ('BLITZ'), ('RAPID'), ('CLASSICAL'), ('CORRESPONDENCE')) AS tc (time_control)
ON CONFLICT (user_id, time_control) DO NOTHING;

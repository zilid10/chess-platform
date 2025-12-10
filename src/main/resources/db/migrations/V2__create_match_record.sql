CREATE TABLE match_records (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY, -- UUID
    white_user_id BIGINT NOT NULL REFERENCES users(id),
    black_user_id BIGINT NOT NULL REFERENCES users(id),
    result VARCHAR(20), -- "1-0", "0-1", "1/2-1/2"
    reason VARCHAR(50), -- "Checkmate", "Resignation"
    pgn TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP
);

CREATE INDEX idx_match_records_white_user_id ON match_records(white_user_id);
CREATE INDEX idx_match_records_black_user_id ON match_records(black_user_id);

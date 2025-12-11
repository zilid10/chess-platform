CREATE TABLE match_records
(
    id            uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    white_user_id uuid NOT NULL REFERENCES users (id),
    black_user_id uuid NOT NULL REFERENCES users (id),
    result        varchar(10),
    reason        varchar(30),
    pgn           text,
    start_time    timestamptz,
    end_time      timestamptz,

    CHECK (result IN ('1-0', '1/2-1/2', '0-1')),
    CHECK (reason IN ('CHECKMATE', 'RESIGNATION', 'STALEMATE', 'ACCEPT_DRAW',
                      'INSUFFICIENT_MATERIAL', 'THREE_FOLD_REPETITION', 'FIFTY_MOVE_RULE'))
);

CREATE INDEX idx_match_records_white_user_id ON match_records (white_user_id);
CREATE INDEX idx_match_records_black_user_id ON match_records (black_user_id);

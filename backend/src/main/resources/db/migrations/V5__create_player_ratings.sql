CREATE TABLE player_ratings
(
    id           uuid                 DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    time_control text        NOT NULL,
    rating       int         NOT NULL DEFAULT 1200,
    games_played int         NOT NULL DEFAULT 0,
    peak_rating  int         NOT NULL DEFAULT 1200,
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,

    UNIQUE (user_id, time_control),
    CHECK (time_control in ('BULLET', 'BLITZ', 'RAPID', 'CLASSICAL', 'CORRESPONDENCE')),
    CHECK (rating > 0),
    CHECK (rating <= peak_rating),
    CHECK (games_played >= 0)
);

CREATE INDEX idx_player_ratings_matchmaking ON player_ratings (time_control, rating)

CREATE TABLE user_friendship
(
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    friend_id  uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, friend_id)
);

CREATE INDEX idx_user_friendship_user_id ON user_friendship (user_id);
CREATE INDEX idx_user_friendship_friend_id ON user_friendship (friend_id);

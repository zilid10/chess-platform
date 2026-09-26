CREATE TABLE friend_request
(
    id           uuid                 DEFAULT gen_random_uuid() PRIMARY KEY,
    sender_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    recipient_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    requested_at timestamptz NOT NULL DEFAULT NOW(),
    updated_at   timestamptz,
    status       varchar(10) NOT NULL DEFAULT 'PENDING',
    
    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
);

CREATE UNIQUE INDEX uq_friend_request_pending_sender_recipient
    ON friend_request (sender_id, recipient_id)
    WHERE status = 'PENDING';
CREATE INDEX idx_friend_request_sender_id ON friend_request (sender_id);
CREATE INDEX idx_friend_request_recipient_id ON friend_request (recipient_id);

CREATE TABLE users
(
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      varchar(255) NOT NULL,
    email         varchar(255) NOT NULL UNIQUE,
    password_hash varchar(68)  NOT NULL,
    about         text,
    created_at    timestamptz  NOT NULL DEFAULT NOW(),
    updated_at    timestamptz  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_app_user_name ON users(name);

CREATE TABLE users
(
    id            uuid                  DEFAULT gen_random_uuid() PRIMARY KEY,
    username      varchar(255) NOT NULL UNIQUE,
    email         varchar(255) NOT NULL UNIQUE,
    password_hash varchar(68)  NOT NULL,
    about         text,
    created_at    timestamptz  NOT NULL DEFAULT NOW(),
    updated_at    timestamptz  NOT NULL DEFAULT NOW()
);

CREATE TABLE members (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    role          VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE posts (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT       NOT NULL REFERENCES members (id),
    title      VARCHAR(255) NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_posts_member_id ON posts (member_id);

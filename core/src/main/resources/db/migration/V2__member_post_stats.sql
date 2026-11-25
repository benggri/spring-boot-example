-- Daily post counts per member, written by the batch module. Deleting a member must not be
-- blocked by (or leave behind) statistics, hence ON DELETE CASCADE.
CREATE TABLE member_post_stats (
    member_id  BIGINT      NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    stat_date  DATE        NOT NULL,
    post_count INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (member_id, stat_date)
);

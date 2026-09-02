-- Application users for Cognito sign-in. The JWT proves who someone is; this
-- table decides what they may do. No row here means no authorities, which is
-- what makes access invite-only even though Google federation cannot be blocked
-- at the Cognito pool.
--
-- Keyed on the verified email, not cognito_sub: if someone signs in with Google
-- and later gets a native account at the same address, Cognito issues two subs
-- for one person. Email resolves both to a single application user.
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    cognito_sub   VARCHAR(255) UNIQUE,
    name          VARCHAR(255),
    role          VARCHAR(32)  NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'INVITED',
    invited_by    UUID REFERENCES users (id),
    created_at    TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    last_login_at TIMESTAMP
);

-- role is ADMIN or PHOTOGRAPHER, chosen by the admin at invite time.
-- status is INVITED, ACTIVE or DISABLED. Access is revoked by setting DISABLED,
-- never by deleting the row, so uploaded photos keep their attribution.
--
-- No index on email: the UNIQUE constraint already builds one, and a second
-- would cost writes for nothing.

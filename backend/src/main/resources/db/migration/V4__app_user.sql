-- Increment 3 (accounts): a person can hold an account to track their runs on a dashboard.
-- GDPR (§7): only email + a BCrypt password hash are stored (data minimisation).
-- `app_user` (not `user`) because USER is a reserved word in SQL.

create table app_user (
    id            bigint       generated always as identity primary key,
    email         varchar(254) not null,
    password_hash varchar(100) not null,
    created_at    timestamptz  not null default now(),

    -- One account per email. Emails are stored lower-cased (normalised in the service), so a
    -- plain unique constraint is enough to make sign-up case-insensitive.
    constraint uq_app_user_email unique (email)
);

-- V3 left registration.user_id as a bare column because the users table didn't exist yet. Now it
-- does, so the seam gets its foreign key: a signed-in registration points at the account that made
-- it, while anonymous registrations keep user_id null.
alter table registration
    add constraint fk_registration_user foreign key (user_id) references app_user (id);

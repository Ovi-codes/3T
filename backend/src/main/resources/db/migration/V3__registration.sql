-- Increment 2 (core loop): a person registers for an event.
--
-- Stored in a leaderboard-friendly shape (charter §3 seam): one row per (event, participant),
-- with `finish_time` and `user_id` nullable. Anonymous registrations carry name + email now;
-- V2 results write into `finish_time` and signed-in registrations set `user_id` — both without a
-- migration. `user_id` has no FK yet because the users table arrives in Increment 3; the column
-- exists now so the seam is ready.
--
-- GDPR (§7): only name + email are collected (data minimisation); the lawful basis is the
-- transactional confirmation email, so no marketing consent is needed.

create table registration (
    id          bigint       generated always as identity primary key,
    event_id    bigint       not null references event (id),
    name        varchar(120) not null,
    email       varchar(254) not null,
    user_id     bigint,
    finish_time interval,
    created_at  timestamptz  not null default now(),

    -- One registration per email per event: submitting the same email twice is rejected
    -- at the DB level, not just in the app.
    constraint uq_registration_event_email unique (event_id, email)
);

-- Registrations are read back per event (a run's participant list, later its leaderboard).
create index idx_registration_event on registration (event_id);
-- Increment 10b (#58): an event can be cancelled.
--
-- Cancelling is deliberately not deleting (docs/adr/0001-cancel-vs-hard-delete-events.md). A hard
-- delete is allowed only while an event has no registrations; once people have signed up, the only
-- way to call a run off is to mark it CANCELLED — the registrations (and the personal data on them)
-- stay, and every registrant is emailed.
--
-- Existing rows are runs that are still on, so the default backfills them as SCHEDULED. The default
-- stays on the column so inserts that predate the enum (and the seed below) keep working.

alter table event
    add column status varchar(20) not null default 'SCHEDULED';

-- The app only knows these two states; the DB refuses anything else rather than trusting the app.
alter table event
    add constraint ck_event_status check (status in ('SCHEDULED', 'CANCELLED'));

-- The public list is "upcoming AND scheduled"; index the pair it filters and orders on.
create index idx_event_status_start_datetime on event (status, start_datetime);

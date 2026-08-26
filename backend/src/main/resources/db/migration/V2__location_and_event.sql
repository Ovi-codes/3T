-- Increment 1: the events domain.
--
-- `location` is a first-class entity from day one (charter §3 seam). Bucharest is the
-- only row for V1, but modelling it now makes multi-location (V2) a matter of data,
-- not schema surgery: an event always belongs to a location.
--
-- `start_datetime` is timestamptz so an instant is stored unambiguously (correct across
-- timezones and on Azure, where the server clock is UTC). The DB owns `created_at`.

create table location (
    id         bigint generated always as identity primary key,
    name       varchar(120) not null,
    city       varchar(120) not null,
    created_at timestamptz  not null default now()
);

create table event (
    id             bigint       generated always as identity primary key,
    location_id    bigint       not null references location (id),
    name           varchar(160) not null,
    start_datetime timestamptz  not null,
    created_at     timestamptz  not null default now()
);

-- The upcoming-events query filters and orders on start_datetime.
create index idx_event_start_datetime on event (start_datetime);

-- Seed one location and a handful of events around "now" so the upcoming-only filter
-- and date ordering are visibly testable. Offsets are relative to now(), so the past
-- events stay past and the future events stay future whenever the migration runs —
-- the tests never rot.
insert into location (name, city) values ('Tineretului Park', 'Bucharest');

insert into event (location_id, name, start_datetime)
select l.id, e.name, e.start_datetime
from location l,
     (values
         ('Tineretului parkrun', now() - interval '14 days'),
         ('Tineretului parkrun', now() - interval '7 days'),
         ('Tineretului parkrun', now() + interval '3 days'),
         ('Tineretului parkrun', now() + interval '10 days'),
         ('Tineretului parkrun', now() + interval '17 days'),
         ('Tineretului parkrun', now() + interval '24 days')
     ) as e(name, start_datetime)
where l.city = 'Bucharest';
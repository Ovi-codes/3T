-- Demo data for personal stats (#45). OPT-IN, like R__demo_leaderboard.sql: Flyway only reads this
-- folder when DEMO_DATA=true (see DemoDataConfig), so tests and prod never see it.
--
-- Gives the demo account (created by R__demo_leaderboard.sql, which runs first) eight weekly runs
-- before the leaderboard run, so its stats have a time trend to draw: 27:52 down to 24:12, then the
-- leaderboard run's 23:37 — with a couple of slower weeks so the line isn't straight.
--
-- Repeatable and idempotent: a re-run adds no run or registration that is already there.

-- Anchored on the demo account's latest past run — the leaderboard run — rather than on "the most
-- recent past run", which moves on as a dev database ages.
with demo_run as (
    select event.location_id, event.start_datetime
    from registration
    join event on event.id = registration.event_id
    where registration.email = 'demo@example.com'
      and event.start_datetime < now()
    order by event.start_datetime desc
    limit 1
)
insert into event (location_id, name, start_datetime)
select demo_run.location_id, 'Tineretului parkrun', demo_run.start_datetime - week.weeks_before * interval '7 days'
from demo_run
cross join generate_series(2, 9) as week (weeks_before)
where not exists (
    select 1
    from event existing
    where existing.location_id = demo_run.location_id
      and existing.start_datetime = demo_run.start_datetime - week.weeks_before * interval '7 days'
);

-- Each history run's field: the demo account, plus seven regulars whose times wobble a little from
-- week to week, so the demo runner's place moves between 5th and 7th. The runs made above are older
-- than the anchor, so it is still the leaderboard run.
with demo_run as (
    select event.location_id, event.start_datetime
    from registration
    join event on event.id = registration.event_id
    where registration.email = 'demo@example.com'
      and event.start_datetime < now()
    order by event.start_datetime desc
    limit 1
),
history as (
    select event.id as event_id, week.weeks_before, week.demo_time
    from demo_run
    cross join (values
        (9, interval '00:27:52'),
        (8, interval '00:27:10'),
        (7, interval '00:27:31'),
        (6, interval '00:26:18'),
        (5, interval '00:25:44'),
        (4, interval '00:25:59'),
        (3, interval '00:24:50'),
        (2, interval '00:24:12')
    ) as week (weeks_before, demo_time)
    join event
      on event.location_id = demo_run.location_id
     and event.start_datetime = demo_run.start_datetime - week.weeks_before * interval '7 days'
)
insert into registration (event_id, name, email, user_id, finish_time)
select history.event_id, runner.name, runner.email, app_user.id, runner.finish_time
from history
cross join lateral (
    select 'Demo Runner' as name, 'demo@example.com' as email, history.demo_time as finish_time
    union all
    select regular.name, regular.email,
           regular.base + ((history.weeks_before * 13) % 37) * interval '1 second'
    from (values
        ('Andrei Popescu',    'andrei.popescu@example.com',    interval '00:18:42'),
        ('Ioana Marinescu',   'ioana.marinescu@example.com',   interval '00:19:55'),
        ('Mihai Constantin',  'mihai.constantin@example.com',  interval '00:21:10'),
        ('Elena Dumitrescu',  'elena.dumitrescu@example.com',  interval '00:22:30'),
        ('Ștefan Rădulescu',  'stefan.radulescu@example.com',  interval '00:24:05'),
        ('Maria Georgescu',   'maria.georgescu@example.com',   interval '00:26:48'),
        ('Radu Stoica',       'radu.stoica@example.com',       interval '00:29:15')
    ) as regular (name, email, base)
) as runner
-- Only the demo account exists, so only its rows are linked to a user; the rest stay anonymous.
left join app_user on app_user.email = runner.email
on conflict (event_id, email) do nothing;

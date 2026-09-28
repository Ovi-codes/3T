-- Demo data for the leaderboard (#44). OPT-IN: Flyway only reads this folder when DEMO_DATA=true
-- (see DemoDataConfig), so tests and prod never see these made-up runners.
--
-- Repeatable (R__), not versioned: it runs after the V migrations, and again only if this file
-- changes, so switching the flag on for an existing dev DB never upsets Flyway's version order.
-- Idempotent: a re-run adds nothing that is already there.
--
-- Sign in as demo@example.com / demo-run-5k to see the run under Past on the dashboard.

insert into app_user (email, password_hash, name)
values ('demo@example.com', '$2a$10$GPw7kkz0yXqX31YnE61AUeAFQofPGSejKGoxujHZECaTxQSEen3zq', 'Demo Runner')
on conflict (email) do nothing;

-- The most recent past run that is still on (V2 always seeds one a week before the DB is created).
-- The field covers every case the leaderboard shows: a podium, a tie for 3rd, the demo account
-- mid-pack, and two runners who never entered a time.
with demo_run as (
    select id
    from event
    where start_datetime < now()
      and status = 'SCHEDULED'
    order by start_datetime desc
    limit 1
)
insert into registration (event_id, name, email, user_id, finish_time)
select demo_run.id, runner.name, runner.email, app_user.id, runner.finish_time
from demo_run
cross join (values
    ('Andrei Popescu',    'andrei.popescu@example.com',    interval '00:18:42'),
    ('Ioana Marinescu',   'ioana.marinescu@example.com',   interval '00:19:55'),
    ('Mihai Constantin',  'mihai.constantin@example.com',  interval '00:21:10'),
    ('Elena Dumitrescu',  'elena.dumitrescu@example.com',  interval '00:21:10'),
    ('Demo Runner',       'demo@example.com',              interval '00:23:37'),
    ('Ștefan Rădulescu',  'stefan.radulescu@example.com',  interval '00:24:05'),
    ('Maria Georgescu',   'maria.georgescu@example.com',   interval '00:26:48'),
    ('Radu Stoica',       'radu.stoica@example.com',       interval '00:29:15'),
    ('Cristina Munteanu', 'cristina.munteanu@example.com', interval '00:33:02'),
    ('Vlad Iordache',     'vlad.iordache@example.com',     null),
    ('Bianca Toma',       'bianca.toma@example.com',       null)
) as runner (name, email, finish_time)
-- Only the demo account exists, so only its row is linked to a user; the rest stay anonymous.
left join app_user on app_user.email = runner.email
on conflict (event_id, email) do nothing;

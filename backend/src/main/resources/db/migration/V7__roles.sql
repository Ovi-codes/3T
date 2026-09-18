-- Increment 10a (#57): user roles + admin event creation.
--
-- A ROLE_ADMIN is conferred by config (the ADMIN_EMAILS env var): when a configured account signs up
-- or logs in, the app lazily ensures its row here. This local `user_roles` table is the real
-- authority and stays even after a future Microsoft Entra External ID swap
-- (docs/research/entra-external-id-roles.md) — the seam will then map a token claim to these
-- authorities, joining on oid+tid. Those columns are deliberately NOT added yet.
--
-- GDPR (§7): role assignment is not sensitive personal data.

create table role (
    id   bigint       generated always as identity primary key,
    name varchar(60)  not null,
    constraint uq_role_name unique (name)
);

create table user_roles (
    user_id bigint not null references app_user (id) on delete cascade,
    role_id bigint not null references role (id),
    primary key (user_id, role_id)
);

-- The two roles the app knows about. ROLE_USER is the baseline every authenticated account carries;
-- ROLE_ADMIN is additive and only granted to configured admin emails.
insert into role (name) values ('ROLE_USER'), ('ROLE_ADMIN');

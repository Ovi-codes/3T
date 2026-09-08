-- Increment 7 (issue #38): a signed-in user shouldn't retype their details to register. The account
-- now carries the person's name, so sign-up captures it and the registration form prefills from it.
-- Mandatory: every sign-up sets a name (also validated at the API). The app isn't live yet, so there
-- are no accounts to preserve — a fresh DB provisions this column empty. GDPR (§7): still name +
-- email only — data-minimised.
alter table app_user
    add column name varchar(120) not null;

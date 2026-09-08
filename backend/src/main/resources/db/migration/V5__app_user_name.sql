-- Increment 7 (issue #38): a signed-in user shouldn't retype their details to register. The account
-- now carries the person's name, so sign-up captures it and the registration form prefills from it.
-- Nullable on purpose: accounts created in Increment 3 (email + password only) have no name; every
-- new sign-up sets it (validated at the API). GDPR (§7): still name + email only — data-minimised.
alter table app_user
    add column name varchar(120);

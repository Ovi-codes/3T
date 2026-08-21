-- Walking skeleton (Increment 0): the only job of this table is to prove that a Flyway
-- migration, JPA and the API are wired to the same database.
--
-- `version` is the primary key because it is the single row's natural identity and JPA
-- needs an @Id; there is no surrogate key to carry.

create table app_info (
    version varchar(32) not null primary key
);

insert into app_info (version) values ('0.0.1');
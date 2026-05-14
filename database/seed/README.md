# Seed Data

Seed files in this directory are not part of the production Flyway migration
path. Run them only against local or demo databases after the schema migrations
have completed.

S2 adds `S2_minimal_safe_demo_seed.sql` as a repeatable demo-safe seed. It
contains placeholder roles, permission/menu-like codes, demo internal users,
tax rule, supplier, SKU, course and course spec data. It does not include real
users, orders, payments, merchant IDs, private keys, callback URLs, SMTP
passwords, or other secrets.

S2 does not add a system configuration table. Mock/sandbox/real mode config is
seeded by the later S3 `sys_config` migration so the configuration model remains
single-sourced.

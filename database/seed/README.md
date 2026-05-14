# Seed Data

Seed files in this directory are not part of the production Flyway migration
path. Run them only against local or demo databases after the schema migrations
have completed.

S2 adds `S2_minimal_safe_demo_seed.sql` as a minimal demo-safe seed. It contains
only placeholder catalog data and does not include real users, orders, payments,
merchant IDs, private keys, callback URLs, SMTP passwords, or other secrets.

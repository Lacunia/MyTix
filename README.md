# MyTix

A database-backed ticketing-platform console application.

## Running

Configure `.env`, then run `./run.sh` in WSL/Linux.

If the database was created before the current version, run `migrate.sql` once
against that database before starting the program. It adds the named-performance
column required by the console while preserving existing rows.

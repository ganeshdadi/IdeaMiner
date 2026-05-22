# Local PostgreSQL Setup

IdeaMiner does not use Docker. For local development, point the app at a locally installed or managed PostgreSQL database with pgvector enabled.

## Homebrew Setup

Install PostgreSQL 17 and pgvector:

```bash
brew install postgresql@17 pgvector
brew services start postgresql@17
```

Homebrew's current pgvector bottle is built for PostgreSQL 17 and 18. Use PostgreSQL 17 for the local setup unless your machine has a different pgvector-compatible PostgreSQL version installed.

## Required Database

Default connection settings:

```properties
IDEAMINER_DB_URL=jdbc:postgresql://localhost:5432/ideaminer
IDEAMINER_DB_USERNAME=ideaminer
IDEAMINER_DB_PASSWORD=ideaminer
```

You can override these environment variables for any local or managed PostgreSQL instance.

## Manual Setup

Create the user and database with your local PostgreSQL admin user:

```bash
/opt/homebrew/opt/postgresql@17/bin/psql -h localhost -d postgres \
  -c "CREATE USER ideaminer WITH PASSWORD 'ideaminer';"

/opt/homebrew/opt/postgresql@17/bin/createdb -h localhost -O ideaminer ideaminer
```

Connect to the `ideaminer` database as your local PostgreSQL admin user and enable pgvector:

```bash
/opt/homebrew/opt/postgresql@17/bin/psql -h localhost -d ideaminer \
  -c "CREATE EXTENSION IF NOT EXISTS vector;"
```

The application also runs this extension statement during Flyway migration. If your PostgreSQL user cannot create extensions, ask a database admin to enable pgvector once.

## Verify

Run:

```bash
./gradlew clean build
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar health
```

Expected successful output includes:

```text
[Health] Database connection: OK
[Health] pgvector extension: OK
```

If your database is not on the default URL, pass environment variables:

```bash
IDEAMINER_DB_URL=jdbc:postgresql://db-host:5432/ideaminer \
IDEAMINER_DB_USERNAME=ideaminer \
IDEAMINER_DB_PASSWORD=secret \
java -jar build/libs/ideaminer-0.0.1-SNAPSHOT.jar health
```

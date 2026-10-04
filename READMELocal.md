# backend

Docker Compose now lives in the sibling `infra` repository; service code and Dockerfiles remain in `backend`. Clone both repositories side by side:

```text
project/
	backend/
	infra/
```

From `infra`, copy `.env.example` to `.env`, fill in the local values, then start the current user-service setup:

```sh
cp .env.example .env
docker compose up --build
```

The user-service is available on `http://localhost:8081`.
REST API server with business logic and data access layer for the Gaming Castle system

## Local environment

Copy `.env.example` to `.env` in this directory and fill in the database and
optional email/SMS settings. Keep `.env` out of source control. The shared
database variable names use the service prefix (for example,
`BOOKING_DB_URL`); load this root `.env` into each service's run configuration
or export its values before starting the service. Spring Boot does not load
`.env` files automatically.

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

# backend

REST API server with business logic and data access layer for the Gaming Castle system. Service source code and each service's Dockerfile live in this repository.

Docker Compose is maintained in the sibling `infra` repository. Clone `backend` and `infra` side by side, then run Compose from the `infra` directory:

```sh
cd ../infra
cp .env.example .env
# Fill in the local values in .env, then:
docker compose up --build
```

The current Compose setup runs `user-service` at `http://localhost:8081`.

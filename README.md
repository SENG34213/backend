# backend

REST API server with business logic and data access layer for the Gaming Castle system. Service source code and each service's Dockerfile live in this repository.

Docker Compose is maintained in the sibling `infra` repository. Clone `backend` and `infra` side by side, then run Compose from the `infra` directory:

```sh
cd ../infra
cp .env.example .env
# Fill in the local values in .env, then:
docker compose up --build
```

The Compose setup runs the API gateway at `http://localhost:8080`, with
`user-service` on port 8081, `booking-service` on 8082, `payment-service` on
8083, `tournament-service` on 8084, `loyalty-service` on 8085, and
`notification-service` on 8086. The Eureka registry is available on port 8761.

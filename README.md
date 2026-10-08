# Gaming Castle backend

This folder contains the Spring Boot microservice backend for Gaming Castle. The services are intended to run alongside the sibling `infra` repository, which owns the local Docker and PostgreSQL setup.

## Service list

- `eureka-server` — service discovery at `http://localhost:8761`
- `api-gateway` — public API entry point at `http://localhost:8080`
- `user-service` — user auth, JWTs, RBAC and password reset flows at `http://localhost:8081`
- `booking-service` — booking and game-station domain at `http://localhost:8082`
- `payment-service` — payment domain at `http://localhost:8083`
- `tournament-service` — tournament domain at `http://localhost:8084`
- `loyalty-service` — loyalty domain at `http://localhost:8085`
- `notification-service` — notification domain at `http://localhost:8086`

## Architecture notes

- Each service registers with Eureka on startup.
- The gateway handles incoming HTTP traffic and forwards requests to the correct service.
- User authentication is handled by the `user-service` and signed with a shared JWT secret.
- Each service owns its own database schema through Flyway migrations.

## Local development

Clone `backend` and `infra` as sibling directories:

```text
project/
  backend/
  infra/
```

Start the local database and the current default service setup from the `infra` repo:

```bash
cd ../infra
cp .env.example .env
# update the secrets in .env before starting the stack
docker compose up -d
```

The current Docker configuration exposes PostgreSQL on `localhost:5432`. If you are running the services directly with Maven instead of through Docker, export the matching service variables before launching them.

## Starting services manually

### 1. Eureka

```bash
cd backend/eureka-server
mvn spring-boot:run
```

### 2. User service

```bash
cd backend/user-service
export USER_DB_URL=jdbc:postgresql://localhost:5432/gamingcastle_users
export USER_DB_USER=gc_admin
export USER_DB_PASSWORD=replace-with-your-local-password
export JWT_SECRET=replace-with-a-strong-secret
export GATEWAY_INTERNAL_SECRET=replace-with-a-strong-gateway-secret
mvn spring-boot:run
```

### 3. Gateway

```bash
cd backend/api-gateway
export JWT_SECRET=replace-with-a-strong-secret
export GATEWAY_INTERNAL_SECRET=replace-with-a-strong-gateway-secret
mvn spring-boot:run
```

### 4. Optional service startup

```bash
cd backend/booking-service
export BOOKING_DB_URL=jdbc:postgresql://localhost:5432/gamingcastle_bookings
export BOOKING_DB_USER=gc_admin
export BOOKING_DB_PASSWORD=replace-with-your-local-password
export JWT_SECRET=replace-with-a-strong-secret
export GATEWAY_INTERNAL_SECRET=replace-with-a-strong-gateway-secret
export LOYALTY_BASE_URL=http://localhost:8085
mvn spring-boot:run
```

Set the same `GATEWAY_INTERNAL_SECRET` and `LOYALTY_BASE_URL` when running `payment-service`; both booking and payment call loyalty directly for booking cancellation and payment redemption/earning. `LOYALTY_BASE_URL` should be the service origin (for example, `http://localhost:8085`), not the `/api` path. The same database-variable pattern applies to `tournament-service`, `loyalty-service` and `notification-service`.

## Main API entry points

The default public routes are exposed through the gateway:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/login/phone`
- `POST /api/auth/password/forgot`
- `POST /api/auth/password/reset`
- `GET /api/users/me` (authenticated)
- `PATCH /api/users/me` (authenticated)

## Testing

```bash
cd backend/user-service
mvn test
```

The current service-level test suite focuses on authentication, authorization and lockout logic.

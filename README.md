# Heritage Marketplace Operations Management System

A full-stack marketplace platform with multi-role access control, order management with tiered benefits, inventory tracking, incident ticketing, appeal workflows, risk management, and audit compliance.

## Prerequisites

- **Docker** and **Docker Compose**

## Environment Variables

All environment variables are configured in the `docker-compose.yml` file. For production, it is recommended to use a `.env` file or a secret management system.

| Variable | Description |
|---|---|
| `JWT_SECRET` | JWT signing secret (min 64 chars) |
| `APP_ENCRYPTION_SECRET` | AES encryption key for PII fields |
| `DB_PASSWORD` | Database password |

## Getting Started

To start the entire stack (Database, Backend, and Frontend):

```bash
cd repo
docker compose up
```

- The **Frontend** will be available at `http://localhost`.
- The **Backend API** will be available at `http://localhost:8080/api/v1`.
- The **Database** will be accessible at `localhost:5432` (if you have a local client).

To run in the background:
```bash
docker compose up -d
```

To stop:
```bash
docker compose down
```

## Running Tests

You can run tests using the provided scripts. Note that some tests require the services to be running.

### Unit Tests (Backend)

Run backend unit tests inside a temporary container:
```bash
docker compose run --rm backend mvn test
```

### Frontend Tests

Run frontend tests inside a temporary container:
```bash
docker compose run --rm frontend npm test
```

### Full Test Suite (Local Script)

If you have the local environment set up, you can still use:
```bash
./run_tests.sh
```

However, for a purely Docker-based workflow, it's recommended to run tests within the containers.

## Project Structure

```
repo/
  Dockerfile        # Backend container definition
  docker-compose.yml # Orchestration for the entire stack
  src/main/java/    # Spring Boot backend
  frontend/         # React frontend (Vite)
    Dockerfile      # Frontend container definition
    nginx.conf      # Frontend server configuration
  run_tests.sh      # Unified test runner
```

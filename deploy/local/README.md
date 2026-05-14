# Local Infrastructure Without Docker

This folder documents the S1 fallback path for machines where Docker Desktop or
WSL-backed Docker cannot run.

## Required Local Services

| Service | Default endpoint | Purpose | Native start example |
|---|---|---|---|
| MySQL 8.x | `127.0.0.1:3306` | Spring Boot datasource and Flyway migrations | `net start MySQL80` or start from the MySQL installer service UI |
| Redis 7.x | `127.0.0.1:6379` | Future session, idempotency, and cache checks | `redis-server redis.windows.conf` |
| Local file path | `./storage/files` | S1 local file storage placeholder | `New-Item -ItemType Directory -Force .\storage\files` |
| Nginx or Vite proxy | `deploy/nginx/*.template` or Vite dev server proxy | Static entry and `/api` proxy path | Use `pnpm dev:admin` and H5 dev scripts for S1 |

## Configuration

Use `deploy/env/.env.local.example` as the no-secret template for native local
services. Keep real passwords in an untracked local `.env` file or shell
environment.

## Verification

Report current local service status:

```powershell
.\scripts\check-local-infra.ps1 -ReportOnly
```

Create the local file storage directory while checking:

```powershell
.\scripts\check-local-infra.ps1 -CreateStoragePath
```

The command exits with a non-zero status when required native services are not
reachable, unless `-ReportOnly` is passed.

## S1 Boundary

S1 provides this fallback and the Docker Compose template. It does not install
system services, store real credentials, or require production external systems.

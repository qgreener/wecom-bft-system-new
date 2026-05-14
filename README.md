# WeCom BFT New System

S1 creates the engineering skeleton only. This repository is the new clean
system workspace and does not depend on the old failed project.

## Scope

| Area | S1 status |
|---|---|
| Backend | Spring Boot 3.x modular monolith skeleton, `/api/health`, `/api/health/ready`, Flyway baseline migration |
| Frontend | Vue 3 + TypeScript + Vite workspaces for PC admin and three H5 entries |
| Miniprogram | WeChat miniprogram directory placeholder only |
| Database | Flyway migration under `backend/src/main/resources/db/migration/`, mirrored in `database/migration/` |
| Deploy | Docker Compose, Nginx, systemd, env template, local scripts |

## Local Commands

| Purpose | Command |
|---|---|
| Backend tests | `mvn -f backend/pom.xml test` |
| Backend dev run | `mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev` |
| Install frontend deps | `pnpm install` |
| Build PC and H5 apps | `pnpm build` |
| Frontend smoke tests | `pnpm frontend:test` |
| S1 full local verification | `.\scripts\verify-s1.ps1` |
| PC and H5 startup smoke | `.\scripts\verify-s1-frontends.ps1 -Apps admin,lead` |
| Native local infra check | `.\scripts\check-local-infra.ps1 -ReportOnly` |
| PC dev server | `pnpm dev:admin` |
| WeCom sidebar H5 dev server | `pnpm dev:wecom-sidebar` |
| Supplier H5 dev server | `pnpm dev:supplier` |
| Lead H5 dev server | `pnpm dev:lead` |

Docker Compose files are provided as deployment templates. Docker was not
validated in this Windows environment because WSL-backed Docker is unavailable.
When Docker is unavailable, use `deploy/local/README.md` and
`deploy/env/.env.local.example` for native MySQL, Redis, local file storage, and
Vite proxy checks.

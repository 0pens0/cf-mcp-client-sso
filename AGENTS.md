# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java/org/tanzu/mcpclient` holds the Spring Boot services, grouped by feature (chat, security, document, vectorstore) for easy dependency tracking.
- `src/main/resources` contains configuration (`application.yaml`, profile-specific overrides), Thymeleaf templates, static assets, and prompt definitions.
- `src/main/frontend` is the Angular workspace (`pulseui`) with `src/app` for views/components and `public` for standalone assets; build artifacts land in `dist/pulseui` before Maven copies them under `target/classes/static`.
- `docs/` captures product and platform notes; keep architectural updates there alongside code changes.
- Deployment manifests (`manifest.yml`) and helper scripts live at the root; update them whenever env variables or bound services change.

## Build, Test, and Development Commands
- `./mvnw clean package` compiles the backend, runs unit tests, and triggers the frontend Maven plugin (`npm ci` + `npm run build`).
- `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts the full stack using local configuration; ensure the PostgreSQL URL in `application-local.yaml` is reachable.
- `cd src/main/frontend && npm run start` spins up the Angular dev server with live reload for UI work; proxy API requests to `http://localhost:8080` via `proxy.conf.json` if you add new endpoints.

## Coding Style & Naming Conventions
- Follow standard Spring naming: packages lowercase, classes PascalCase, configuration beans suffixed with `Config`, and REST controllers ending with `Controller`.
- Prefer constructor injection and annotate new endpoints with explicit `@RequestMapping` paths to avoid collisions.
- Keep Java code formatted with 4-space indentation and align with existing controller/service patterns before submitting PRs.
- In Angular, use kebab-case folder names, PascalCase components/services, and keep shared UI primitives in `src/main/frontend/src/app/components`.

## Testing Guidelines
- Backend tests run through JUnit via `./mvnw test`; place them in `src/test/java` mirroring the package under test.
- Frontend tests rely on Karma/Jasmine via `npm run test`; co-locate specs next to components (`*.spec.ts`).
- Aim for meaningful coverage of security flows and MCP adapters; add integration tests for new OAuth scopes or vector-store interactions.

## Commit & Pull Request Guidelines
- Write imperative commit subjects under 72 characters (e.g., `Add CF-SSO onboarding dialog`); include context in the body when touching multiple layers.
- Reference related issues in the PR description and call out impacts on Cloud Foundry manifests or environment variables.
- Provide screenshots or terminal output for UI or authentication changes; note manual verification steps when touching login, document processing, or MCP wiring.

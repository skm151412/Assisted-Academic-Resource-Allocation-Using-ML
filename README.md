# Academic Resource Allocation System (ARA)

Monorepo structure:

- `backend/` Spring Boot API (Java 17, PostgreSQL)
- `frontend/` React client (Vite)

This repository is scaffolded against the engineering roadmap phases and enforces:

- Layered architecture (`controller -> service -> repository -> database`)
- No business logic in controllers
- Allocation only after booking approval
- Explicit separation of booking and allocation concerns

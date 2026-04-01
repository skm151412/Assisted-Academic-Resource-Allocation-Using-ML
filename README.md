# Academic Resource Allocation System (ARA)

ML-Assisted Classroom Allocation System is a web-based application that automatically assigns classrooms and labs using timetable data. It prevents room conflicts through rule-based logic and supports utilization analysis to improve efficient use of academic resources.

Monorepo structure:

- `ara-backend/` Spring MVC + JPA API (Java 17, Jetty)
- `ara-frontend/` React client

Core architecture expectations:

- Layered architecture (`controller -> service -> repository -> database`)
- No business logic in controllers
- Allocation only after booking approval
- Explicit separation of booking and allocation concerns

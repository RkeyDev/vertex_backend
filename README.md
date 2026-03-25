# Vertex - Collaborative Real-Time System Design Board (UML)

This system allows multiple users to design UML and System Design diagrams simultaneously with instant synchronization.

## Tech Stack

### Backend
- **Java 21 (LTS)**: Core business logic and RESTful services.
- **Spring Boot 3.4**: Framework for dependency injection and security.
- **Spring Security + JWT**: Stateless authentication with role-based access control.
- **WebSockets (STOMP)**: Real-time bi-directional communication for canvas synchronization.
- **PostgreSQL**: Primary relational storage for boards and user data.
- **Redis**: Pub/Sub for distributed scaling and JWT/Session caching.

### Microservices
- **Python Worker**: Decoupled service for high-performance Image/PDF export processing.

### Frontend
- **React**: Interactive drawing canvas with optimized state synchronization.

### DevOps & Tooling
- **Docker & Docker Compose**: Containerization for consistent local development.
- **GitHub Actions**: CI/CD pipeline for automated testing and linting.
- **Maven**: Dependency management and build automation.

---

## Architecture

The project follows a **Modular Monolith** approach, prepared for microservice extraction. It utilizes a distributed event-driven architecture via Redis to ensure horizontal scalability across multiple backend instances.

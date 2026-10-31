# Changelog

Este archivo registra los cambios relevantes del proyecto organizados por versión.

## [2.0.0] - 2026-10-31 - Evaluación Parcial 2: pipeline CI/CD

### Added
- Pipeline `ci-cd.yml`: Snyk → pruebas JUnit + JaCoCo → SonarCloud → Docker multistage → deploy en AWS EC2.
- Pruebas unitarias con Mockito para `AuthService`, `UsuarioService`, `RecursoFisicoService` y `LibroProyeccionService` (71 pruebas en total).
- Perfil de pruebas con H2 en memoria (`application-test.yml`).
- JaCoCo con reporte XML/HTML y mínimo de 80 % de cobertura en la capa de servicios.
- Configuración de SonarCloud en el `pom.xml` con Quality Gate bloqueante.
- Dependabot para Maven, Docker y GitHub Actions.
- Política `.snyk` con excepción justificada y fecha de vencimiento.
- Buildx y swap de 2 GB en el User Data de EC2.

### Changed
- Contraseña de PostgreSQL y clave JWT leídas desde variables de entorno.
- H2 con scope `test`.
- Imagen de runtime Java ejecutada con un usuario sin privilegios.
- Spring Cloud 2025.0.3 y versiones parchadas de Tomcat, Netty, Jackson, PostgreSQL, BouncyCastle y dependencias de Eureka.

### Fixed
- Esquema SQL con `BIGSERIAL`/`BIGINT` para coincidir con las entidades `Long` (los microservicios no arrancaban con `ddl-auto: validate`).
- `.env.example` ahora se versiona (el `.gitignore` lo excluía).

## [Unreleased]

### Added
- Documentación estándar del repositorio en Markdown.
- Workflow de GitHub Actions para validar Pull Requests y desplegar `main` en AWS EC2.
- Scripts de apoyo para preparar y reiniciar el entorno del laboratorio.
- Vista web `frontend/` para visualizar los cambios desplegados en AWS.
- Publicación automática de la vista web mediante Apache.
- Proxy de Apache hacia el API Gateway para comprobar su estado desde el navegador.

## [0.1.0] - 2026-08-28

### Added
- Proyecto Biblioteca con arquitectura de microservicios Spring Boot.
- Eureka Server.
- Microservicios de usuarios, catálogo y recursos.
- API Gateway.
- Scripts SQL para bases de datos PostgreSQL.

> En un proyecto real se recomienda actualizar este archivo en cada versión publicada y describir cambios que afecten a usuarios, operación o compatibilidad.

## [Semana 2 - Docker Compose]

- Se agrega `docker-compose.yml` para orquestar el proyecto completo.
- Se agrega PostgreSQL 16 como contenedor con volumen persistente.
- Se agregan `healthcheck` y `depends_on` con `condition: service_healthy`.
- Se agrega una red interna para resolución por nombre de servicio.
- Se agregan límites de CPU y memoria para fines didácticos.
- Se agrega Dockerfile Multi-stage reutilizable para los módulos Java.
- Se agrega Nginx como contenedor del frontend y reverse proxy al API Gateway.
- Se actualiza GitHub Actions para validar y desplegar mediante Docker Compose.
- EC2 ahora requiere Docker y Docker Compose; Java/Maven/BD/Nginx quedan dentro de contenedores.

## Quick orientation for AI coding agents

This is a multi-module Maven project (Java 21, Spring Boot 3) implementing a small "CampusCoffee" service.
Focus on the hexagonal architecture: domain (business), data (persistence/adapters), api (HTTP adapters), application (boot & dev data).

Key modules and files to inspect when making changes
- `pom.xml` (root): multi-module parent, Java 21, MapStruct/Lombok annotation processors.
- `domain/src/main/java/.../ports/PosService.java`: business contract and upsert/import semantics.
- `domain/src/main/java/.../impl/PosServiceImpl.java`: core business logic; calls `PosDataService` and `OsmDataService` ports.
- `data/`: adapters for persistence and external data
  - `data/impl/PosDataServiceImpl.java`: JPA-based persistence adapter; translates DB errors to domain exceptions.
  - `data/impl/OsmDataServiceImpl.java`: current stub for OSM; replace with HTTP client when implementing real import.
  - `data/mapper/PosEntityMapper.java`: MapStruct mapper with custom house-number parsing/merging (see comments).
  - `data/persistence/PosEntity.java` and `PosRepository.java`: JPA entity and repository; `pos_name_key` is the uniqueness constraint name used in error detection.
- `api/`: HTTP adapter
  - `api/controller/PosController.java`: REST endpoints; uses `PosDtoMapper` and `PosService`.
  - `api/mapper/PosDtoMapper.java`: MapStruct DTO-domain mapper.

Important project-specific behaviors and conventions (do not change lightly)
- Hexagonal pattern: domain defines ports (`domain/ports`); adapters live in `data/impl` and `api`.
- Upsert semantics: `PosService.upsert()` creates when `id==null`, updates when `id!=null` (update requires existing entity).
- House-number handling: domain uses a flat `houseNumber` string, persistence splits it into numeric + suffix. Mapping logic is in `PosEntityMapper` (split/merge). If you change address fields, update this mapper.
- Duplicate-name handling: DB unique constraint name is `pos_name_key`. `PosDataServiceImpl` inspects exception messages and throws `DuplicatePosNameException`.
- Dev profile side-effects: running with profile `dev` will clear DB and re-load fixtures via `LoadInitialData` (see `application/src/main/java/.../LoadInitialData.java`). Avoid running `dev` profile against production data.
- Flyway migrations are under `application/src/main/resources/db/migration`.

Build / run / test (examples)
- Build everything: `mvn clean install` (root). Parent POM configures annotation processors — ensure IDE/build uses Maven so MapStruct and Lombok compile correctly.
- Run tests: `mvn test` or `mvn -q clean install` for quiet builds.
- Start Postgres (dev):
  `docker run -d -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:17-alpine`
- Run app (dev profile clears DB and loads fixtures):
  `cd application && mvn spring-boot:run -Dspring-boot.run.profiles=dev`

Integration notes & TODO hotspots for contributors
- OSM import: `OsmDataServiceImpl.fetchNode()` is a stub. To implement real import, add an HTTP client, parse OSM XML/JSON, and update `PosServiceImpl.convertOsmNodeToPos` mapping.
- Mapping changes: if you add/remove fields to `domain.model.Pos`, update `PosDto`, `PosEntity`, and both mappers (`PosDtoMapper`, `PosEntityMapper`).
- Timestamp management: `PosEntity` uses JPA lifecycle callbacks (`@PrePersist/@PreUpdate`) to set `createdAt`/`updatedAt` — tests and mappers rely on that.
- Sequence reset: `PosRepository.resetSequence()` is used by dev clear logic — keep the native query if you preserve the sequence name.

Debugging quick tips
- Look at `application/src/main/resources/application.yaml` for datasource config and active dev settings.
- Global API error mapping is in `api/exceptions/GlobalExceptionHandler.java` — domain exceptions are intentionally translated into specific HTTP statuses.

When editing code, run:
- `mvn -pl application,test -am test` to run module-specific tests after changes (or simply `mvn test`).

If any section above is unclear or you want additional, targeted guidance (e.g., how to implement OSM HTTP client, or how to run system tests), request specifics and I'll add short, focused instructions.

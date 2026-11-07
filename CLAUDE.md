# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

LeafCare_HSF: a Spring Boot 4.1.2 / Java 17 server-rendered web app (FPT university final project). Maven build, Spring MVC (`spring-boot-starter-webmvc`) + Thymeleaf, Spring Data JPA, Bean Validation, PostgreSQL driver, Lombok. Currently just the generated skeleton (`LeafCareHsfApplication` and one context-load test).

## Commands

Use the Maven wrapper (Windows: `mvnw.cmd`; bash: `./mvnw`):

- Run: `./mvnw spring-boot:run`
- Build: `./mvnw clean package`
- All tests: `./mvnw test`
- Single test: `./mvnw test -Dtest=LeafCareHsfApplicationTests#methodName`

## Testing (enforced by CI)

GitHub Actions (`.github/workflows/ci.yml`) runs `npm run build` and `./mvnw verify` on every push to `main` and every PR; a failing test fails the build.
- Every new public method in `service/` and every controller endpoint gets a JUnit 5 test (write it first, TDD). Repository query methods you add get a `@DataJpaTest`.
- Controllers: `@WebMvcTest` + `@MockitoBean` for the service. Services: plain JUnit + Mockito, or `@DataJpaTest` + `@Import` when they need the DB.
- Cover the edge cases in the plan's "Review Focus", not just the happy path.
- Tests run on H2 (`src/test/resources/application.properties`), never on the real PostgreSQL.
- Run `./mvnw test` locally and see it pass before committing.

## Notes

- Base package is `com.son.leafcare` (note: "leafcase", not "leafcare") — put new code under it so component scanning from `LeafCareHsfApplication` finds it.
- `application.properties` has only the app name: no datasource is configured yet. PostgreSQL is the only DB driver, so the context-load test and the app will fail to start until `spring.datasource.*` is set (or an embedded/test DB is added).
- Lombok is wired through `annotationProcessorPaths` in `pom.xml` for both compile and testCompile; keep both if editing the compiler plugin.
- No git repository is initialized in this directory.

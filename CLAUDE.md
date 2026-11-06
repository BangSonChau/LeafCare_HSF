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

## Notes

- Base package is `com.son.leafcare` (note: "leafcase", not "leafcare") — put new code under it so component scanning from `LeafCareHsfApplication` finds it.
- `application.properties` has only the app name: no datasource is configured yet. PostgreSQL is the only DB driver, so the context-load test and the app will fail to start until `spring.datasource.*` is set (or an embedded/test DB is added).
- Lombok is wired through `annotationProcessorPaths` in `pom.xml` for both compile and testCompile; keep both if editing the compiler plugin.
- No git repository is initialized in this directory.

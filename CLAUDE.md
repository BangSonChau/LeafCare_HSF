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

## UI work (templates, CSS, animations)

Stack: Thymeleaf + Tailwind v4 + DaisyUI v5, server-rendered. No React, no framer-motion, no new JS libraries.

Project skills in `.claude/skills/` (use them; subagents too):
- Before writing or changing any template/CSS: use the `impeccable` skill (follow `PRODUCT.md`/`DESIGN.md` if present) and `emil-design-eng`.
- Animations: CSS transitions / `@keyframes` / Tailwind/DaisyUI classes only; use `animate` to build, `animation-performance` for jank. Respect `prefers-reduced-motion`.
- Before reporting a UI task done: run `review-animations` on changed animations and `break-ui` on new pages (long names, empty lists, many rows).
- Ignore skill advice that only applies to React/Next.js; translate it to Thymeleaf + Tailwind or skip it.

## Progress log (`docs/PROGRESS.md`)

`docs/PROGRESS.md` is the project's memory between sessions. It is imported here, so every session starts with it:

@docs/PROGRESS.md

- Start of a session: trust PROGRESS.md for status, next step and past decisions. Open only the files it names or the current task needs; do not re-explore the whole repo.
- After every finished plan task, and every commit outside the plan, update PROGRESS.md **in the same commit**:
  - rewrite "Hiện trạng" (current task, next step, open problems);
  - add one entry at the top of "Nhật ký": date, task, what was done, files changed (`git diff --stat` summary), decisions or deviations from the plan, known issues;
  - add lasting choices (library, pattern, naming) to "Quyết định quan trọng".
- Write it in Vietnamese, short: at most 5 bullets per entry. Keep the file under ~150 lines: when longer, squash old entries to one line each.
- Subagent-driven runs: the implementer subagent writes the entry in its task commit; if a reviewer forces fixes, the fix commit updates that entry. The controller checks the entry exists before marking the task complete.

## Git workflow (branch per task)

- Never commit straight to `main`. Before starting any plan task or other change, create a branch: `git checkout main && git pull && git checkout -b feat/task-<N>-<short-name>` (non-plan work: `fix/...`, `docs/...`).
- If the previous task's branch is not merged into `main` yet, branch from that branch instead (the next task needs its code) and note it in PROGRESS.md.
- Commit on the branch, then `git push -u origin <branch>` and open a PR to `main` so CI runs. Do not merge it yourself; Shin merges.
- Record the branch name in the PROGRESS.md entry.

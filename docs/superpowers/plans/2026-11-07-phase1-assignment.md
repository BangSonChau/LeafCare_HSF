# LeafCare Phase 1 (Assignment) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Execution order (UI first):**
1. Main session, interactive: `/impeccable init` → writes `PRODUCT.md` (answers Shin's questions; no code).
2. Main session, interactive: Task 1. Its UI step uses `impeccable` new-work to pick the visual direction and write `DESIGN.md`, then builds `layout/base.html` + pager. Do not hand Task 1 to a subagent: the design questions need Shin.
3. Tasks 2–5 with superpowers:subagent-driven-development, in order (2 → 3 → 4 → 5; each consumes the previous one). Every page follows `DESIGN.md` and the Task 1 layout.
4. Optional final pass: `/impeccable polish` + `break-ui` over all pages.

**Branches:** each task starts on its own branch `feat/task-<N>-<short-name>` and ends with push + PR (rules in CLAUDE.md "Git workflow"). Never commit to `main`.

**Progress log:** every task ends by updating `docs/PROGRESS.md` (rules in CLAUDE.md "Progress log") in the same commit as the code.

**Goal:** Ship phase 1: shared layout, Plant/Disease CRUD with search + pagination, leaf-photo upload with ScanHistory, and a dashboard.

**Architecture:** Layered MVC under `com.son.leafcare` (`controller / service / repository / entity`), one of each per entity. Server-rendered Thymeleaf pages extending `layout/base.html` via `thymeleaf-layout-dialect`. Images are stored on disk, DB keeps only the file name.

**Tech Stack:** Spring Boot 4.1.2, Java 17, Thymeleaf + layout dialect, Spring Data JPA, PostgreSQL, Bean Validation, Lombok, Tailwind v4 + DaisyUI, H2 (tests only).

**Spec:** `docs/superpowers/specs/2026-11-07-phase1-assignment-design.md`

## Global Constraints

- Base package `com.son.leafcare` (not `leafcase`).
- Page size 10 rows (`PageRequest.of(page, 10)`), search by name, case-insensitive.
- Upload: jpg/png/webp only, max 5 MB, check content-type AND extension; store as `UUID + extension` in `leafcare.upload-dir` (default `uploads`); served at `/uploads/**`; deleting a scan deletes its file.
- `ScanHistory.status` enum `ScanStatus { PENDING }` default `PENDING`, UI label "Chờ phân tích"; `confidence`, `plant`, `disease` nullable.
- `Plant.name` required and unique; `Disease.name` required; `Disease.plant` nullable.
- `ddl-auto=update` stays; no auth, no AI, no seed data, no Docker.
- CSS is built with `npm run build` into `static/css/output.css`; new templates must only use classes present after a rebuild (rebuild before manual check).
- Keep Lombok in both `annotationProcessorPaths` blocks of `pom.xml`.
- UI skills: any step that writes templates/CSS must use the project skills `impeccable` + `emil-design-eng` (CSS/Tailwind only, no React/framer-motion), and run `break-ui` (and `review-animations` if anything animates) before reporting done. See CLAUDE.md "UI work".
- Progress log: each task's last step before commit updates `docs/PROGRESS.md` (see CLAUDE.md "Progress log"); it goes in the task's commit.
- Routes (fixed by this plan): `/plants`, `/plants/new`, `/plants/{id}/edit`, `POST /plants`, `POST /plants/{id}`, `POST /plants/{id}/delete`; identical shape for `/diseases`; `/scans`, `/scans/new`, `/scans/{id}`, `POST /scans`, `POST /scans/{id}/delete`. Query params `q` (search) and `page` (0-based). Flash attributes `success` / `error`.

## Review Focus

- Blank / whitespace-only `q` → returns all rows, no error (tested in Task 2).
- `q` containing `%` or `_` → treated literally, not as wildcard (Task 2).
- `page` negative or past the last page → clamps to valid page, no 500 (Task 2).
- Duplicate plant name on create/edit → form error on the `name` field, not a 500 (Task 2).
- Deleting a plant still referenced by a disease or scan → flash `error`, row kept, no 500 (Task 2 for diseases, Task 4 for scans).
- Unknown id on edit/detail/delete → 404 page, not a stack trace (Task 2).
- Empty file, filename `../../x.png`, `shell.php.png`, spoofed content-type, >5 MB → friendly form error, nothing written outside `uploads/` (Task 4).
- Deleting a scan whose file is already gone → scan row still deleted (Task 4).

---

### Task 1: Foundation: dependencies, test DB, layout

**Files:**
- Modify: `pom.xml` (add `nz.net.ultraq.thymeleaf:thymeleaf-layout-dialect` 3.4.0; `com.h2database:h2` scope `test`)
- Modify: `src/main/resources/application.properties` (add `leafcare.upload-dir=uploads`, `spring.servlet.multipart.max-file-size=5MB`, `spring.servlet.multipart.max-request-size=6MB`)
- Create: `src/test/resources/application.properties` (H2 in-memory datasource `jdbc:h2:mem:leafcare;MODE=PostgreSQL`, `spring.datasource.password=`, `leafcare.upload-dir=target/test-uploads`)
- Create: `src/main/resources/templates/layout/base.html`, `src/main/resources/templates/fragments/pagination.html`
- Modify: `src/main/resources/templates/index.html` (decorate base; temporary content kept)
- Modify: `.gitignore` (add `uploads/`)
- Test: `src/test/java/com/son/leafcare/controller/HomeControllerTest.java`

**Interfaces:**
- Produces: `layout/base.html` with `layout:fragment="content"` slot, navbar links to `/`, `/plants`, `/diseases`, `/scans`, flash `success`/`error` alerts, footer. `fragments/pagination.html` defines fragment `pager(page, baseUrl, q)` where `page` is a Spring `Page<?>`; links keep `q` and set `page`. Test resources give every later `@SpringBootTest`/`@DataJpaTest` an H2 DB.

- [ ] **Step 1: Write failing test** `HomeControllerTest` (`@WebMvcTest(HomeController.class)` — Boot 4 package `org.springframework.boot.webmvc.test.autoconfigure`; confirm import via IDE): `GET /` → 200, view `index`, response body contains `LeafCare` navbar link `href="/plants"` (proves the layout is applied).
- [ ] **Step 2: Run** `./mvnw test -Dtest=HomeControllerTest` → FAIL (no `/plants` link).
- [ ] **Step 3: Implement** pom/properties/templates above. Navbar and footer live only in `base.html`; `index.html` uses `layout:decorate="~{layout/base}"`. If the dialect is not auto-registered under Boot 4 (symptom: `layout:decorate` ignored, no navbar), add `@Bean LayoutDialect` in a `config/WebConfig` class; if it still fails, fall back to `th:replace` fragments (spec option A) and note it in the commit. UI: use skills `impeccable` + `emil-design-eng`; run `break-ui` before reporting done.
- [ ] **Step 4: Run** `./mvnw test` → PASS, including `LeafCareHsfApplicationTests.contextLoads` (now on H2).
- [ ] **Step 5: Run** `npm run build`, then `./mvnw spring-boot:run` and open `/` to eyeball navbar/footer.
- [ ] **Step 6: Update `docs/PROGRESS.md`**: new "Nhật ký" entry for this task (what was done, files changed, decisions/deviations, known issues) and refresh "Hiện trạng" with the next task.
- [ ] **Step 7: Commit** `git add -A && git commit -m "feat: layout dialect base template, test DB"`

---

### Task 2: Plant CRUD (establishes the pattern)

**Files:**
- Create: `entity/Plant.java`, `repository/PlantRepository.java`, `service/PlantService.java`, `controller/PlantController.java`, `exception/NotFoundException.java` (+ `@ResponseStatus(NOT_FOUND)`)
- Create: `templates/plants/list.html`, `templates/plants/form.html`
- Test: `repository/PlantRepositoryTest.java`, `controller/PlantControllerTest.java`

**Interfaces:**
- Produces:
  - `Plant { Long id; @NotBlank @Column(unique) String name; String scientificName; @Size(max=2000) String description }` (Lombok `@Getter @Setter @NoArgsConstructor`)
  - `PlantRepository extends JpaRepository<Plant, Long>`: `Page<Plant> findByNameContainingIgnoreCase(String q, Pageable p)`; `boolean existsByNameIgnoreCase(String name)`
  - `PlantService`: `Page<Plant> search(String q, int page)` (trims `q`, clamps `page` to `[0, totalPages-1]`, size 10, sorted by name), `Plant get(Long id)` (throws `NotFoundException`), `Plant save(Plant p)` (throws `DuplicateNameException` if another plant has the name), `void delete(Long id)` (throws `InUseException` on `DataIntegrityViolationException`). Create `DuplicateNameException` and `InUseException` as `RuntimeException`s in `exception/`.
  - Pattern reused by Task 3: controller methods `list(q,page,model)`, `newForm`, `create(@Valid ...)`, `editForm`, `update`, `delete`; on `DuplicateNameException` use `bindingResult.rejectValue("name", "duplicate", "Tên đã tồn tại")`; on `InUseException` flash `error`.

- [ ] **Step 1: Write failing tests** in `PlantRepositoryTest` (`@DataJpaTest`, Boot 4 package `org.springframework.boot.data.jpa.test.autoconfigure`): seed 25 plants "Cây 01".."Cây 25" plus "Lan 100%" → (a) `findByNameContainingIgnoreCase("cây", PageRequest.of(0,10))` has `totalElements==25`, `content.size()==10`; (b) page 2 has 5; (c) `"%"` matches only "Lan 100%"; (d) `""` matches all 26. In `PlantControllerTest` (`@WebMvcTest` + `@MockitoBean PlantService`): POST `/plants` with blank name → 200 form view with field error on `name`; POST with service throwing `DuplicateNameException` → 200 form view, field error on `name`; GET `/plants/99/edit` with service throwing `NotFoundException` → 404; POST `/plants/1/delete` with service throwing `InUseException` → redirect `/plants` with flash `error`.
- [ ] **Step 2: Run** `./mvnw test -Dtest='PlantRepositoryTest,PlantControllerTest'` → FAIL (classes missing).
- [ ] **Step 3: Implement** entity, repository, service, controller, templates. `list.html`: search box (`q`), table (name, scientific name, actions edit/delete with confirm), "Thêm cây" button, pagination via `fragments/pagination :: pager`. `form.html` shared for create/edit with per-field errors. Success → redirect `/plants` with flash `success`. UI: use skills `impeccable` + `emil-design-eng`; run `break-ui` before reporting done.
- [ ] **Step 4: Run** the same command → PASS.
- [ ] **Step 5: Update `docs/PROGRESS.md`**: new "Nhật ký" entry for this task (what was done, files changed, decisions/deviations, known issues) and refresh "Hiện trạng" with the next task.
- [ ] **Step 6: Commit** `git add -A && git commit -m "feat: plant CRUD with search and pagination"`

---

### Task 3: Disease CRUD

**Files:**
- Create: `entity/Disease.java`, `repository/DiseaseRepository.java`, `service/DiseaseService.java`, `controller/DiseaseController.java`, `templates/diseases/list.html`, `templates/diseases/form.html`
- Test: `repository/DiseaseRepositoryTest.java`, `controller/DiseaseControllerTest.java`

**Interfaces:**
- Consumes: `PlantService`/`PlantRepository` (`findAll(Sort)` for the plant dropdown), `NotFoundException`, `InUseException`, pager fragment from Task 2.
- Produces: `Disease { Long id; @NotBlank String name; String symptoms; String treatment; @ManyToOne(fetch=LAZY) Plant plant }`; `DiseaseRepository`: `Page<Disease> findByNameContainingIgnoreCase(String q, Pageable p)`; `DiseaseService` with the same method set as `PlantService` minus the duplicate-name check (names need not be unique). Form binds plant via `plant.id` select with an empty "— Không chọn —" option mapping to `null`.

- [ ] **Step 1: Write failing tests**: repository search/page test as in Task 2 (25 diseases, page 3 has 5); controller: POST `/diseases` blank name → field error; POST with `plant.id` empty → saves with `plant==null` (verify via `ArgumentCaptor` on the service mock).
- [ ] **Step 2: Run** `./mvnw test -Dtest='Disease*Test'` → FAIL.
- [ ] **Step 3: Implement** mirroring Task 2. List shows plant name or "—". Avoid LazyInitialization in templates: repository method uses `@EntityGraph(attributePaths="plant")`. UI: use skills `impeccable` + `emil-design-eng`; run `break-ui` before reporting done.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Update `docs/PROGRESS.md`**: new "Nhật ký" entry for this task (what was done, files changed, decisions/deviations, known issues) and refresh "Hiện trạng" with the next task.
- [ ] **Step 6: Commit** `git add -A && git commit -m "feat: disease CRUD"`

---

### Task 4: Scan upload and history

**Files:**
- Create: `entity/ScanStatus.java`, `entity/ScanHistory.java`, `repository/ScanHistoryRepository.java`, `service/FileStorageService.java`, `service/ScanHistoryService.java`, `controller/ScanController.java`, `exception/InvalidImageException.java`, `config/WebConfig.java`, `templates/scans/list.html`, `templates/scans/form.html`, `templates/scans/detail.html`
- Test: `service/FileStorageServiceTest.java`, `service/ScanHistoryServiceTest.java`, `controller/ScanControllerTest.java`

**Interfaces:**
- Consumes: `NotFoundException`, pager fragment.
- Produces:
  - `ScanStatus { PENDING }` with `getLabel()` → "Chờ phân tích".
  - `ScanHistory { Long id; String imageFileName; @Size(max=1000) String note; @Enumerated(STRING) ScanStatus status = PENDING; Double confidence; @ManyToOne(LAZY) Plant plant; @ManyToOne(LAZY) Disease disease; LocalDateTime createdAt }` (`@PrePersist` sets `createdAt`).
  - `ScanHistoryRepository`: `Page<ScanHistory> findAll(Pageable)` (sorted `createdAt desc`), `List<ScanHistory> findTop5ByOrderByCreatedAtDesc()`.
  - `FileStorageService(@Value("${leafcare.upload-dir}") String dir)`: `String store(MultipartFile f)` (throws `InvalidImageException` with a Vietnamese message), `void delete(String fileName)` (silent if missing). Validation: not empty; size ≤ 5 MB; extension from the *last* dot of the original name, lower-cased, in `{jpg,jpeg,png,webp}`; content-type in `{image/jpeg,image/png,image/webp}`; saved name `UUID + "." + ext`; resolved path must stay inside the upload dir. Creates the dir if absent.
  - `ScanHistoryService`: `ScanHistory create(MultipartFile f, String note)`, `Page<ScanHistory> list(int page)` (clamped, size 10), `ScanHistory get(Long id)`, `void delete(Long id)` (row first, then file).
  - `WebConfig implements WebMvcConfigurer`: maps `/uploads/**` → `file:<abs upload dir>/`.
  - Controller: `@ExceptionHandler`-free; `create` catches `InvalidImageException` and `MaxUploadSizeExceededException` handled via `@ExceptionHandler(MaxUploadSizeExceededException.class)` in `ScanController` returning the form view with `error` "Ảnh tối đa 5 MB".

- [ ] **Step 1: Write failing tests.** `FileStorageServiceTest` (`@TempDir`): stores `MockMultipartFile("file","leaf.png","image/png",bytes)` → returned name matches `[0-9a-f-]{36}\.png`, file exists in temp dir; rejects (each asserts `InvalidImageException` and temp dir empty): `leaf.gif`/`image/gif`, `leaf.png` with content-type `text/html`, `shell.php` with `image/png`, empty file, 5 MB + 1 byte; `../../x.png` stores inside temp dir with UUID name; `shell.php.png` → stored name ends `.png` and contains no `shell`; `delete("missing.png")` does not throw. `ScanHistoryServiceTest` (`@DataJpaTest` + `@Import` services, `@TempDir` via property override): `create` yields `status==PENDING`, `createdAt!=null`; `delete` removes row and file; `delete` when file already missing still removes row; `list(99)` clamps to last page. `ScanControllerTest`: POST `/scans` with service throwing `InvalidImageException` → 200 form view with `error`; GET `/scans/99` with `NotFoundException` → 404.
- [ ] **Step 2: Run** `./mvnw test -Dtest='FileStorageServiceTest,ScanHistoryServiceTest,ScanControllerTest'` → FAIL.
- [ ] **Step 3: Implement** the classes above. `form.html`: `enctype="multipart/form-data"`, file input `accept="image/jpeg,image/png,image/webp"`, note textarea. `list.html`: thumbnail, note excerpt, status badge, created time, paged. `detail.html`: full image, note, status label, delete button (confirm). Success → redirect `/scans/{id}` with flash `success`. UI: use skills `impeccable` + `emil-design-eng`; run `break-ui` before reporting done.
- [ ] **Step 4: Run** same command → PASS.
- [ ] **Step 5: Manual check:** `npm run build`, run app, upload a real jpg, see it in list/detail, delete it, confirm the file left `uploads/`.
- [ ] **Step 6: Update `docs/PROGRESS.md`**: new "Nhật ký" entry for this task (what was done, files changed, decisions/deviations, known issues) and refresh "Hiện trạng" with the next task.
- [ ] **Step 7: Commit** `git add -A && git commit -m "feat: leaf photo upload and scan history"`

---

### Task 5: Dashboard

**Files:**
- Modify: `controller/HomeController.java`, `templates/index.html`
- Create: `service/DashboardService.java`
- Test: `controller/HomeControllerTest.java` (extend from Task 1)

**Interfaces:**
- Consumes: `PlantRepository.count()`, `DiseaseRepository.count()`, `ScanHistoryRepository.count()` and `findTop5ByOrderByCreatedAtDesc()`.
- Produces: `record DashboardStats(long plants, long diseases, long scans, List<ScanHistory> recent)`; `DashboardService.stats()`. Model attribute `stats`.

- [ ] **Step 1: Write failing test:** in `HomeControllerTest` mock `DashboardService.stats()` returning `new DashboardStats(3,2,7,List.of())`; `GET /` → model attribute `stats` set and body contains `3`, `2`, `7` in their stat cards; with empty `recent` body shows "Chưa có lượt quét nào".
- [ ] **Step 2: Run** `./mvnw test -Dtest=HomeControllerTest` → FAIL.
- [ ] **Step 3: Implement** service, controller, template (three DaisyUI `stat` cards linking to the lists, recent-scans table linking to `/scans/{id}`, call-to-action to `/scans/new`). UI: use skills `impeccable` + `emil-design-eng`; run `break-ui` before reporting done.
- [ ] **Step 4: Run** `./mvnw test` (full suite) → all PASS.
- [ ] **Step 5: Update `docs/PROGRESS.md`**: new "Nhật ký" entry for this task (what was done, files changed, decisions/deviations, known issues) and refresh "Hiện trạng" with the next task.
- [ ] **Step 6: Commit** `git add -A && git commit -m "feat: dashboard"`

---

## Self-review notes

- Spec coverage: layout (T1), Plant/Disease CRUD+search+paging (T2–3), upload/history/detail/delete (T4), dashboard (T5), Tailwind (T1 rebuild), tests named in spec (T2 repository, T4 upload service) plus test DB (T1). Slides/guide/auth/seed explicitly out.
- Open decisions made here because the spec left them: routes, flash names, block-delete when a plant/disease is referenced, dashboard shows last 5 scans.

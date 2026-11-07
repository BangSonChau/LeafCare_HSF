# LeafCare – Nhật ký tiến độ

> Claude cập nhật file này sau mỗi task/commit (quy định trong CLAUDE.md, mục "Progress log"). Session mới đọc file này trước, không đọc lại cả project.

## Hiện trạng

- Đang ở: Task 2 (Plant CRUD) xong trên nhánh `feat/task-2-plant-crud` (phân từ `main` sau khi Task 1 merge), chờ review/PR. Plan: `docs/superpowers/plans/2026-11-07-phase1-assignment.md`.
- Việc tiếp theo: Task 3 (Disease CRUD), dùng lại đúng mẫu `PlantController`/`PlantService`/`plants/*.html`; nhánh `feat/task-3-...` phân từ `feat/task-2-plant-crud` nếu chưa merge.
- Vấn đề đang mở: chưa chạy finish review của impeccable; chưa push/PR; xoá cây đang được tham chiếu mới chỉ test bằng mock (khi có Disease/Scan sẽ cần test thật).
- Task 3 phải thêm test thật: xoá một cây đang được Disease tham chiếu thì dòng vẫn còn và ném `InUseException`.

## Quyết định quan trọng

- Spring Boot 4.1.2 + Thymeleaf (layout dialect) + Tailwind v4 + DaisyUI v5, render phía server, không React/JS library mới.
- Package gốc `com.son.leafcare`. Test chạy trên H2, không đụng PostgreSQL thật.
- CI (GitHub Actions) chạy `npm run build` + `./mvnw verify` mỗi push/PR; test fail là build fail.
- Thiết kế: hướng "Phòng khám cây" (xanh lâm sàng #0B7A4B trên nền trắng men #F4F8F5, font Be Vietnam Pro, nút "Quét lá" luôn trong tầm ngón cái). Chi tiết ở `DESIGN.md`, `PRODUCT.md`, `.impeccable/surfaces/`.
- Thanh điều hướng đánh dấu mục đang mở bằng model attribute `currentPath` (`GlobalModelAdvice`), vì Thymeleaf 3.1 không còn `#request`.
- Test pager dùng `SpringTemplateEngine` + `WebContext` (link `@{/...}` cần web context).
- UI dùng skill trong `.claude/skills/`: impeccable, emil-design-eng, animate, animation-performance, break-ui, review-animations.

## Nhật ký (mới nhất ở trên)

### 2026-11-07 – Task 2: Plant CRUD
- Đã làm: `Plant`, `PlantRepository`, `PlantService` (search trim/sort theo tên/clamp trang, chống trùng tên không phân biệt hoa thường, xoá -> `InUseException`), `PlantController`, `plants/list.html` (bảng trên desktop, hàng xếp dọc trên điện thoại, empty state 2 loại) và `plants/form.html`; ba exception trong `exception/`.
- File: 3 file test mới (repository, service, controller; 35 test xanh), template, `output.css` build lại.
- Quyết định: `%`/`_` được Spring Data `Containing` tự escape; thêm `findByNameIgnoreCase` (so sánh id khi sửa) và `@Size(max=255)` cho tên; xác nhận xoá bằng `confirm()` gốc của trình duyệt. Nhánh: `feat/task-2-plant-crud`.
- Kiểm tra: `./mvnw test` xanh; xem tay 1280/390 trên H2 ổn, tên rất dài không tràn ngang.
- Còn lại: màn form lỗi và empty state chỉ kiểm tra bằng test/markup, chưa chụp.

### 2026-11-07 – Task 1: layout, theme, pager
- Đã làm: chọn hướng thiết kế qua `/impeccable` (new-work), viết `DESIGN.md`; làm `layout/base.html` (top bar desktop, bottom bar điện thoại, flash success/error), `fragments/brand.html` (logo), `fragments/pagination.html`; theme DaisyUI `leafcare`; thêm `thymeleaf-layout-dialect` 3.4.0 (tự đăng ký được trên Boot 4, không cần `@Bean`).
- File: 15 file, gồm `pom.xml`, `application.properties`, `input.css`/`output.css`, `index.html`, `GlobalModelAdvice`, `HomeControllerTest`, `PagerFragmentTest`.
- Lệch plan: thêm `PagerFragmentTest` (3 test) và `GlobalModelAdvice`; H2 và `src/test/resources/application.properties` đã có sẵn nên không thêm. Nhánh: `feat/task-1-layout`.
- Kiểm tra: `./mvnw test` 5/5 xanh; chụp desktop 1280 và phone 390 trên H2 ổn.
- Còn lại: font tải từ Google Fonts (cần mạng); không có favicon (404 nhẹ).

### 2026-11-07 – Setup trước Phase 1
- Đã làm: cài Superpowers, cài 6 skill UI ở mức project, CI GitHub Actions chạy xanh trên `main`, viết spec + plan Phase 1 (5 task), thêm nhật ký tiến độ này.
- File: `CLAUDE.md` (mục UI work, Testing, Progress log), `.github/workflows/ci.yml`, `docs/superpowers/`, `docs/PROGRESS.md`.

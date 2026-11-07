# LeafCare – Nhật ký tiến độ

> Claude cập nhật file này sau mỗi task/commit (quy định trong CLAUDE.md, mục "Progress log"). Session mới đọc file này trước, không đọc lại cả project.

## Hiện trạng

- Đang ở: Task 2 (Plant CRUD) xong trên nhánh `feat/task-2-plant-crud` (phân từ `main` sau khi Task 1 merge), chờ PR. Plan: `docs/superpowers/plans/2026-11-07-phase1-assignment.md`.
- Việc tiếp theo: Task 3 (Disease CRUD), dùng lại đúng mẫu `PlantController`/`PlantService`/`plants/*.html`; nhánh `feat/task-3-...` phân từ `feat/task-2-plant-crud` nếu chưa merge.
- Vấn đề đang mở: chưa chạy finish review của impeccable; chưa push/PR; xoá cây đang được tham chiếu mới chỉ test bằng mock: Task 3 phải thêm test thật, xoá một cây đang được Disease tham chiếu thì dòng vẫn còn và ném `InUseException`.
- Task 3: khi ô chọn cây để trống, `disease.plant` bind ra object `Plant` có id null; service phải set `disease.plant = null` (nếu không sẽ bị `TransientPropertyValueException`).

## Quyết định quan trọng

- Spring Boot 4.1.2 + Thymeleaf (layout dialect) + Tailwind v4 + DaisyUI v5, render phía server, không React/JS library mới.
- Package gốc `com.son.leafcare`. Test chạy trên H2, không đụng PostgreSQL thật.
- CI (GitHub Actions) chạy `npm run build` + `./mvnw verify` mỗi push/PR; test fail là build fail.
- Thiết kế: hướng "Phòng khám cây" (xanh lâm sàng #0B7A4B trên nền trắng men #F4F8F5, font Be Vietnam Pro, nút "Quét lá" luôn trong tầm ngón cái). Chi tiết ở `DESIGN.md`, `PRODUCT.md`, `.impeccable/surfaces/`.
- Thanh điều hướng đánh dấu mục đang mở bằng model attribute `currentPath` (`GlobalModelAdvice`), vì Thymeleaf 3.1 không còn `#request`.
- Test pager dùng `SpringTemplateEngine` + `WebContext` (link `@{/...}` cần web context).
- UI dùng skill trong `.claude/skills/`: impeccable, emil-design-eng, animate, animation-performance, break-ui, review-animations.
- Mẫu CRUD Task 3 phải copy từ Plant: id lạ -> `NotFoundException` (404); trùng tên -> `bindingResult.rejectValue("name","duplicate","Tên đã tồn tại")`; xoá khi đang được tham chiếu -> `InUseException` -> flash `error`; `confirm()` khi xoá; `@Size(max=255)` cho chuỗi ngắn.

## Nhật ký (mới nhất ở trên)

### 2026-11-07 – Task 2: Plant CRUD
- Đã làm: `Plant`, `PlantRepository`, `PlantService` (search trim/sort theo tên/clamp trang, chống trùng tên không phân biệt hoa thường bằng `existsByNameIgnoreCase[AndIdNot]`, xoá -> `InUseException`), `PlantController`, `plants/list.html` + `form.html`, 3 exception; trang lỗi tiếng Việt `error/404`, `error/4xx`, `error/5xx` (Boot chỉ nhận tên theo mã/dải mã, `error/error` không được dùng).
- Test: 5 lớp test mới (repository, service mock, `PlantServiceDbTest` trên DB thật, controller, `ErrorPageTest` chạy server thật); toàn bộ xanh. `PlantServiceDbTest` tìm ra lỗi thật: kết quả rỗng với `page>0` trả về trang số 5 thay vì trang 0 (đã sửa).
- Quyết định: `%`/`_` được Spring Data `Containing` tự escape; `@Size(max=255)` cho tên; xác nhận xoá bằng `confirm()`. Nhánh: `feat/task-2-plant-crud`.
- Kiểm tra tay 1280/390 trên H2 ổn, tên rất dài không tràn ngang.
- Còn lại: form lỗi và empty state chưa chụp; xoá cây bị tham chiếu mới test bằng mock (Task 3 bổ sung); xoá xong mất `q`/`page`; `page` không phải số trả 400.

### 2026-11-07 – Task 1: layout, theme, pager
- Đã làm: chọn hướng thiết kế qua `/impeccable` (new-work), viết `DESIGN.md`; làm `layout/base.html` (top bar desktop, bottom bar điện thoại, flash success/error), `fragments/brand.html` (logo), `fragments/pagination.html`; theme DaisyUI `leafcare`; thêm `thymeleaf-layout-dialect` 3.4.0 (tự đăng ký được trên Boot 4, không cần `@Bean`).
- File: 15 file, gồm `pom.xml`, `application.properties`, `input.css`/`output.css`, `index.html`, `GlobalModelAdvice`, `HomeControllerTest`, `PagerFragmentTest`.
- Lệch plan: thêm `PagerFragmentTest` (3 test) và `GlobalModelAdvice`; H2 và `src/test/resources/application.properties` đã có sẵn nên không thêm. Nhánh: `feat/task-1-layout`.
- Kiểm tra: `./mvnw test` 5/5 xanh; chụp desktop 1280 và phone 390 trên H2 ổn.
- Còn lại: font tải từ Google Fonts (cần mạng); không có favicon (404 nhẹ).

### 2026-11-07 – Setup trước Phase 1
- Đã làm: cài Superpowers, cài 6 skill UI ở mức project, CI GitHub Actions chạy xanh trên `main`, viết spec + plan Phase 1 (5 task), thêm nhật ký tiến độ này.
- File: `CLAUDE.md` (mục UI work, Testing, Progress log), `.github/workflows/ci.yml`, `docs/superpowers/`, `docs/PROGRESS.md`.

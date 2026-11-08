# LeafCare – Nhật ký tiến độ

> Claude cập nhật file này sau mỗi task/commit (quy định trong CLAUDE.md, mục "Progress log"). Session mới đọc file này trước, không đọc lại cả project.

## Hiện trạng

- Đang ở: Phase 1 (5 task + Flyway + README) đã merge vào `main`. Timeline cập nhật ở `docs/TIMELINE.md` (thay bản PDF).
- Việc tiếp theo: Giai đoạn 2 (tuần 4–5): Spring Security + Google OAuth2, Subscription/Payment, MoMo sandbox + IPN, trang chọn gói / kết quả / lịch sử giao dịch, giới hạn Free 5 lượt/ngày.
- Vấn đề đang mở: xoá cây/bệnh đang được tham chiếu mới test bằng mock; font tải từ Google Fonts; chưa có favicon.

## Quyết định quan trọng

- Spring Boot 4.1.2 + Thymeleaf (layout dialect) + Tailwind v4 + DaisyUI v5, render phía server, không React/JS library mới.
- Package gốc `com.son.leafcare`. Test chạy trên H2, không đụng PostgreSQL thật.
- CI (GitHub Actions) chạy `npm run build` + `./mvnw verify` mỗi push/PR; test fail là build fail.
- Thiết kế: hướng "Phòng khám cây" (xanh lâm sàng #0B7A4B trên nền trắng men #F4F8F5, font Be Vietnam Pro, nút "Quét lá" luôn trong tầm ngón cái). Chi tiết ở `DESIGN.md`, `PRODUCT.md`, `.impeccable/surfaces/`.
- Thanh điều hướng đánh dấu mục đang mở bằng model attribute `currentPath` (`GlobalModelAdvice`), vì Thymeleaf 3.1 không còn `#request`.
- Test pager dùng `SpringTemplateEngine` + `WebContext` (link `@{/...}` cần web context).
- Thanh toán dùng MoMo sandbox (không dùng VNPay). Trang kết quả đọc trạng thái từ DB (cập nhật qua IPN), không tin tham số redirect.
- UI dùng skill trong `.claude/skills/`: impeccable, emil-design-eng, animate, animation-performance, break-ui, review-animations.
- DB: Flyway quản lý schema (`db/migration`), Hibernate `ddl-auto=validate`; file đã merge không sửa, thêm V mới. Dữ liệu mẫu ở `db/seed/V1_1__seed_data.sql` (chỉ app chạy, test không). `baseline-on-migrate=true` để DB cũ tạo bằng `update` nhận là V1.
- Mẫu CRUD Task 3 phải copy từ Plant: id lạ -> `NotFoundException` (404); trùng tên -> `bindingResult.rejectValue("name","duplicate","Tên đã tồn tại")`; xoá khi đang được tham chiếu -> `InUseException` -> flash `error`; `confirm()` khi xoá; `@Size(max=255)` cho chuỗi ngắn.

## Nhật ký (mới nhất ở trên)

### 2026-11-08 – Cập nhật timeline (ngoài plan)
- Đã làm: chuyển timeline sang Markdown `docs/TIMELINE.md`; đổi VNPay -> MoMo sandbox; liệt kê trang chọn gói, nút thanh toán MoMo, trang kết quả, lịch sử giao dịch; đánh dấu Phase 1 xong.
- File: `docs/TIMELINE.md` (mới), `docs/PROGRESS.md`. Giữ `LeafCare_Timeline.pdf` làm bản cũ.

### 2026-11-07 – Flyway migration (ngoài plan)
- Đã làm: thêm `spring-boot-starter-flyway` + `flyway-database-postgresql`; `V1__init.sql` (plant, disease, scan_history từ entity); `db/seed/V1_1__seed_data.sql` (3 cây, 3 bệnh); `ddl-auto=validate` ở cả app và test.
- Test: 96 test xanh, test H2 chạy migration thật rồi validate. Thử PostgreSQL 16: DB trống -> V1 + seed; DB cũ tạo bằng `update` -> baseline V1, seed bỏ qua cây trùng tên.
- Quyết định: seed đặt version 1.1 ở thư mục riêng để không chiếm số V2 của schema và để test không có dữ liệu mẫu. Nhánh: `chore/flyway`.
- File: `pom.xml`, 2 `application.properties`, 2 file SQL, `CLAUDE.md`, `PROGRESS.md`.

### 2026-11-08 – Task 5: Dashboard
- Đã làm: `DashboardService.stats()` + record `DashboardStats`, `HomeController` gắn model `stats`, `index.html` có 3 thẻ số liệu link tới danh sách, 5 lượt quét gần đây (link `/scans/{id}`), empty state, nút "Quét lá".
- Test: `HomeControllerTest` (2), `DashboardServiceTest` (1); `./mvnw verify` xanh.
- Quyết định: ghi chú dài bị cắt 1 dòng (`line-clamp-1`), ảnh lỗi có nền xám; badge trạng thái ẩn trên điện thoại. Nhánh: `feat/task-5-dashboard`.
- Kiểm tay 1280/390 (0 dòng, ghi chú dài, thiếu ảnh): bỏ nút "Quét lá" thừa ở đầu trang (đã có trên thanh trên/dưới), thẻ số liệu 3 cột trên điện thoại, số có dấu chấm ngăn nghìn, `alt=""` cho ảnh thumbnail.

### 2026-11-07 – Task 4: Scan upload và lịch sử
- Đã làm: `ScanStatus`, `ScanHistory`, `ScanHistoryRepository`, `FileStorageService` (đuôi theo dấu chấm cuối, content-type, <=5 MB, tên UUID, chặn path traversal), `ScanHistoryService` (xoá dòng trước rồi tới file), `ScanController`, `WebConfig` (`/uploads/**`), 3 template `scans/*`.
- Test: `FileStorageServiceTest`, `ScanHistoryServiceTest` (`@DataJpaTest` + `@TempDir`), `ScanControllerTest`; `./mvnw verify` xanh. Kiểm tay trên H2: upload, list/detail 1280 và 390, xoá thì file biến mất, file >5 MB và sai loại hiện lỗi tiếng Việt.
- Lệch plan: thêm `spring.servlet.multipart.resolve-lazily=true`, vì không có nó `MaxUploadSizeExceededException` bị ném trước controller (trả 413) và `@ExceptionHandler` không chạy; handler tự đặt `currentPath` vì advice không chạy cho exception handler.
- Quyết định: ghi chú rỗng lưu `null`, ghi chú >1000 ký tự bị chặn ở controller; list chỉ có 1 hàng/lượt quét, badge nằm dưới ngày giờ để không chật trên điện thoại. Nhánh: `feat/task-4-scan-upload`.
- Còn lại: không kiểm tra magic bytes; hook impeccable báo `broken-image` cho `th:src` động (báo nhầm, bỏ qua).

### 2026-11-07 – Task 3: Disease CRUD
- Đã làm: `Disease` (name, symptoms, treatment, `plant` LAZY), `DiseaseRepository` (`@EntityGraph("plant")`), `DiseaseService` (không kiểm tra trùng tên), `DiseaseController`, `diseases/list.html` + `form.html` (select cây, tuỳ chọn "— Không chọn —"); thêm `PlantService.findAll()` cho dropdown. Nav "Bệnh" đã có sẵn trong layout.
- Quyết định: ô cây trống bind ra `Plant` id null -> service đặt `plant = null`; id cây lạ -> `NotFoundException`; xoá bệnh không cần `InUseException` (chưa có bảng tham chiếu).
- Test: `DiseaseRepositoryTest`, `DiseaseServiceDbTest` (DB thật: plant null, plant lạ, tên trùng, xoá, và xoá Plant đang được Disease tham chiếu -> `InUseException`, dòng vẫn còn, chạy ngoài transaction test), `DiseaseControllerTest`; `PlantService.delete` đã flush sẵn nên không cần sửa. 64 test xanh.
- File: 3 entity/repo/service/controller mới, 2 template, 3 test mới, `output.css` build lại. Nhánh: `feat/task-3-disease-crud`.
- UI (commit sau): chạy impeccable, emil-design-eng, break-ui; kiểm tra tay 1280/390 trên H2 với tên 250 ký tự, tên cây dài, không cây: không tràn ngang. Sửa: tên bệnh `line-clamp-3`, tên cây `line-clamp-2` kèm `title`; ô "không có cây" thêm chữ ẩn "Chưa gắn cây" cho trình đọc màn hình.
- Còn lại: chưa chụp form lỗi/empty state.

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

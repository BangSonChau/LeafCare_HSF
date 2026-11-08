# LeafCare – Nhật ký tiến độ

> Claude cập nhật file này sau mỗi task/commit (quy định trong CLAUDE.md, mục "Progress log"). Session mới đọc file này trước, không đọc lại cả project.

## Hiện trạng

- Đang ở: Giai đoạn 2. Kế hoạch ở `docs/superpowers/plans/2026-11-08-phase2-login-payment.md` (Task 6–10).
- Việc tiếp theo: Task 9 (lịch sử giao dịch), chờ Shin đồng ý. Shin muốn được hỏi trước mỗi task và mỗi quyết định.
- Vấn đề đang mở: link tunnel `trycloudflare.com` đổi mỗi lần chạy `cloudflared`, phải sửa URL webhook trên SePay; Task 10 dùng `V5__scan_owner.sql` (V4 đã dùng cho SePay); xoá cây/bệnh đang được tham chiếu mới test bằng mock; font tải từ Google Fonts; chưa có favicon.

## Quyết định quan trọng

- Spring Boot 4.1.2 + Thymeleaf (layout dialect) + Tailwind v4 + DaisyUI v5, render phía server, không React/JS library mới.
- Package gốc `com.son.leafcare`. Test chạy trên H2, không đụng PostgreSQL thật.
- CI (GitHub Actions) chạy `npm run build` + `./mvnw verify` mỗi push/PR; test fail là build fail.
- Đăng nhập: Spring Security + Google OIDC; `authentication.getName()` là email (tra `AppUser` theo email); ADMIN = email trong `LEAFCARE_ADMIN_EMAILS`. Test controller cần `@Import(SecurityConfig.class)` + `@MockitoBean AppUserService`.
- Thiết kế: hướng "Phòng khám cây" (xanh lâm sàng #0B7A4B trên nền trắng men #F4F8F5, font Be Vietnam Pro, nút "Quét lá" luôn trong tầm ngón cái). Chi tiết ở `DESIGN.md`, `PRODUCT.md`, `.impeccable/surfaces/`.
- Thanh điều hướng đánh dấu mục đang mở bằng model attribute `currentPath` (`GlobalModelAdvice`), vì Thymeleaf 3.1 không còn `#request`.
- Test pager dùng `SpringTemplateEngine` + `WebContext` (link `@{/...}` cần web context).
- Thanh toán dùng SePay (QR VietQR chuyển khoản vào tài khoản cá nhân, webhook xác thực bằng API key), thay MoMo sandbox vì thẻ ATM test của MoMo bị từ chối. Code MoMo giữ ở `service/momo/` nhưng không dùng. Trạng thái đơn chỉ đổi qua webhook.
- UI dùng skill trong `.claude/skills/`: impeccable, emil-design-eng, animate, animation-performance, break-ui, review-animations.
- DB: Flyway quản lý schema (`db/migration`), Hibernate `ddl-auto=validate`; file đã merge không sửa, thêm V mới. Dữ liệu mẫu ở `db/seed/V1_1__seed_data.sql` (chỉ app chạy, test không). `baseline-on-migrate=true` để DB cũ tạo bằng `update` nhận là V1.
- Mẫu CRUD Task 3 phải copy từ Plant: id lạ -> `NotFoundException` (404); trùng tên -> `bindingResult.rejectValue("name","duplicate","Tên đã tồn tại")`; xoá khi đang được tham chiếu -> `InUseException` -> flash `error`; `confirm()` khi xoá; `@Size(max=255)` cho chuỗi ngắn.

## Nhật ký (mới nhất ở trên)

### 2026-11-08 – Thanh toán QR ngân hàng qua SePay (ngoài plan)
- Lý do: thẻ ATM test của MoMo sandbox bị "nhà phát hành từ chối"; Shin chọn thay MoMo bằng SePay (QR thật, tài khoản cá nhân).
- Đã làm: `POST /payments/premium` tạo đơn PENDING rồi mở `/payments/{orderId}` (VietQR `vietqr.app/img`, thông tin tài khoản, tự tải lại 5s); `POST /payments/sepay/webhook` (công khai, bỏ CSRF, header `Authorization: Apikey`, trả `{"success":true}`); `PaymentService.handleWebhook` lấy mã đơn từ `code` hoặc `LC`+17 số trong nội dung, chỉ tiền vào, thiếu tiền giữ PENDING, thừa vẫn nhận; `V4__payment_sepay.sql` đổi `momo_trans_id` -> `trans_id`. Bỏ endpoint MoMo; README hướng dẫn SePay.
- Test: `PaymentServiceTest` (12), `PaymentControllerTest` (9); 150 test xanh.
- 08/11: Shin thử thật thành công (MBBank cá nhân, webhook API Key qua `cloudflared tunnel`, 2.000đ -> Premium đến 08/12/2026). Vietcombank cá nhân SePay chưa hỗ trợ.
- Env mới: `SEPAY_BANK`, `SEPAY_ACCOUNT`, `SEPAY_ACCOUNT_NAME`, `SEPAY_API_KEY`, `LEAFCARE_PREMIUM_PRICE`. Nhánh: `feat/sepay-qr`.

### 2026-11-08 – MoMo trả bằng thẻ ATM test (ngoài plan)
- Lý do: QR sandbox chỉ quét được bằng app MoMo bản test; Shin muốn nhập thẻ ngân hàng giả để demo.
- Đã làm: `momo.request-type` (env `MOMO_REQUEST_TYPE`, mặc định `payWithATM`; `captureWallet` = QR), dùng trong chữ ký và body `create`; thêm 1 test; README ghi thẻ test `9704 0000 0000 0018`.
- Sau này Shin muốn ngân hàng/QR thật: cần tài khoản MoMo doanh nghiệp production (đăng ký kinh doanh), chỉ đổi env, không đổi code. Nhánh: `fix/momo-atm-test-card`.

### 2026-11-08 – Task 8: MoMo sandbox
- Đã làm: `MomoClient` (create/query captureWallet, ký HMAC-SHA256, kiểm chữ ký IPN, RestClient timeout 30s), `PaymentService` (`startPremium`, `handleIpn`, `refresh`; khoá dòng `PESSIMISTIC_WRITE` để IPN và trang kết quả không kích hoạt 2 lần), `PaymentController` (`/payments/checkout`, `POST /payments/momo`, `/payments/momo/return`, `POST /payments/momo/ipn` trả 204/400, `/payments/{orderId}`), 2 template `payments/*`; IPN công khai và bỏ CSRF trong `SecurityConfig`.
- Test: `MomoClientTest` (9, chữ ký so với giá trị tính bằng Python), `PaymentServiceTest` (13), `PaymentControllerTest` (11).
- Quyết định: Shin chọn key test công khai của MoMo (env `MOMO_*`); orderId = `LC` + millis + 4 số; resultCode 0 = thành công, 1000/7000/7002 = còn chờ, còn lại = thất bại; sai số tiền thì bỏ qua; trang kết quả chỉ đọc DB (gọi `query` khi PENDING), không tin tham số redirect. Nhánh: `feat/task-8-momo`.
- Shin hỏi dùng QR tài khoản cá nhân: không tự xác nhận được (không IPN/chữ ký); đề xuất làm task phụ sau Task 10 nếu Shin muốn.

### 2026-11-08 – Task 7: Subscription, Payment + trang chọn gói
- Đã làm: `V3__subscription_payment.sql`; entity `Subscription` (1 dòng/người, không có dòng = Free), `Payment`, enum `Plan`, `PaymentStatus`; `SubscriptionService` (`isPremium`, `premiumUntil`, `activatePremium` cộng dồn khi còn hạn); `AppUserService.current(auth)`; trang `/plans` + mục "Gói" trên navbar (điện thoại: bấm avatar).
- Test: `SubscriptionServiceTest` (5), `PlanControllerTest` (2), thêm 2 test `current` trong `AppUserServiceTest`.
- Quyết định: giá/số ngày ở `leafcare.premium.price=49000`, `leafcare.premium.days=30`; tiền là `long` VND. Nhánh: `feat/task-7-plans`.
- Shin đã thử đăng nhập Google thật trên PostgreSQL: chạy được (Task 6 xong).

### 2026-11-08 – Task 6: Google login, USER/ADMIN
- Đã làm: `spring-boot-starter-security-oauth2-client` + `thymeleaf-extras-springsecurity6`; `V2__users.sql` + `AppUser`/`Role`/`AppUserService.upsertFromGoogle`; `SecurityConfig` (OIDC user service gắn ROLE_USER/ROLE_ADMIN, principal name = email); trang `/login`, `error/403.html`; navbar avatar/tên/Đăng xuất, ẩn nav khi chưa đăng nhập; nút Thêm/Sửa/Xoá cây, bệnh chỉ hiện cho ADMIN.
- Test: `SecurityConfigTest` (10), `AppUserServiceTest` (4); test controller cũ thêm `@Import(SecurityConfig.class)`, `@WithMockUser(roles="ADMIN")`, `.with(csrf())`; `ErrorPageTest` mở `/plants/**` bằng filter chain riêng trong test. 110 test xanh.
- Quyết định: chỉ `/login`, CSS, trang lỗi là công khai (`/` cũng cần đăng nhập vì có ảnh quét); ADMIN theo `LEAFCARE_ADMIN_EMAILS`, xét lại mỗi lần đăng nhập; client id mặc định `chua-cau-hinh` để app vẫn chạy khi chưa có env. Thông tin navbar lấy từ model `me` (`GlobalModelAdvice`). Nhánh: `feat/task-6-google-login` (tách từ `docs/phase2-plan`).
- File: `pom.xml`, 2 `application.properties`, `README.md` (hướng dẫn Google OAuth), 8 file main mới, 2 template mới, `base.html`, 2 `list.html`, 6 test.

### 2026-11-08 – Kế hoạch Giai đoạn 2 (ngoài plan)
- Đã làm: viết plan Giai đoạn 2, chia 5 task (6 Google login, 7 gói + entity, 8 MoMo, 9 lịch sử giao dịch, 10 giới hạn Free).
- Quyết định: ADMIN theo danh sách email env `LEAFCARE_ADMIN_EMAILS`; khoá bí mật chỉ qua env; chạy local không có IPN thì trang kết quả gọi API `query` của MoMo; lượt quét gắn theo người dùng (V4). Nhánh: `docs/phase2-plan`.
- File: `docs/superpowers/plans/2026-11-08-phase2-login-payment.md` (mới), `docs/PROGRESS.md`.

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

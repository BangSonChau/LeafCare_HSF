# LeafCare – Nhật ký tiến độ

> Claude cập nhật file này sau mỗi task/commit (quy định trong CLAUDE.md, mục "Progress log"). Session mới đọc file này trước, không đọc lại cả project.

## Hiện trạng

- Đang ở: Giai đoạn 2 xong; RAG backend đã merge (PR #27), trang Hỏi đáp `/chat` ở nhánh `feat/rag-chat-page`; tiếp theo trang Tài liệu RAG cho ADMIN với giao diện sidebar (Shin muốn kiểu ảnh mẫu: sidebar tối, thanh tìm kiếm, card trắng bo góc). Kế hoạch ở `docs/superpowers/plans/2026-11-08-phase2-login-payment.md` (Task 6–12).
- Việc tiếp theo: tài liệu Assignment đã có bản nháp (`docs/assignment/`); nhóm cần chụp ảnh prompt thật và điền tên thành viên. Giai đoạn 3 (AI) Shin tạm hoãn. Shin muốn được hỏi trước mỗi task và mỗi quyết định.
- Vấn đề đang mở: ảnh `/uploads/**` vẫn mở được bởi mọi người đã đăng nhập nếu biết tên file (tên UUID, khó đoán); xoá cây/bệnh đang được tham chiếu mới test bằng mock; font tải từ Google Fonts; chưa có favicon.

## Quyết định quan trọng

- Ảnh upload lưu trong DB (bảng `image_file`, `FileStorageService`), không lưu ra đĩa, vì Render Free xoá đĩa.
- Spring Boot 4.1.2 + Thymeleaf (layout dialect) + Tailwind v4 + DaisyUI v5, render phía server, không React/JS library mới.
- Package gốc `com.son.leafcare`. Test chạy trên H2, không đụng PostgreSQL thật.
- CI (GitHub Actions) chạy `npm run build` + `./mvnw verify` mỗi push/PR; test fail là build fail.
- Đăng nhập: Spring Security + Google OIDC; `authentication.getName()` là email (tra `AppUser` theo email); ADMIN = email trong `LEAFCARE_ADMIN_EMAILS`. Test controller cần `@Import(SecurityConfig.class)` + `@MockitoBean AppUserService`.
- Thiết kế: hướng "Phòng khám cây" (xanh lâm sàng #0B7A4B trên nền trắng men #F4F8F5, font Be Vietnam Pro, nút "Quét lá" luôn trong tầm ngón cái). Chi tiết ở `DESIGN.md`, `PRODUCT.md`, `.impeccable/surfaces/`.
- Thanh điều hướng đánh dấu mục đang mở bằng model attribute `currentPath` (`GlobalModelAdvice`), vì Thymeleaf 3.1 không còn `#request`.
- Test pager dùng `SpringTemplateEngine` + `WebContext` (link `@{/...}` cần web context).
- Thanh toán dùng SePay (QR VietQR chuyển khoản vào tài khoản cá nhân, webhook xác thực bằng API key), thay MoMo sandbox vì thẻ ATM test của MoMo bị từ chối. Code MoMo giữ ở `service/momo/` nhưng không dùng. Trạng thái đơn chỉ đổi qua webhook.
- UI dùng skill trong `.claude/skills/`: impeccable, emil-design-eng, animate, animation-performance, break-ui, review-animations.
- RAG: Spring AI + pgvector, bảng `vector_store` do Spring AI tự tạo (ngoài Flyway); Postgres local/Neon phải có pgvector; test H2 tắt vectorstore + model.
- DB: Flyway quản lý schema (`db/migration`), Hibernate `ddl-auto=validate`; file đã merge không sửa, thêm V mới. Dữ liệu mẫu ở `db/seed/V1_1__seed_data.sql` (chỉ app chạy, test không). `baseline-on-migrate=true` để DB cũ tạo bằng `update` nhận là V1.
- Mẫu CRUD Task 3 phải copy từ Plant: id lạ -> `NotFoundException` (404); trùng tên -> `bindingResult.rejectValue("name","duplicate","Tên đã tồn tại")`; xoá khi đang được tham chiếu -> `InUseException` -> flash `error`; `confirm()` khi xoá; `@Size(max=255)` cho chuỗi ngắn.

## Nhật ký (mới nhất ở trên)

### 2026-11-11 – Trang Hỏi đáp `/chat`
- Đã làm: `ChatPageController` (`GET/POST /chat`, form thường nên không cần JS/CSRF thủ công, mọi người đã đăng nhập dùng được), `chat/index.html` (ô hỏi, câu trả lời, danh sách nguồn); link "Hỏi đáp" ở thanh trên desktop và thẻ link ở trang chủ cho điện thoại (thanh dưới đã đủ 5 mục).
- Test: `ChatPageControllerTest` (6: hiện form, trả lời + nguồn được escape, không nguồn thì ẩn khối nguồn, câu hỏi rỗng/>500 không gọi LLM, lỗi Gemini hiện thông báo thân thiện và giữ câu hỏi, thiếu CSRF bị 403); 206 test xanh. Chưa kiểm tay trên trình duyệt (máy Claude không có Gemini key/pgvector).
- Quyết định: lỗi gọi Gemini không lộ chi tiết cho người dùng, chỉ ghi log. Nhánh: `feat/rag-chat-page`.

### 2026-11-11 – Ghép RAG chatbot (Giai đoạn 3, bước 1)
- Đã làm: thêm Spring AI 2.0.2 (Gemini chat + embedding, pgvector, Tika) vào `pom.xml`; `RagDocumentService` (đọc file -> chia 400 token -> embedding -> lưu), `RagChatService` (top 4 đoạn, ngưỡng 0.3, trả lời tiếng Việt kèm nguồn `[1]`), record `RagAnswer`, `RagController` (`POST /api/rag/chat` cho người đăng nhập, `POST /api/rag/documents` chỉ ADMIN trong `SecurityConfig`); `compose.yaml` dùng `pgvector/pgvector:pg16`; README mục "Chatbot RAG".
- Test: 3 lớp mới (service x2, controller), test H2 tắt vectorstore/model bằng property + `RagTestBeans` (mock `VectorStore`, `ChatClient.Builder`) cho test full context; 200 test xanh. Chưa thử với Gemini key thật và Postgres pgvector thật.
- Quyết định: key chỉ đọc từ env `GEMINI_API_KEY`; model chat `gemini-3.8-flash` (2.5-flash hết hiệu lực với tài khoản mới); file rỗng trả 0 đoạn (Tika ném lỗi với file 0 byte). Chưa làm: trang chat giao diện, Admin nhập key, nối với kết quả quét lá. Nhánh: `feat/rag`.

### 2026-11-08 – Tài liệu + slide Assignment (ngoài plan)
- Đã làm: `docs/assignment/USER_GUIDE.md` (setup code, Tailwind v4 + DaisyUI v5 trong Thymeleaf, setup Claude Code, quy trình dùng AI làm back-end/UI, kịch bản demo), `LeafCare_Assignment.pptx` (8 slide, có ghi chú người nói), 9 ảnh chụp app trong `docs/assignment/images/`.
- Quyết định: Shin muốn slide ít, tập trung setup code, setup AI, dùng AI làm back-end + demo; bỏ Bootstrap trong đề, lấy stack thật. Ảnh chụp từ app chạy local với dữ liệu demo (QR VietQR không tải được nên không dùng ảnh QR).
- Còn lại: ảnh chụp prompt thật (timeline yêu cầu), tên thành viên. Nhánh: `docs/assignment-guide-slides`.

### 2026-11-08 – Lưu ảnh quét vào database (ngoài plan)
- Lý do: Shin thấy ảnh quét trên Render bị mất (gói Free xoá đĩa mỗi lần deploy/khởi động lại); Shin chọn lưu vào DB Neon thay vì Cloudinary.
- Đã làm: `V6__image_file.sql` (bảng `image_file`: name PK, content_type, data BYTEA), entity `ImageFile`, `ImageFileRepository`; `FileStorageService` giữ nguyên kiểm tra (đuôi, content-type, <=5 MB, tên UUID) nhưng ghi/xoá trong DB; `ImageController` phục vụ `GET /uploads/{name}` (cache 1 năm, private, 404 khi không có); bỏ `WebConfig` và `leafcare.upload-dir`. Ảnh và dòng quét chung 1 transaction nên lỗi thì cả hai cùng rollback.
- Test: `FileStorageServiceTest` viết lại bằng `@DataJpaTest` (11), `ImageControllerTest` (3), `ScanHistoryServiceTest` đếm ảnh trong DB; chạy thêm trên PostgreSQL thật (cột `bytea`): qua. 190 test xanh.
- Còn lại: ảnh đã mất trên Render và ảnh cũ trong thư mục `uploads/` ở máy Shin không được chuyển vào DB (hiện ảnh lỗi). Nhánh: `fix/store-images-in-db`.

### 2026-11-08 – Task 12: Dashboard quản trị
- Đã làm: `/admin` (chỉ ADMIN, chặn trong `SecurityConfig`): 4 thẻ (người dùng + mới 7 ngày, Premium còn hạn, doanh thu tổng + tháng này, lượt quét hôm nay + 7 ngày), biểu đồ cột 14 ngày bằng HTML/CSS (có danh sách ẩn cho trình đọc màn hình), 5 giao dịch mới nhất, 5 người quét nhiều nhất; `/admin/users` (tìm theo email/tên, gói + hạn Premium, số lượt quét, ngày tham gia, 20 dòng/trang, pager chung). Menu desktop thêm "Quản trị"; điện thoại: nút "Trang quản trị" trên trang chủ ADMIN.
- Code: `AdminService` + record `AdminStats`, `DayCount`, `TopScanner`, `UserRow`; `AdminController`; query mới ở 4 repository (đếm/tổng tiền bằng JPQL, top người quét lấy id + count rồi `findAllById` để chạy được cả H2 lẫn PostgreSQL).
- Test: `AdminServiceTest` (9, chạy thêm trên PostgreSQL thật: qua), `AdminControllerTest` (5), `SecurityConfigTest`/`HomeControllerTest` thêm assert; 191 test xanh. Kiểm tay 390/1280 (số rất lớn, email dài, 20 dòng, trống): không tràn ngang.
- Quyết định: Shin chọn trang riêng `/admin` (không thay trang chủ), số liệu và trang người dùng theo đề xuất; ngày tính theo giờ Việt Nam như Task 10; doanh thu = tổng đơn SUCCESS. Cập nhật `docs/TIMELINE.md` (SePay, Render, dashboard, Giai đoạn 2 xong). Nhánh: `feat/task-12-admin-dashboard`.

### 2026-11-08 – Task 11: Deploy lên Render (phần code)
- Đã làm: `Dockerfile` (build Maven JDK 17, chạy JRE 17, `MaxRAMPercentage=75` cho 512 MB), `.dockerignore`; `application.properties` đọc `DB_URL`, `DB_USERNAME`, `PORT`, `SHOW_SQL` (mặc định vẫn như local), `server.forward-headers-strategy=framework` để redirect URI Google là https sau proxy Render; README mục "Deploy lên Render".
- Kiểm: jar chạy với PostgreSQL tạm + `PORT=9091`: Flyway áp 6 migration, `/login` 200, redirect URI = `https://leafcare.onrender.com/...` khi có `X-Forwarded-*`. Không build được image Docker ở máy Claude (không có Docker daemon), Render sẽ build lần đầu. 175 test xanh.
- 08/11: Shin deploy xong: https://leafcare-4nh6.onrender.com (Render Free, Singapore, auto-deploy từ `main`), DB Neon Singapore; đăng nhập Google và thanh toán SePay chạy thật. Webhook SePay trỏ về Render.
- Quyết định: DB dùng Neon (Singapore) thay PostgreSQL free của Render (hết hạn); ảnh upload mất khi deploy lại (chấp nhận cho demo). Shin tự làm phần tạo tài khoản theo hướng dẫn. Nhánh: `feat/task-11-render`.

### 2026-11-08 – ADMIN thấy người quét (ngoài plan)
- Lý do: Shin thấy ADMIN xem được mọi lượt quét nhưng không biết của tài khoản nào.
- Đã làm: danh sách và trang chi tiết lượt quét hiện "Người quét: tên · email" chỉ cho ADMIN (`sec:authorize`); lượt cũ không có chủ hiện "Chưa rõ"; fragment `scans/list :: owner`; `ScanHistoryRepository.findAll(Pageable)` thêm `@EntityGraph("owner")` để không N+1.
- Test: `ScanControllerTest` +4 (ADMIN thấy tên/email/"Chưa rõ", USER không thấy, ở cả list và detail).
- Lọc theo người dùng, thống kê theo tài khoản để cho Task 12 (dashboard admin). Nhánh: `fix/admin-sees-scan-owner`.

### 2026-11-08 – Task 10: Lượt quét theo người dùng + giới hạn Free
- Đã làm: `V5__scan_owner.sql` (`scan_history.user_id` null được, FK, index), `ScanHistory.owner`; `ScanHistoryService` nhận `AppUser`: `remainingToday` (null = không giới hạn cho Premium/ADMIN), `create` chặn lượt thứ 6 **trước** khi lưu file (`ScanLimitException`), `list/get/delete` chỉ của mình (người khác hoặc dòng không chủ -> 404), ADMIN thấy hết; `DashboardService.stats(user)` (số cây/bệnh chung, lượt quét của mình; ADMIN tính tất cả).
- Giao diện: trang Quét lá hiện "Còn X/5 lượt hôm nay (gói Free)"; hết lượt thì hộp cảnh báo + nút "Nâng cấp Premium", nút Lưu ảnh bị khoá. Kiểm tay 390: không tràn ngang.
- Test: `ScanHistoryServiceTest` (+8: chủ sở hữu, 5 lượt rồi chặn không ghi file, đếm lùi, hôm qua không tính, người khác không tính, Premium/ADMIN không giới hạn, chỉ thấy/xoá của mình), `ScanControllerTest` (+2), `DashboardServiceTest` (+1); 167 test xanh.
- Quyết định: ngày tính theo `Asia/Ho_Chi_Minh` đổi sang giờ máy chủ (vì `createdAt` lưu giờ máy chủ, Render chạy UTC); hai lần upload cùng lúc có thể lọt lượt thứ 6 (ghi `shortcut:` trong code). Lượt quét cũ không có chủ: chỉ ADMIN thấy. Nhánh: `feat/task-10-free-limit`.

### 2026-11-08 – Task 9: Lịch sử giao dịch
- Đã làm: `PaymentRepository.findByUser(user, Pageable)`, `PaymentService.history(user, page)` (10 dòng/trang, mới nhất trên, trang quá cuối -> trang cuối như Plant), `GET /payments` -> `payments/history.html` (mã đơn, gói, thời gian, số tiền, badge trạng thái, bấm vào mở trang đơn; trống thì nút "Xem các gói"; pager chung), link "Lịch sử giao dịch" ở trang Gói.
- Test: thêm 3 test `PaymentServiceTest` (chỉ đơn của mình, phân trang/clamp, rỗng), 3 test `PaymentControllerTest`, 1 assert `PlanControllerTest`; 156 test xanh. Kiểm tay 390/1280 (10 dòng, số tiền 499.000.000 ₫, trống): không tràn ngang.
- Quyết định: nhãn PENDING đổi "Đang xử lý" -> "Chờ thanh toán" (với QR chuyển khoản, đơn chưa trả tiền nằm ở trạng thái này). Nhánh: `feat/task-9-payment-history`.

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

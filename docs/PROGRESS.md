# LeafCare – Nhật ký tiến độ

> Claude cập nhật file này sau mỗi task/commit (quy định trong CLAUDE.md, mục "Progress log"). Session mới đọc file này trước, không đọc lại cả project.

## Hiện trạng

- Đang ở: Giai đoạn 3 – quét lá bằng model: #32 (package theo feature) và #33 (nút Phân tích) đã merge, Shin thử trên máy chạy đúng (đốm vòng cà chua 97%). PR `feat/scan-service-render` đưa service ONNX vào `scan-service/` chờ merge.
- Việc tiếp theo: deploy `scan-service` thành Web Service Free thứ hai trên Render, đặt `SCAN_SERVICE_URL` cho LeafCare; sửa giờ hiển thị trên Render (đang là UTC); hiện top 3, hướng xử lý từ RAG. Shin muốn được hỏi trước mỗi task và mỗi quyết định.
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
- Thanh toán dùng SePay (QR VietQR chuyển khoản vào tài khoản cá nhân, webhook xác thực bằng API key), thay MoMo sandbox vì thẻ ATM test của MoMo bị từ chối. Code MoMo giữ ở `payment/momo/` nhưng không dùng. Trạng thái đơn chỉ đổi qua webhook.
- UI dùng skill trong `.claude/skills/`: impeccable, emil-design-eng, animate, animation-performance, break-ui, review-animations.
- RAG: Spring AI + pgvector, bảng `vector_store` do Spring AI tự tạo (ngoài Flyway); Postgres local/Neon phải có pgvector; test H2 tắt vectorstore + model.
- DB: Flyway quản lý schema (`db/migration`), Hibernate `ddl-auto=validate`; file đã merge không sửa, thêm V mới. Dữ liệu mẫu ở `db/seed/V1_1__seed_data.sql` (chỉ app chạy, test không). `baseline-on-migrate=true` để DB cũ tạo bằng `update` nhận là V1.
- Model quét lá chạy bằng ONNX Runtime trong `scan-service/` (Render Free, ~330 MB RAM), không dùng PyTorch; `model.onnx` để ở Hugging Face model repo `shin0110/leafcare-scan` vì Space Docker/Gradio của Hugging Face giờ phải trả phí và GitHub chặn file >100 MB.
- Code chia package theo feature (`plant`, `disease`, `scan`, `user`, `plan`, `payment`, `rag`, `admin`, `home`, `common`, `config`), không chia theo tầng nữa; test theo cùng package.
- Mẫu CRUD Task 3 phải copy từ Plant: id lạ -> `NotFoundException` (404); trùng tên -> `bindingResult.rejectValue("name","duplicate","Tên đã tồn tại")`; xoá khi đang được tham chiếu -> `InUseException` -> flash `error`; `confirm()` khi xoá; `@Size(max=255)` cho chuỗi ngắn.

## Nhật ký (mới nhất ở trên)

### 2026-11-13 – Service quét lá bản ONNX cho Render Free (ngoài plan)
- Lý do: Hugging Face giờ thu phí Space Docker và Gradio; service PyTorch cần ~930 MB RAM, vượt 512 MB của Render Free.
- Đã làm: xuất model sang ONNX (so với PyTorch: cùng nhãn, xác suất lệch <1e-6); `scan-service/` (`main.py` FastAPI + onnxruntime, `labels.py` 39 nhãn, `requirements.txt`, `.python-version` 3.12), API `/predict`, `/health` giữ nguyên nên LeafCare không đổi code. Đo RSS ~330 MB.
- README: cách chạy local và cấu hình Web Service Render (build tải `model.onnx` từ Hugging Face). Không có test tự động cho Python (CI chỉ chạy Maven); đã thử tay `/predict` ra đúng nhãn mẫu.
- Nhánh: `feat/scan-service-render`.

### 2026-11-12 – Nút "Phân tích" ảnh lá bằng model (Giai đoạn 3)
- Đã làm: `V7__scan_model_label.sql` (`disease.model_label` unique, `scan_history.model_label`), seed `V7_1` (thêm Khoai tây, Ớt chuông, Ngô và 15 bệnh có nhãn model; gắn nhãn cho "Bệnh mốc sương" cà chua có sẵn); `ScanClient` (RestClient multipart `POST /predict`, timeout 60s); `ScanHistoryService.analyze` (khớp bệnh theo nhãn, gán cây; lỗi service -> `FAILED`, không giữ transaction khi chờ model); `POST /scans/{id}/analyze`; trang chi tiết hiện bệnh, cây, độ tin cậy, cảnh báo khi < 60%, triệu chứng, cách xử lý, câu "chỉ mang tính tham khảo"; nút Phân tích / Phân tích lại.
- Sửa kèm: xoá bệnh đang có trong kết quả quét -> `InUseException` + flash lỗi (trước đây sẽ lỗi FK); sửa bệnh qua form giữ nguyên `model_label`.
- Test: `ScanClientTest` (3), `ScanHistoryServiceTest` (+6), `ScanControllerTest` (+9), `DiseaseRepositoryTest`/`DiseaseServiceDbTest`/`DiseaseControllerTest` (+5); 246 test xanh. Seed chạy thử trên PostgreSQL 16 (2 lần, không trùng); 15 nhãn khớp đúng `CNN.py`. Kiểm giao diện 390/1280 bằng HTML render từ test: không tràn ngang.
- Quyết định: Shin chọn hỗ trợ 4 cây trước và nút Phân tích riêng (không chạy khi upload); nội dung triệu chứng/cách xử lý là kiến thức chung, cần đối chiếu cẩm nang; service model chạy riêng (env `SCAN_SERVICE_URL`), chưa deploy. Chưa làm: hiện top 3, hướng xử lý từ RAG, sửa `model_label` trên giao diện. Nhánh: `feat/scan-analyze` (tách từ `refactor/package-by-feature`).

### 2026-11-12 – Tái cấu trúc package theo feature (ngoài plan)
- Lý do: Shin muốn folder chia theo chức năng thay vì theo tầng (controller/service/entity/repository/exception), trước khi ghép model quét lá.
- Đã làm: chuyển 55 lớp main + 31 lớp test (`git mv`, giữ lịch sử) sang `plant`, `disease`, `scan`, `user`, `plan`, `payment`, `payment.momo`, `rag`, `admin`, `home`, `common`; sửa package/import; JPQL trong `PaymentRepository` đổi tên đầy đủ của `PaymentStatus`; `ScanHistoryService.startOfVietnamDay()` thành `public` vì `AdminService` giờ khác package. Template, SQL, cấu hình không đổi.
- Test: 223 test xanh, bằng đúng số trước khi chuyển (`./mvnw verify`). Không đổi hành vi.
- File: 89 file (chủ yếu đổi tên), `CLAUDE.md` (mục Notes mô tả package), `README.md` (đường dẫn MoMo). Nhánh: `refactor/package-by-feature`.

### 2026-11-11 – Job nạp tài liệu không còn kẹt ở "Đang đọc file"
- Lý do: Shin upload cẩm nang PDF 210 trang sau #30, trạng thái đứng ở "Đang đọc file". `RagIngestJobs` chỉ bắt `Exception`, nên `OutOfMemoryError` (Render Free 512 MB, Tika đọc PDF lớn) làm luồng nền chết im lặng và job kẹt mãi.
- Đã làm: bắt `Throwable`, báo "Hết bộ nhớ..." rõ ràng khi OOM; mỗi job có thời gian đã chạy (dừng đếm khi xong hoặc lỗi), hiện trên `/admin/documents`.
- Test: +2 (`RagIngestJobsTest`: OOM -> FAILED, thời gian dừng đếm), assert "Đã chạy" ở controller test; 225 test xanh. Chưa thử PDF thật trên Render; nếu server bị Render kill vì vượt RAM thì danh sách job mất hẳn (xem Logs Render).
- Cách tránh: nạp bản `.txt` đã trích chữ thay vì PDF (Tika đọc txt tức thì, ít RAM). Nhánh: `fix/rag-ingest-failure-state`.

### 2026-11-11 – Nạp tài liệu lớn (20 MB, theo lô, ở nền)
- Lý do: Shin upload cẩm nang bệnh cây (PDF 210 trang, bản text ~390 KB, tách thử ra 398 đoạn) lên Render thì lỗi hết; giới hạn 5 MB chắc chắn chặn PDF lớn, và nạp một lần cả trăm đoạn dễ chạm timeout request/quota embedding của Gemini. Chưa có log lỗi thật từ Render.
- Đã làm: `multipart.max-file-size=20MB` (ảnh quét vẫn 5 MB vì `FileStorageService` tự kiểm); `RagDocumentService.split/store` (lô 20 đoạn, lỗi thì chờ 20s×lần rồi thử lại tối đa 5 lần, lô đã lưu được giữ); `RagIngestJobs` (nạp ở nền bằng 1 luồng, theo dõi tiến độ trong RAM, giữ 10 lần gần nhất); `/admin/documents` hiện thanh tiến độ, tự tải lại 5 giây khi đang chạy, báo lỗi file >20 MB bằng flash.
- Test: `RagDocumentServiceTest` (lô, retry, bỏ cuộc), `RagIngestJobsTest` (5), `AdminDocumentControllerTest` viết lại; 222 test xanh. Chưa thử với Gemini và Render thật.
- Quyết định: trạng thái nạp chỉ trong RAM (mất khi server khởi động lại, ghi `shortcut:`); nạp dở rồi lỗi thì upload lại sẽ lưu trùng các đoạn đã có (chưa có xoá theo tên file). Nhánh: `fix/rag-large-upload`.

### 2026-11-11 – Khung admin sidebar + trang Tài liệu RAG
- Đã làm: `layout/admin.html` (sidebar tối `bg-neutral` #14261C, thanh trên trắng có ô tìm kiếm trỏ tới `/admin/users?q=`, nội dung nền enamel; điện thoại dùng DaisyUI drawer, không JS); Dashboard và Người dùng chuyển sang khung này (đổi `layout:decorate`, bỏ link "← Quản trị" và nút "Người dùng" thừa); trang `/admin/documents` (form upload, flash thành công/lỗi) + `AdminDocumentController`.
- Test: `AdminDocumentControllerTest` (7: form, upload ok, file không có chữ, file rỗng không gọi service, lỗi ingest, USER bị 403, thiếu CSRF), `AdminControllerTest` thêm assert sidebar; 214 test xanh. Kiểm bằng Playwright 1280/390 (không tràn ngang, drawer mở được); chưa kiểm trên app chạy thật.
- Quyết định: Shin muốn giao diện admin kiểu ảnh mẫu nhưng cùng màu xanh LeafCare, áp dụng cả khu `/admin`. Skill `.claude/skills` (impeccable...) không có trong repo (chỉ có trên máy Shin), nên theo `DESIGN.md`/`PRODUCT.md` bằng tay. File upload tối đa 5 MB (giới hạn chung của app). Chưa làm: danh sách và xoá tài liệu đã nạp. Nhánh: `feat/admin-shell`.

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

### Cũ hơn (mỗi mục 1 dòng)
- 2026-11-08 – Task 7: Subscription, Payment + trang chọn gói (`feat/task-7-plans`)
- 2026-11-08 – Task 6: Google login, USER/ADMIN (`feat/task-6-google-login`)
- 2026-11-08 – Kế hoạch Giai đoạn 2 (ngoài plan) (`docs/phase2-plan`)
- 2026-11-08 – Cập nhật timeline (ngoài plan)
- 2026-11-07 – Flyway migration (ngoài plan) (`chore/flyway`)
- 2026-11-08 – Task 5: Dashboard (`feat/task-5-dashboard`)
- 2026-11-07 – Task 4: Scan upload và lịch sử (`feat/task-4-scan-upload`)
- 2026-11-07 – Task 3: Disease CRUD (`feat/task-3-disease-crud`)
- 2026-11-07 – Task 2: Plant CRUD (`feat/task-2-plant-crud`)
- 2026-11-07 – Task 1: layout, theme, pager (`feat/task-1-layout`)
- 2026-11-07 – Setup trước Phase 1

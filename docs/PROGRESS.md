# LeafCare – Nhật ký tiến độ

> Claude cập nhật file này sau mỗi task/commit (quy định trong CLAUDE.md, mục "Progress log"). Session mới đọc file này trước, không đọc lại cả project.

## Hiện trạng

- Đang ở: Hỏi đáp RAG chạy ổn trên Render (#38–#44 đã merge). Env Render: `GEMINI_CHAT_MODEL=gemini-3.5-flash-lite`, `GEMINI_FALLBACK_MODEL=gemini-flash-lite-latest`, `GEMINI_TIMEOUT=60s`, key mới. PR `feat/answer-format-scan-wake` (timeline, format câu trả lời, quét lá tự thử lại khi service ngủ) chờ merge.
- Việc tiếp theo: trang Admin nhập API key + chọn nhà cung cấp (Shin đồng ý làm, hỏi thiết kế trước). Shin tự đổi model mặc định trong `application.properties` (đã hướng dẫn). **Nhắc Shin xoá key Gemini đã lộ trong ảnh chụp** (13/11). Shin muốn được hỏi trước mỗi task và mỗi quyết định.
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
- RAG: Spring AI + pgvector, bảng `vector_store` và `rag_answer_cache` (semantic cache) do Spring AI tự tạo (ngoài Flyway); gọi Gemini qua Resilience4j (timeout, cầu dao, model dự phòng), trả lời streaming SSE; Postgres local/Neon phải có pgvector; test H2 tắt vectorstore + model.
- DB: Flyway quản lý schema (`db/migration`), Hibernate `ddl-auto=validate`; file đã merge không sửa, thêm V mới. Dữ liệu mẫu ở `db/seed/V1_1__seed_data.sql` (chỉ app chạy, test không). `baseline-on-migrate=true` để DB cũ tạo bằng `update` nhận là V1.
- Model quét lá chạy bằng ONNX Runtime trong `scan-service/` (Render Free, ~330 MB RAM), không dùng PyTorch; `model.onnx` để ở Hugging Face model repo `shin0110/leafcare-scan` vì Space Docker/Gradio của Hugging Face giờ phải trả phí và GitHub chặn file >100 MB.
- Code chia package theo feature (`plant`, `disease`, `scan`, `user`, `plan`, `payment`, `rag`, `admin`, `home`, `common`, `config`), không chia theo tầng nữa; test theo cùng package.
- Mẫu CRUD Task 3 phải copy từ Plant: id lạ -> `NotFoundException` (404); trùng tên -> `bindingResult.rejectValue("name","duplicate","Tên đã tồn tại")`; xoá khi đang được tham chiếu -> `InUseException` -> flash `error`; `confirm()` khi xoá; `@Size(max=255)` cho chuỗi ngắn.

## Nhật ký (mới nhất ở trên)

### 2026-11-13 – Timeline, format câu trả lời AI, quét lá chờ service thức dậy (ngoài plan)
- Lý do: Shin chọn bỏ vision API (dùng model ONNX), cập nhật timeline, format câu trả lời (hiện `*` thô), quét lá lỗi 502 khi service Render đang ngủ.
- Đã làm: `TIMELINE.md` (vision API bỏ; model, RAG, nối chat với quét: xong 13/11; còn trang Admin API key). `RagChatService.toHtml` + JS trong `chat/index.html` cùng định dạng: tiêu đề `#`, gạch đầu dòng `*`/`-`, danh sách số (`<ol start>`), in đậm, nghiêng; CSS `.answer` trong `input.css`; prompt yêu cầu tiêu đề in đậm + gạch đầu dòng. `ScanClient.predict` thử lại 502/503/504 tối đa 4 lần, cách 15s; nút Phân tích báo "1–2 phút".
- Test: `RagChatServiceTest` +1 (định dạng), `ScanClientTest` +2 (thức dậy, bỏ cuộc sau 4 lần); 304 test xanh. JS cho kết quả giống hệt Java (chạy bằng node), Playwright 390px với server SSE giả: danh sách hiện đúng, không tràn ngang. Chưa thử với Gemini/Render thật.
- Nhánh: `feat/answer-format-scan-wake`.

### 2026-11-13 – Tìm ra Gemini treo do model `gemini-3.8-flash` qua API (ngoài plan)
- Lý do: sau #41–#43 trang vẫn báo quá tải; log `TimeoutException ... 60000ms`, đổi key và temperature không đỡ. Shin chạy curl `generateContent` từ máy: `gemini-3.8-flash` đứng rất lâu (trong AI Studio vẫn nhanh), `gemini-3.5-flash-lite` trả lời 1,7s mã 200.
- Đã làm: Shin đặt env Render `GEMINI_CHAT_MODEL=gemini-3.5-flash-lite`, `GEMINI_FALLBACK_MODEL=gemini-flash-lite-latest`; app trả lời được, câu hỏi cùng ý lấy từ cache ngay. Không sửa code. Nhánh: `docs/progress-gemini-model`.

### 2026-11-13 – Temperature Gemini về 1.0 (ngoài plan)
- Lý do: trên Render Gemini không ra chữ nào sau 60s (cả stream lẫn gọi ở nền), dù key mới và embedding vẫn chạy; trong AI Studio cùng model, cùng câu hỏi trả lời ngay. Khác biệt: app đặt `temperature=0.2`, AI Studio dùng 1.0 (Gemini 3 ẩn ô temperature, Google khuyên giữ 1.0).
- Đã làm: `spring.ai.google.genai.chat.temperature=${GEMINI_TEMPERATURE:1.0}` (đổi lại được qua env, không cần deploy code). Chưa kiểm chứng được nguyên nhân (máy Claude không gọi được Gemini).
- Nhánh: `fix/gemini-temperature`.

### 2026-11-13 – Gemini quá tải: server viết tiếp ở nền + trang tự hỏi lại (ngoài plan)
- Lý do: Shin hỏi sau khi báo "quá tải" có chạy ngầm để lưu câu trả lời không (trước đây không). Shin chọn **cả hai** cách.
- Đã làm: `RagChatService.answerLater` (1 luồng nền, thử lại sau 1, 2, 3 phút bằng `ask()`, viết được thì lưu cache; mỗi câu hỏi một lượt, tối đa 20 câu chờ, chỉ trong RAM); `/chat` và `/chat/stream` gọi khi quá tải. Trang Hỏi đáp đếm ngược 30 giây rồi tự hỏi lại, tối đa 2 lần (bấm Hỏi tay thì đếm lại từ đầu); câu báo quá tải nói rõ đang viết ở nền.
- Test: `RagChatServiceTest` +2 (thử lại đến khi lưu cache; đúng 3 lần, bấm 2 lần vẫn 1 lượt), `ChatPageControllerTest` thêm verify; 301 test xanh. Thử JS bằng Playwright 390px + server SSE giả (2 lần busy rồi trả lời): tự hỏi lại 2 lần, lần 3 ra chữ, không tràn ngang. Chưa thử với Gemini thật.
- Nhánh: `feat/rag-answer-later`.

### 2026-11-13 – Hỏi đáp không còn đứng mãi ở "Đang tra cẩm nang…" (ngoài plan)
- Lý do: trên Render trang `/chat` hiện nguồn rồi đứng mãi, log không có dòng `RAG chat ... failed`. Thư viện Gemini gọi HTTP ngay lúc subscribe stream (chặn luồng), nên `.timeout(25s)` chưa bắt đầu đếm.
- Đã làm: `RagChatService.streamFrom` thêm `subscribeOn(Schedulers.boundedElastic())` trước `.timeout`, Gemini chậm thì sau `GEMINI_TIMEOUT` chuyển model dự phòng hoặc báo quá tải.
- Test: `RagChatServiceTest.streamTimesOutWhenOpeningTheStreamBlocks` (đỏ trước khi sửa: chờ 11,7s không lỗi); 299 test xanh. Chưa thử với Gemini thật.
- Ghi chú: lỗi `Scan ... 502 BAD_GATEWAY` là service quét lá Render đang ngủ dậy, không phải lỗi code. Nhánh: `fix/rag-stream-timeout`.

### 2026-11-13 – Semantic cache cho Hỏi đáp RAG (ngoài plan)
- Lý do: Gemini free chậm/quá tải; câu hỏi giống nghĩa (nhất là nút "Hỏi cẩm nang", câu hỏi cố định theo bệnh) nên trả ngay. Shin chọn ngưỡng **0.92** (cosine).
- Đã làm: `AnswerCache` (PgVectorStore thứ hai, bảng `rag_answer_cache` do Spring AI tự tạo như `vector_store`, ngoài Flyway; câu hỏi = text, câu trả lời + nguồn trong metadata; tắt khi không có embedding/pgvector như test H2). `RagChatService.ask` + `/chat/stream` tra cache trước, lưu sau khi Gemini viết xong (không lưu câu "chưa đề cập" hay lúc quá tải); `RagDocumentService.store` xoá cache sau mỗi lần nạp (kể cả nạp dở). Env `RAG_CACHE_THRESHOLD`.
- Test: `AnswerCacheTest` (7), `RagChatServiceTest` +3, `ChatPageControllerTest` +2, `RagDocumentServiceTest` +1; 299 test xanh. Chạy thử `AnswerCache` trên PostgreSQL 16 + pgvector thật (embedding giả): lưu/tìm/xoá đúng, metadata danh sách nguồn đọc lại được. Chưa đo ngưỡng với embedding Gemini thật.
- Ghi chú: mỗi câu hỏi tốn thêm 1 lần gọi embedding để tra cache. Nhánh: `feat/rag-semantic-cache` (tách từ `feat/rag-chat-stream` chưa merge).

### 2026-11-13 – Hỏi đáp: chữ trả lời chạy dần + nút "Hỏi cẩm nang" mở trang ngay (ngoài plan)
- Lý do: Shin thấy bấm "Hỏi cẩm nang" phải đợi lâu mới chuyển trang; muốn chuyển ngay sang Hỏi đáp có sẵn câu hỏi.
- Đã làm: nút ở kết quả quét thành link `GET /chat?q=...` (điền sẵn, tự hỏi khi mở); `POST /chat/stream` (SSE, có CSRF): `sources` -> `token`... -> `done`, Gemini hỏng thì `busy` (vẫn có nguồn), lỗi tìm kiếm thì `error`; `RagChatService.prepare/stream` (Flux, cùng cầu dao, timeout giữa 2 đoạn chữ, model dự phòng nếu chưa ra chữ nào). JS thuần trong `chat/index.html` (fetch + đọc stream, escape rồi in đậm); không JS thì form POST `/chat` cũ vẫn chạy.
- Test: `ChatPageControllerTest` +8, `RagChatServiceTest` +3, `ScanControllerTest` sửa; 285 test xanh. Thử JS bằng Playwright 390px với server SSE giả: chữ hiện dần, `<script>` bị escape, nhánh `busy`, không tràn ngang. Chưa thử với Gemini thật.
- Nhánh: `feat/rag-chat-stream` (tách từ `fix/rag-chat-timeout` chưa merge).

### 2026-11-13 – Hỏi đáp RAG chịu lỗi khi Gemini quá tải (ngoài plan)
- Lý do: log Render cho thấy `503 This model is currently experiencing high demand`; Spring AI mặc định thử lại 10 lần, chờ tăng dần nên trang treo ~11 phút. Shin chọn hướng Streaming + Semantic cache + Resilience4j, làm từng PR.
- Đã làm: `spring.ai.retry.max-attempts=2` (chờ 2s); `RagChatService` dùng Resilience4j lõi 2.4.0 (circuitbreaker + timelimiter, không dùng starter vì app là Boot 4): mỗi model chờ tối đa `GEMINI_TIMEOUT` 25s, cầu dao 3/5 lỗi -> ngắt 60s, model dự phòng `GEMINI_FALLBACK_MODEL`, tối đa 4 lời gọi Gemini cùng lúc; hỏng hết thì `answer = null`, trang vẫn hiện đoạn cẩm nang + thông báo quá tải. `**chữ**` hiện in đậm (escape HTML trước). Báo lỗi chỉ hiện 1 lần (layout). Log lỗi quét lá chỉ ghi mã HTTP.
- Test: `RagChatServiceTest` +7 (dự phòng, hỏng hết, timeout, cầu dao mở, rút gọn lỗi, in đậm), `ChatPageControllerTest` +2; chưa thử với Gemini thật.
- Nhánh: `fix/rag-chat-timeout`.

### 2026-11-13 – Bỏ log INFO khi phân tích xong (ngoài plan)
- Lý do: Shin muốn bỏ việc số 2 của #36 sau khi deploy #36 trên Render bị huỷ (app không mở cổng sau 15 phút; dòng log không phải nguyên nhân, mới chỉ là phỏng đoán do Render Free thiếu CPU).
- Đã làm: xoá `log.info` + đo thời gian trong `ScanHistoryService.analyze`, xoá test `OutputCaptureExtension`, README chỉ còn cách xem log `/predict` và log lỗi. Log lỗi `Scan {} analysis failed` giữ nguyên.
- Test: 265 test xanh. Nhánh: `fix/remove-scan-log`.

### 2026-11-13 – Hỏi cẩm nang từ kết quả quét + log phân tích (ngoài plan)
- Lý do: Shin muốn từ bệnh vừa nhận diện hỏi luôn cẩm nang đã nạp (RAG), và muốn biết trên production lượt phân tích có chạy thành công không.
- Đã làm: trang chi tiết lượt quét (DONE, có bệnh) có form POST `/chat` với câu hỏi sẵn "Cách nhận biết và xử lý <bệnh> trên <cây>?", trả lời ngay, câu hỏi vẫn sửa được ở trang Hỏi đáp; không đổi `ChatPageController`. `ScanHistoryService.analyze` ghi `INFO Scan {id} analysed in {x.x}s: {nhãn} {%}% (chosen plant: {prefix|auto})`. README mục kiểm tra log trên Render.
- Test: +3 (`ScanControllerTest` có/không có nút, `ScanHistoryServiceTest` kiểm dòng log bằng `OutputCaptureExtension`); 266 test xanh. Chưa kiểm giao diện bằng trình duyệt.
- Nhánh: `feat/scan-ask-rag`.

### 2026-11-13 – Chọn loại cây khi quét, top 3, đủ 14 cây, giờ Việt Nam (ngoài plan)
- Lý do: Shin thấy kết quả online lệch giờ (Render chạy UTC) và muốn biết app hỗ trợ cây nào, chọn cây trước khi quét, xem top 3. Shin chọn ô chọn cây **không bắt buộc** (mặc định "Để máy tự nhận").
- Đã làm: `V8__scan_choose_plant.sql` (`plant.model_prefix` unique, `scan_history.chosen_plant_id`, `looks_like_plant_id`, `top3`); seed `V8_1` (thêm 10 cây, gắn prefix cho 14 cây, 11 bệnh còn lại); service trả `probs` đủ 39 nhãn; `analyze` chỉ xét nhãn của cây đã chọn (chuẩn hoá lại), ảnh không lá vẫn thắng, ảnh rõ là cây khác (≥50%) thì `looksLikePlant`; cây kết quả lấy từ nhãn kể cả lá khoẻ; trang Quét lá liệt kê cây hỗ trợ + ô chọn; trang kết quả hiện cây đã chọn, top 3 tiếng Việt, cảnh báo < 80%. `PlantService.save` giữ `model_prefix` khi sửa qua form. Dockerfile `-Duser.timezone=Asia/Ho_Chi_Minh`.
- Test: +17 (`ScanHistoryServiceTest`, `ScanControllerTest`, `ScanClientTest`, `PlantServiceDbTest`); 263 test xanh. Migration + seed chạy thử trên PostgreSQL 16 (seed chạy 2 lần không trùng: 14 cây có prefix, 26 bệnh có nhãn). Chưa kiểm giao diện bằng trình duyệt.
- Không sửa giờ các lượt quét cũ (DB Neon có cả dòng ghi giờ UTC từ Render lẫn giờ VN từ máy Shin). Nhánh: `feat/scan-choose-plant`.

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

### Cũ hơn (mỗi mục 1 dòng)
- 2026-11-11 – Trang Hỏi đáp `/chat` (`feat/rag-chat-page`)
- 2026-11-11 – Ghép RAG chatbot: Spring AI 2.0.2, Gemini, pgvector, `/api/rag/*` (`feat/rag`)
- 2026-11-08 – Tài liệu + slide Assignment `docs/assignment/` (ngoài plan) (`docs/assignment-guide-slides`)
- 2026-11-08 – Lưu ảnh quét vào database `image_file` (ngoài plan) (`fix/store-images-in-db`)
- 2026-11-08 – Task 12: Dashboard quản trị `/admin`, `/admin/users` (`feat/task-12-admin-dashboard`)
- 2026-11-08 – Task 11: Deploy Render (Docker, Neon) – https://leafcare-4nh6.onrender.com (`feat/task-11-render`)
- 2026-11-08 – ADMIN thấy người quét (ngoài plan) (`fix/admin-sees-scan-owner`)
- 2026-11-08 – Task 10: Lượt quét theo người dùng + giới hạn Free (`feat/task-10-free-limit`)
- 2026-11-08 – Task 9: Lịch sử giao dịch (`feat/task-9-payment-history`)
- 2026-11-08 – Thanh toán QR ngân hàng qua SePay (ngoài plan) (`feat/sepay-qr`)
- 2026-11-08 – Task 8: MoMo sandbox (`feat/task-8-momo`)
- 2026-11-08 – MoMo trả bằng thẻ ATM test (ngoài plan) (`fix/momo-atm-test-card`)
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

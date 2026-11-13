# LeafCare

Web app quản lý sức khoẻ cây trồng (đồ án cuối kỳ HSF, FPT). Người dùng quản lý danh sách cây, danh mục bệnh, và lưu lịch sử quét lá kèm ảnh chụp.

## Chức năng

- **Dashboard** (`/`): thống kê nhanh về cây, bệnh và các lượt quét.
- **Cây trồng** (`/plants`): thêm, sửa, xoá, phân trang.
- **Bệnh** (`/diseases`): thêm, sửa, xoá danh mục bệnh.
- **Lịch sử quét lá** (`/scans`): tạo lượt quét có upload ảnh (tối đa 5MB), xem chi tiết, xoá.

## Công nghệ

- Java 17, Spring Boot 4.1.2 (Spring MVC, Spring Data JPA, Bean Validation), Lombok
- Thymeleaf (layout dialect), render phía server
- Tailwind CSS v4 + DaisyUI v5
- PostgreSQL, schema quản lý bằng Flyway
- JUnit 5 + Mockito, test chạy trên H2

## Chạy trên máy

Cần: JDK 17, Node.js 22, PostgreSQL.

1. Tạo database tên `leafcare` (user mặc định `postgres`, cổng 5432).
2. Đặt mật khẩu DB qua biến môi trường `DB_PASSWORD`.
3. Build CSS và chạy app:

```bash
npm ci
npm run build          # hoặc: npm run watch khi đang sửa giao diện
export DB_PASSWORD=...  # Windows: set DB_PASSWORD=...
./mvnw spring-boot:run  # Windows: mvnw.cmd spring-boot:run
```

4. Mở http://localhost:8080, đăng nhập bằng Google (cần làm mục "Cấu hình Google OAuth" trước).

Lần chạy đầu Flyway tự tạo bảng (`db/migration`) và nạp dữ liệu mẫu (`db/seed`). Ảnh upload được lưu trong database (bảng `image_file`), mở qua `/uploads/{tên}`.

Chạy test (dùng H2, không cần PostgreSQL):

```bash
./mvnw test
```

### Cấu hình Google OAuth

Mỗi người tự tạo OAuth client của mình (không chia sẻ secret, không commit):

1. https://console.cloud.google.com → tạo project → **Google Auth Platform**.
2. **Branding**: tên app, email. **Audience**: External, thêm email của mình vào **Test users**.
3. **Clients → Create client** → Web application → **Authorized redirect URIs**: `http://localhost:8080/login/oauth2/code/google` (đường dẫn Spring Security tự tạo: `/login/oauth2/code/{registrationId}`, phải khớp từng ký tự).
4. Đặt biến môi trường (IntelliJ: Run → Edit Configurations → Environment variables):

| Biến | Ý nghĩa |
|---|---|
| `GOOGLE_CLIENT_ID` | Client ID |
| `GOOGLE_CLIENT_SECRET` | Client secret |
| `LEAFCARE_ADMIN_EMAILS` | Email được quyền ADMIN, cách nhau bằng dấu phẩy (đổi xong đăng nhập lại) |

Không đặt hai biến Google thì app vẫn chạy nhưng bấm đăng nhập sẽ lỗi `invalid_client`.

### Cấu hình thanh toán SePay (QR ngân hàng)

Thanh toán Premium bằng chuyển khoản: trang `/payments/{orderId}` hiện mã VietQR của tài khoản ngân hàng bạn (số tiền + nội dung = mã đơn `LC...`). Khi tiền về, SePay gọi webhook `POST /payments/sepay/webhook`, app tìm mã đơn trong nội dung, kiểm số tiền rồi bật Premium. Đây là **tiền thật** vào tài khoản của bạn.

1. Đăng ký https://my.sepay.vn, liên kết tài khoản ngân hàng.
2. (Nên làm) Công ty → Cấu hình chung → Cấu trúc mã thanh toán: tiền tố `LC`, phần sau là 17 chữ số. Không đặt cũng được, app tự tìm `LC` + 17 số trong nội dung.
3. Tích hợp Webhook → Thêm webhook: URL `https://<địa chỉ công khai>/payments/sepay/webhook`, kiểu xác thực **API Key**, giao dịch tiền vào. Chạy local thì cần tunnel, ví dụ `cloudflared tunnel --url http://localhost:8080` hoặc `ngrok http 8080`.
4. Biến môi trường:

| Biến | Ý nghĩa |
|---|---|
| `SEPAY_BANK` | Tên ngắn ngân hàng theo https://vietqr.app/banks.json, ví dụ `MBBank`, `Vietcombank` |
| `SEPAY_ACCOUNT` | Số tài khoản nhận tiền |
| `SEPAY_ACCOUNT_NAME` | (tuỳ chọn) Tên chủ tài khoản, hiện dưới mã QR |
| `SEPAY_API_KEY` | API key đặt ở bước 3 (SePay gửi header `Authorization: Apikey <key>`) |
| `LEAFCARE_PREMIUM_PRICE` | (tuỳ chọn) Giá Premium, mặc định 49000; đặt `2000` khi thử bằng tiền thật |

Code MoMo sandbox cũ vẫn còn ở `payment/momo/` (không còn nút trên giao diện).

### Chatbot RAG (hỏi đáp theo tài liệu bệnh lá)

- Cần Postgres có extension **pgvector**: chạy `docker compose up -d` (image `pgvector/pgvector:pg16`, cổng 5432) thay cho Postgres cũ. Neon có sẵn pgvector.
- Lấy key ở https://aistudio.google.com/apikey, đặt biến môi trường `GEMINI_API_KEY` (không ghi vào file). Đổi model chat bằng `GEMINI_CHAT_MODEL` (mặc định `gemini-3.8-flash`).
- Khi Gemini quá tải (503): mỗi model chờ tối đa `GEMINI_TIMEOUT` (mặc định `25s`), thử lại 1 lần; 3/5 lần gần nhất lỗi thì tạm ngừng gọi model chính 60 giây. Đặt `GEMINI_FALLBACK_MODEL` (tên một model flash khác) để tự hỏi sang model đó. Hỏng hết thì trang Hỏi đáp vẫn hiện các đoạn cẩm nang liên quan. Log: tìm `RAG chat:`.
- ADMIN upload tài liệu (PDF/DOCX/TXT/MD, không nhận PDF scan): `POST /api/rag/documents`, form-data `file`. Mọi người đã đăng nhập hỏi: `POST /api/rag/chat`, JSON `{"question":"..."}` → `{answer, sources}`. Hai API này cần phiên đăng nhập và CSRF token như các form khác.
- Lần chạy đầu Spring AI tự tạo extension `vector` và bảng `vector_store`. Upload cùng một file hai lần sẽ lưu hai lần.

### Phân tích ảnh lá bằng model (nút "Phân tích")

Trang chi tiết lượt quét có nút **Phân tích**: LeafCare gửi ảnh sang service Python trong thư mục `scan-service/` (FastAPI + model CNN 39 nhãn PlantVillage, chạy bằng ONNX Runtime) và lưu bệnh, độ tin cậy vào lượt quét. Danh mục khớp với model qua `plant.model_prefix` (14 cây model biết) và `disease.model_label` (seed `db/seed/V7_1__seed_model_diseases.sql`, `V8_1__seed_all_model_plants.sql`). Người dùng có thể chọn loại cây trước khi quét: khi đó chỉ các nhãn của cây đó được xét (xác suất chuẩn hoá lại), và nếu ảnh rõ ràng là cây khác (≥ 50%) trang kết quả sẽ báo. Trang kết quả hiện top 3 và cảnh báo khi độ tin cậy dưới 80%.

- File model `model.onnx` (~210 MB, quá giới hạn 100 MB của GitHub) nằm ở Hugging Face: https://huggingface.co/shin0110/leafcare-scan. Model gốc là PyTorch (`plant_disease_model_1_latest.pt`), đã xuất sang ONNX; kết quả giống bản PyTorch, RAM ~330 MB thay vì ~930 MB nên chạy được trên Render Free.
- Chạy trên máy: trong `scan-service/`, `python -m venv .venv`, bật venv, `pip install -r requirements.txt`, tải `model.onnx` từ link trên vào thư mục, rồi `uvicorn main:app --port 7860`.
- Deploy (Render, Web Service thứ hai, Free): Language **Python 3**, Root Directory `scan-service`, Build Command `pip install -r requirements.txt && curl -fL -o model.onnx https://huggingface.co/shin0110/leafcare-scan/resolve/main/model.onnx`, Start Command `uvicorn main:app --host 0.0.0.0 --port $PORT`, Health Check Path `/health`. Sau đó đặt env `SCAN_SERVICE_URL` của LeafCare = địa chỉ service này.
- LeafCare đọc địa chỉ service từ env `SCAN_SERVICE_URL` (mặc định `http://localhost:7860`). Service Free ngủ sau 15 phút không dùng; lần đầu gọi lại mất khoảng 1 phút.
- Kiểm tra trên Render: Logs của `leafcare-scan` có `POST /predict ... 200 OK` mỗi lần phân tích; lỗi thì Logs của LeafCare có `Scan 12 analysis failed: ...`.
- Service tắt hoặc chậm quá 60 giây: lượt quét thành "Chưa phân tích được", người dùng bấm "Phân tích lại".

### Deploy lên Render (Docker + Neon PostgreSQL)

App có `Dockerfile`; Render tự build từ nhánh `main` mỗi khi có commit mới.

1. **Database (Neon, miễn phí):** https://neon.tech → New project, region **Singapore** → Connect, lấy chuỗi `postgresql://<user>:<pass>@<host>/<db>?sslmode=require`.
2. **Render:** https://render.com → New → Web Service → chọn repo GitHub → Language **Docker**, Branch `main`, Region **Singapore**, Instance **Free**.
3. **Environment** trên Render:

| Biến | Giá trị |
|---|---|
| `DB_URL` | `jdbc:postgresql://<host>/<db>?sslmode=require` (lấy từ chuỗi Neon, bỏ phần `user:pass@`) |
| `DB_USERNAME`, `DB_PASSWORD` | user và mật khẩu trong chuỗi Neon |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | như khi chạy local |
| `LEAFCARE_ADMIN_EMAILS` | email ADMIN |
| `SEPAY_BANK`, `SEPAY_ACCOUNT`, `SEPAY_ACCOUNT_NAME`, `SEPAY_API_KEY` | như khi chạy local |
| `SHOW_SQL` | `false` (đỡ rác log) |

Render tự đặt `PORT`; app đọc `X-Forwarded-*` nên link đăng nhập Google là `https://`.

4. **Google Console** → Credentials → OAuth client → thêm Authorized redirect URI `https://<tên>.onrender.com/login/oauth2/code/google`.
5. **SePay** → Webhook: đổi URL thành `https://<tên>.onrender.com/payments/sepay/webhook`.

**Cập nhật code:** merge PR vào `main` là Render tự build và deploy lại (3–5 phút); Flyway tự chạy migration mới trên DB Neon. Nên đặt Settings → Auto-Deploy = **After CI Checks Pass** để code đỏ CI không lên. Thêm biến môi trường mới thì nhớ thêm cả trên Render.

Giới hạn gói Free: app ngủ sau 15 phút không có truy cập (mở lại mất ~1 phút; SePay tự gửi lại webhook); đĩa của server bị xoá mỗi lần deploy, vì vậy ảnh được lưu trong database chứ không lưu ra đĩa.

## Quy trình làm việc nhóm

- Mỗi task một nhánh tách từ `main`: `feat/...`, `fix/...`, `docs/...`, `chore/...`.
- Push nhánh rồi mở Pull Request vào `main`. Không commit thẳng lên `main`.
- `main` được bảo vệ: PR phải qua CI `build-and-test` (build CSS + `./mvnw verify`) mới merge được. Merge kiểu **squash**.
- Chạy `./mvnw test` ở máy trước khi push.
- Đổi schema DB: thêm file migration mới `V<N>__mo_ta.sql`. **Không sửa file migration đã merge.**

## Tài liệu thêm

- `docs/PROGRESS.md`: tiến độ và các quyết định quan trọng
- `DESIGN.md`, `PRODUCT.md`: định hướng thiết kế giao diện
- `docs/superpowers/`: spec và plan từng phase

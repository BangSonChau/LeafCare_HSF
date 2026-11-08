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

Lần chạy đầu Flyway tự tạo bảng (`db/migration`) và nạp dữ liệu mẫu (`db/seed`). Ảnh upload được lưu ở thư mục `uploads/`.

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

Code MoMo sandbox cũ vẫn còn ở `service/momo/` (không còn nút trên giao diện).

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

# LeafCare

Web app quản lý sức khoẻ cây trồng (đồ án cuối kỳ HSF, FPT). Người dùng quản lý danh sách cây, danh mục bệnh, và lưu lịch sử quét lá kèm ảnh chụp.

## Chức năng

- **Dashboard** (`/`): thống kê nhanh về cây, bệnh và các lượt quét.
- **Cây trồng** (`/plants`): thêm, sửa, xoá, phân trang.
- **Bệnh** (`/diseases`): thêm, sửa, xoá danh mục bệnh.
- **Lịch sử quét lá** (`/scans`): tạo lượt quét có upload ảnh (tối đa 5MB), xem chi tiết, xoá.
- **Trợ lý AI & RAG** (`/chat`): hỏi triệu chứng tiếng Việt, đính kèm ảnh lá phân tích đa phương thức (Gemini Multimodal), đối chiếu kho tri thức cây trồng Việt Nam đã kiểm duyệt (`src/main/resources/knowledge/manifest.json`) kèm dẫn nguồn truy vết.

## Công nghệ

- Java 17, Spring Boot 4.1.2 (Spring MVC, Spring Data JPA, Bean Validation), Lombok
- Thymeleaf (layout dialect), render phía server
- Tailwind CSS v4 + DaisyUI v5
- SQL Server, schema quản lý bằng Flyway (migration riêng cho SQL Server)
- JUnit 5 + Mockito, test chạy trên H2

## Chạy trên máy

Cần: JDK 17 trở lên, Maven, Node.js 22 trở lên, SQL Server 2019 trở lên (hỗ trợ collation UTF-8).

1. Trên máy có SQL Server Express (`.\SQLEXPRESS`), chạy `powershell -File .\setup-local-db.ps1` một lần bằng tài khoản Windows có quyền tạo database/login. Script tạo database `leafcare` với collation UTF-8 và login `leafcare_app` có quyền đọc ghi, tạo bảng và migration trong schema `dbo` của riêng database này. Mật khẩu ngẫu nhiên lưu trong `application-local.properties` đã được Git bỏ qua.
2. JDBC mặc định kết nối `localhost:1433`. Nếu dùng server khác, cấu hình `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và tạo database với collation UTF-8 tương ứng. `trustServerCertificate=true` chỉ phù hợp môi trường local.
3. Chạy từ thư mục dự án:

```bash
powershell -File .\run-local.ps1
```

4. Mở http://localhost:8081 (tránh cổng 8080 đang được dùng trên máy).
5. (Tuỳ chọn) Để bật gọi trực tiếp **Google Gemini API** cho tính năng Chat & nhận diện ảnh lá (`/chat`), thêm biến môi trường `GEMINI_API_KEY` hoặc khai báo vào `application-local.properties`:

```properties
leafcare.gemini.api-key=YOUR_GEMINI_API_KEY
```

Nếu chưa cấu hình `GEMINI_API_KEY`, trang `/chat` vẫn hoạt động ở chế độ tra cứu trực tiếp kho tri thức RAG cục bộ đã kiểm duyệt.

Lần chạy đầu Flyway tự tạo bảng (`db/sqlserver/migration`) và nạp dữ liệu mẫu (`db/sqlserver/seed`). Ảnh upload được lưu ở thư mục `uploads/`. Các migration cũ trong `db/migration` được giữ nguyên cho bộ test H2.

Chạy test (dùng H2, không cần PostgreSQL):

```bash
./mvnw test
```

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

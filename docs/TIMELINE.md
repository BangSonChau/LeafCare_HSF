# LeafCare · Timeline dự án

Ứng dụng nhận diện bệnh lá cây · Spring Boot + Thymeleaf + Tailwind CSS v4 / DaisyUI + PostgreSQL.

Bắt đầu thứ Hai 09/11/2026, kéo dài 10 tuần đến 17/01/2027. Chưa có deadline chính thức nên lịch chia theo tuần; nếu hạn nộp khác, dời các mốc tương ứng.

> File này thay cho `LeafCare_Timeline.pdf` (bản cũ, còn ghi VNPay). Thay đổi so với bản PDF: thanh toán dùng **SePay (QR chuyển khoản ngân hàng thật)** thay VNPay; MoMo sandbox đã thử nhưng bỏ vì thẻ test bị từ chối. Cập nhật 08/11/2026: Giai đoạn 2 xong sớm, thêm deploy Render và dashboard quản trị. Cập nhật 13/11/2026: nhận diện lá dùng model riêng (ONNX) thay vision API; RAG, chat có trích dẫn và nối với kết quả quét đã xong.

## Giai đoạn

| Giai đoạn | Tuần | Công việc | Trạng thái |
|---|---|---|---|
| **1 · Assignment** | 1–3 | Dựng project Spring Boot, Tailwind v4 + DaisyUI, layout chung | Xong |
| | | Entity Plant, Disease, ScanHistory + CRUD, tìm kiếm, phân trang | Xong |
| | | Upload ảnh lá, lưu lịch sử, trang chủ / dashboard | Xong |
| | | Chụp lại prompt dùng AI agent (Copilot / Claude Code / Cursor) | |
| | | Slide + user guide Assignment | |
| **2 · Đăng nhập + Thanh toán** | 4–5 | Spring Security + Google OAuth2, phân quyền USER / ADMIN | Xong |
| | | Entity Subscription, Payment; tích hợp **SePay** (QR VietQR, webhook xác thực API key) | Xong |
| | | Trang giao diện thanh toán: chọn gói Free/Premium, trang QR thanh toán, trang kết quả, lịch sử giao dịch | Xong |
| | | Giới hạn tài khoản Free (5 lượt quét / ngày) | Xong |
| | | ADMIN thấy người quét; dashboard quản trị `/admin` (số liệu, biểu đồ 14 ngày) + danh sách người dùng | Xong |
| | | Deploy lên Render (Docker) + PostgreSQL Neon: https://leafcare-4nh6.onrender.com | Xong |
| **3 · AI** | 6–8 | ~~Nhận diện lá qua vision API~~: bỏ, thay bằng model riêng bên dưới | Bỏ |
| | | Service FastAPI chạy model PlantVillage bằng ONNX Runtime (Render Free), chọn loại cây, top 3 | Xong |
| | | Chatbot Spring AI (Gemini) | Xong |
| | | Trang Admin nhập API key, chọn nhà cung cấp | |
| | | RAG: pgvector, upload tài liệu (nạp ở nền), chat có trích dẫn, chữ chạy dần, semantic cache, chịu lỗi khi Gemini quá tải | Xong |
| | | Nối chatbot với kết quả quét lá (nút "Hỏi cẩm nang") | Xong |
| **4 · Hoàn thiện** | 9–10 | Sửa lỗi, UI responsive, dữ liệu mẫu | |
| | | Docker Compose (app + Postgres) cho chạy local (Dockerfile đã có từ lúc deploy Render) | |
| | | User guide cuối kỳ, slide demo, video dự phòng | |

## Công việc theo tuần

| Tuần | Trọng tâm | Công việc | Kết quả cần đạt |
|---|---|---|---|
| **1** · 09/11 – 15/11/2026 | Setup | Tạo project Spring Boot (Web, Thymeleaf, JPA, Validation, Lombok, PostgreSQL). Cài Tailwind CLI v4 + DaisyUI, quét `templates/**/*.html`. Layout chung (navbar, footer) bằng thymeleaf-layout-dialect. Đẩy code lên GitHub. | Project chạy được, layout chung, repo GitHub |
| **2** · 16/11 – 22/11/2026 | Chức năng | Entity `Plant`, `Disease`, `ScanHistory`. CRUD Cây/Bệnh (danh sách, thêm, sửa, xoá, tìm kiếm, phân trang). Trang upload ảnh lá (lưu file + lịch sử). Trang chủ / dashboard. | CRUD hoàn chỉnh, upload ảnh, dashboard |
| **3** · 23/11 – 29/11/2026 | Tài liệu | Tổng hợp ảnh chụp prompt dùng AI agent để sinh UI và back-end. Viết slide + user guide: cài đặt, cấu hình Tailwind với Thymeleaf, cách dùng AI, demo. | Nộp Assignment |
| **4** · 30/11 – 06/12/2026 | Đăng nhập | Spring Security + Google OAuth2 login, phân quyền USER / ADMIN. Bắt đầu entity `Subscription`, `Payment`. Trang chọn gói Free / Premium (bảng so sánh quyền lợi, giá). | Đăng nhập Google, phân quyền, trang chọn gói |
| **5** · 07/12 – 13/12/2026 | Thanh toán | Thanh toán Premium qua **SePay**: tạo đơn, hiện QR VietQR (số tiền + mã đơn), SePay gọi webhook khi tiền về, kiểm API key và số tiền, cập nhật `Payment` + kích hoạt `Subscription`. Giao diện: trang QR thanh toán (tự tải lại), trang kết quả, lịch sử giao dịch. Giới hạn Free (5 lượt quét / ngày). **Đã xong sớm 08/11/2026**, kèm deploy Render và dashboard quản trị. | Mua Premium bằng chuyển khoản thật, xem được lịch sử giao dịch |
| **6** · 14/12 – 20/12/2026 | Nhận diện lá | ~~Vision API~~ → service FastAPI + ONNX chạy model PlantVillage, trả về tên cây, tên bệnh, độ tin cậy, top 3. **Đã xong sớm 13/11/2026.** | Quét lá ra kết quả bệnh |
| **7** · 21/12 – 27/12/2026 | Chatbot | Giao diện chat dùng Spring AI. Trang Admin nhập API key và chọn nhà cung cấp (OpenAI / Gemini / Claude); key lưu mã hoá trong database. | Chat đổi được nhà cung cấp |
| **8** · 28/12/2026 – 03/01/2027 | RAG | Bật pgvector, upload tài liệu bệnh cây (PDF/MD), chia nhỏ + tạo embedding. Chat trả lời kèm nguồn trích dẫn. Nối chatbot với kết quả quét lá ("Lá bị bệnh X, cách chữa?"). **Đã xong sớm 13/11/2026**, kèm semantic cache và chịu lỗi khi Gemini quá tải. | Chat có trích dẫn, nối với kết quả quét |
| **9** · 04/01 – 10/01/2027 | Ổn định | Sửa lỗi, làm UI responsive, viết dữ liệu mẫu, Docker Compose (app + Postgres). | Bản chạy ổn định bằng Docker |
| **10** · 11/01 – 17/01/2027 | Bàn giao | Viết user guide cuối kỳ và slide demo, quay video dự phòng. | Demo cuối kỳ |

## Giao diện thanh toán (Giai đoạn 2)

| Trang | Nội dung |
|---|---|
| Chọn gói | So sánh Free (5 lượt quét / ngày) và Premium (không giới hạn), giá, gói đang dùng; nút nâng cấp. |
| Thanh toán QR | Tóm tắt đơn (gói, số tiền), nút "Tạo mã QR chuyển khoản"; trang đơn hiện QR VietQR, số tài khoản, nội dung = mã đơn. |
| Kết quả thanh toán | Cùng trang đơn: chờ thanh toán (tự tải lại 5 giây) / thành công / thất bại; trạng thái chỉ đổi qua webhook SePay, không tin trình duyệt. |
| Lịch sử giao dịch | Danh sách giao dịch của người dùng: mã đơn, gói, số tiền, trạng thái, thời gian; phân trang. |
| Quản trị (ADMIN) | `/admin`: người dùng, Premium, doanh thu, lượt quét, biểu đồ 14 ngày, giao dịch mới, người quét nhiều; `/admin/users`: tìm người dùng, gói, số lượt quét. |

## Chia việc nhóm 4 người

| Thành viên | Phụ trách |
|---|---|
| **1 · Back-end + CSDL** | Entity, repository, service, CRUD, upload file, cấu hình PostgreSQL, Docker Compose, dữ liệu mẫu. |
| **2 · Giao diện Tailwind/Thymeleaf** | Tailwind v4 + DaisyUI, layout Thymeleaf, các trang danh sách / form / dashboard, giao diện thanh toán, giao diện chat, responsive. |
| **3 · Đăng nhập + Thanh toán** | Spring Security, Google OAuth2, phân quyền USER / ADMIN, Subscription / Payment, SePay + webhook, lịch sử giao dịch, giới hạn Free, dashboard quản trị, deploy Render. |
| **4 · AI (model, chat, RAG)** | FastAPI + ONNX model PlantVillage, Spring AI chatbot, quản lý API key, RAG với pgvector. |

Ai làm phần nào thì tự viết tài liệu cho phần đó. Cả nhóm cùng làm: ghi lại prompt AI agent (tuần 1–3), slide + user guide (tuần 3 và 10), sửa lỗi + dữ liệu mẫu (tuần 9).

## Các mốc chính

- **Cuối tuần 3 (29/11/2026):** Nộp Assignment (CRUD, upload ảnh, slide, user guide, ảnh chụp prompt AI).
- **Cuối tuần 5 (13/12/2026):** Đăng nhập Google + thanh toán chạy được, có trang kết quả và lịch sử giao dịch. **Đã đạt sớm 08/11/2026** (SePay, kèm deploy Render và dashboard quản trị).
- **Cuối tuần 8 (03/01/2027):** Nhận diện lá, chatbot đổi nhà cung cấp, RAG có trích dẫn. Nhận diện lá và RAG **đã đạt sớm 13/11/2026**; còn trang Admin nhập API key / chọn nhà cung cấp.
- **Cuối tuần 10 (17/01/2027):** Demo cuối kỳ, user guide, slide, video dự phòng.

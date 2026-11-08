# LeafCare · Timeline dự án

Ứng dụng nhận diện bệnh lá cây · Spring Boot + Thymeleaf + Tailwind CSS v4 / DaisyUI + PostgreSQL.

Bắt đầu thứ Hai 09/11/2026, kéo dài 10 tuần đến 17/01/2027. Chưa có deadline chính thức nên lịch chia theo tuần; nếu hạn nộp khác, dời các mốc tương ứng.

> File này thay cho `LeafCare_Timeline.pdf` (bản cũ, còn ghi VNPay). Thay đổi so với bản PDF: thanh toán dùng **MoMo sandbox** thay VNPay, và liệt kê rõ các trang giao diện thanh toán.

## Giai đoạn

| Giai đoạn | Tuần | Công việc | Trạng thái |
|---|---|---|---|
| **1 · Assignment** | 1–3 | Dựng project Spring Boot, Tailwind v4 + DaisyUI, layout chung | Xong |
| | | Entity Plant, Disease, ScanHistory + CRUD, tìm kiếm, phân trang | Xong |
| | | Upload ảnh lá, lưu lịch sử, trang chủ / dashboard | Xong |
| | | Chụp lại prompt dùng AI agent (Copilot / Claude Code / Cursor) | |
| | | Slide + user guide Assignment | |
| **2 · Đăng nhập + Thanh toán** | 4–5 | Spring Security + Google OAuth2, phân quyền USER / ADMIN | |
| | | Entity Subscription, Payment; tích hợp **MoMo sandbox** (tạo đơn, redirect, IPN, kiểm chữ ký) | |
| | | Trang giao diện thanh toán: chọn gói Free/Premium, nút thanh toán MoMo, trang kết quả, lịch sử giao dịch | |
| | | Giới hạn tài khoản Free (5 lượt quét / ngày) | |
| **3 · AI** | 6–8 | Nhận diện lá qua vision API (Gemini / GPT-4o) | |
| | | Service FastAPI chạy model PlantVillage (tuỳ chọn) | |
| | | Chatbot Spring AI + trang Admin nhập API key, chọn nhà cung cấp | |
| | | RAG: pgvector, upload tài liệu, chat có trích dẫn | |
| | | Nối chatbot với kết quả quét lá | |
| **4 · Hoàn thiện** | 9–10 | Sửa lỗi, UI responsive, dữ liệu mẫu | |
| | | Docker Compose (app + Postgres) | |
| | | User guide cuối kỳ, slide demo, video dự phòng | |

## Công việc theo tuần

| Tuần | Trọng tâm | Công việc | Kết quả cần đạt |
|---|---|---|---|
| **1** · 09/11 – 15/11/2026 | Setup | Tạo project Spring Boot (Web, Thymeleaf, JPA, Validation, Lombok, PostgreSQL). Cài Tailwind CLI v4 + DaisyUI, quét `templates/**/*.html`. Layout chung (navbar, footer) bằng thymeleaf-layout-dialect. Đẩy code lên GitHub. | Project chạy được, layout chung, repo GitHub |
| **2** · 16/11 – 22/11/2026 | Chức năng | Entity `Plant`, `Disease`, `ScanHistory`. CRUD Cây/Bệnh (danh sách, thêm, sửa, xoá, tìm kiếm, phân trang). Trang upload ảnh lá (lưu file + lịch sử). Trang chủ / dashboard. | CRUD hoàn chỉnh, upload ảnh, dashboard |
| **3** · 23/11 – 29/11/2026 | Tài liệu | Tổng hợp ảnh chụp prompt dùng AI agent để sinh UI và back-end. Viết slide + user guide: cài đặt, cấu hình Tailwind với Thymeleaf, cách dùng AI, demo. | Nộp Assignment |
| **4** · 30/11 – 06/12/2026 | Đăng nhập | Spring Security + Google OAuth2 login, phân quyền USER / ADMIN. Bắt đầu entity `Subscription`, `Payment`. Trang chọn gói Free / Premium (bảng so sánh quyền lợi, giá). | Đăng nhập Google, phân quyền, trang chọn gói |
| **5** · 07/12 – 13/12/2026 | Thanh toán | Tích hợp **MoMo sandbox** để mua gói Premium: tạo đơn (API `create`, ký HMAC-SHA256), chuyển sang trang MoMo, nhận `redirectUrl` và IPN (`ipnUrl`), kiểm chữ ký, cập nhật `Payment` + kích hoạt `Subscription`. Giao diện: nút "Thanh toán bằng MoMo", trang kết quả thanh toán (thành công / thất bại / đang xử lý), trang lịch sử giao dịch của người dùng. Giới hạn tài khoản Free (5 lượt quét / ngày). | Mua Premium qua MoMo sandbox, xem được lịch sử giao dịch |
| **6** · 14/12 – 20/12/2026 | Nhận diện lá | Gửi ảnh qua vision API (Gemini / GPT-4o), trả về tên cây, tên bệnh, độ tin cậy. Nếu còn thời gian: service FastAPI chạy model PlantVillage. | Quét lá ra kết quả bệnh |
| **7** · 21/12 – 27/12/2026 | Chatbot | Giao diện chat dùng Spring AI. Trang Admin nhập API key và chọn nhà cung cấp (OpenAI / Gemini / Claude); key lưu mã hoá trong database. | Chat đổi được nhà cung cấp |
| **8** · 28/12/2026 – 03/01/2027 | RAG | Bật pgvector, upload tài liệu bệnh cây (PDF/MD), chia nhỏ + tạo embedding. Chat trả lời kèm nguồn trích dẫn. Nối chatbot với kết quả quét lá ("Lá bị bệnh X, cách chữa?"). | Chat có trích dẫn, nối với kết quả quét |
| **9** · 04/01 – 10/01/2027 | Ổn định | Sửa lỗi, làm UI responsive, viết dữ liệu mẫu, Docker Compose (app + Postgres). | Bản chạy ổn định bằng Docker |
| **10** · 11/01 – 17/01/2027 | Bàn giao | Viết user guide cuối kỳ và slide demo, quay video dự phòng. | Demo cuối kỳ |

## Giao diện thanh toán (Giai đoạn 2)

| Trang | Nội dung |
|---|---|
| Chọn gói | So sánh Free (5 lượt quét / ngày) và Premium (không giới hạn), giá, gói đang dùng; nút nâng cấp. |
| Thanh toán MoMo | Tóm tắt đơn (gói, số tiền), nút "Thanh toán bằng MoMo" chuyển sang trang thanh toán sandbox của MoMo. |
| Kết quả thanh toán | Trang `redirectUrl` MoMo trả về: thành công / thất bại / đang chờ IPN; trạng thái lấy từ DB (đã cập nhật qua IPN), không tin tham số trên URL. |
| Lịch sử giao dịch | Danh sách giao dịch của người dùng: mã đơn, gói, số tiền, trạng thái, thời gian; phân trang. |

## Chia việc nhóm 4 người

| Thành viên | Phụ trách |
|---|---|
| **1 · Back-end + CSDL** | Entity, repository, service, CRUD, upload file, cấu hình PostgreSQL, Docker Compose, dữ liệu mẫu. |
| **2 · Giao diện Tailwind/Thymeleaf** | Tailwind v4 + DaisyUI, layout Thymeleaf, các trang danh sách / form / dashboard, giao diện thanh toán, giao diện chat, responsive. |
| **3 · Đăng nhập + Thanh toán** | Spring Security, Google OAuth2, phân quyền USER / ADMIN, Subscription / Payment, MoMo sandbox + IPN, lịch sử giao dịch, giới hạn Free. |
| **4 · AI (vision, chat, RAG)** | Vision API nhận diện lá, (tuỳ chọn) FastAPI + PlantVillage, Spring AI chatbot, quản lý API key, RAG với pgvector. |

Ai làm phần nào thì tự viết tài liệu cho phần đó. Cả nhóm cùng làm: ghi lại prompt AI agent (tuần 1–3), slide + user guide (tuần 3 và 10), sửa lỗi + dữ liệu mẫu (tuần 9).

## Các mốc chính

- **Cuối tuần 3 (29/11/2026):** Nộp Assignment (CRUD, upload ảnh, slide, user guide, ảnh chụp prompt AI).
- **Cuối tuần 5 (13/12/2026):** Đăng nhập Google + thanh toán MoMo sandbox chạy được, có trang kết quả và lịch sử giao dịch.
- **Cuối tuần 8 (03/01/2027):** Nhận diện lá, chatbot đổi nhà cung cấp, RAG có trích dẫn.
- **Cuối tuần 10 (17/01/2027):** Demo cuối kỳ, user guide, slide, video dự phòng.

# LeafCare – Thiết kế Giai đoạn 1 (Assignment, tuần 1–3)

Hạn nộp: 29/11/2026. Nguồn: `docs/LeafCare_Timeline.pdf`.

## Ý định và lý do
Làm phần code của giai đoạn 1 để nộp Assignment: CRUD, upload ảnh lá, lịch sử, dashboard. Hiện làm một mình, sau này thêm thành viên, nên cấu trúc phải dễ chia việc. Chưa có AI (tuần 6) nên upload ảnh chỉ lưu lại, chưa nhận diện.

## Mục tiêu
- Layout chung (navbar, footer) dùng `thymeleaf-layout-dialect`.
- CRUD `Plant`, `Disease`: danh sách, thêm, sửa, xoá, tìm kiếm theo tên, phân trang (10 dòng/trang).
- Upload ảnh lá + ghi chú, lưu `ScanHistory` trạng thái "Chờ phân tích"; xem danh sách (phân trang) và chi tiết, xoá.
- Trang chủ/dashboard: tổng số cây, bệnh, lượt quét; vài lượt quét gần nhất.
- Tailwind v4 + DaisyUI (đã có sẵn).

## Không làm (non-goals)
- Đăng nhập, phân quyền, gắn user vào lịch sử (giai đoạn 2).
- Nhận diện bệnh, chatbot, RAG (giai đoạn 3).
- Slide, user guide, chụp prompt AI: để sau, do bạn/nhóm làm.
- Dữ liệu mẫu, Docker Compose (tuần 9).

## Ràng buộc
- Spring Boot 4.1.2, Java 17, Thymeleaf, JPA, PostgreSQL (đã cấu hình, `ddl-auto=update`).
- Base package `com.son.leafcare`.
- CSS build bằng `npm run build`; `output.css` được commit/được sinh trước khi chạy.

## Các cách đã cân nhắc và lựa chọn
**Layout:** (A) Thymeleaf fragments có sẵn; (B) `thymeleaf-layout-dialect`. **Chọn B** theo timeline; chấp nhận rủi ro tương thích với Boot 4. Nếu dialect không tự đăng ký, khai báo `@Bean LayoutDialect`; nếu vẫn lỗi thì chuyển sang A.
**Lưu ảnh:** (A) file trên đĩa; (B) `bytea` trong DB. **Chọn A**: thư mục `uploads/` (cấu hình `leafcare.upload-dir`), DB chỉ lưu tên file. Ảnh phục vụ qua `/uploads/**` bằng resource handler.
**Upload giai đoạn 1:** chọn cách đơn giản: chỉ upload ảnh + ghi chú, không chọn cây/bệnh bằng tay, không làm kết quả giả.

## Mô hình dữ liệu
- `Plant`: id, name (bắt buộc, duy nhất), scientificName, description.
- `Disease`: id, name (bắt buộc), symptoms, treatment, `plant` (ManyToOne, có thể null).
- `ScanHistory`: id, imageFileName, note, status (enum `PENDING` mặc định), confidence (null), `plant` và `disease` (ManyToOne, null), createdAt.
`status`, `confidence`, `plant`, `disease` có sẵn để tuần 6 chỉ điền kết quả AI mà không đổi bảng.

## Cấu trúc code
Theo tầng: `controller / service / repository / entity` dưới `com.son.leafcare`. Mỗi entity một controller, service, repository. Template trong `templates/{plants,diseases,scans}/` + `templates/layout/base.html`. Validation bằng Bean Validation trên form.

## Upload
- Chỉ nhận ảnh (jpg/png/webp), tối đa 5 MB; kiểm tra content-type và phần mở rộng.
- Lưu tên file ngẫu nhiên (UUID + đuôi) để tránh trùng và path traversal.
- Xoá lượt quét thì xoá cả file.
- Báo lỗi thân thiện trên form khi file sai.

## Việc để người làm quyết định
Giao diện chi tiết từng trang, thông báo flash, tên route chính xác, số bản ghi dashboard.

## Kiểm thử
Một test cho service upload (từ chối file sai loại, lưu file đúng) và một test cho tìm kiếm/phân trang repository. Test context-load cần DB hoặc profile test.

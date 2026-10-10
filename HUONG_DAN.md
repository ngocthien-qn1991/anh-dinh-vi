# Ảnh Định Vị – Hướng dẫn cài đặt

## 1. Tạo Google Sheet nhận dữ liệu (làm 1 lần)
1. Tạo Google Sheet mới.
2. Menu **Tiện ích mở rộng → Apps Script**, xóa code mẫu, dán toàn bộ `apps-script/Code.gs`, bấm Lưu.
3. Chọn hàm `capQuyen` → **Chạy** → cấp quyền cho tài khoản Google (Sheet + Drive).
4. **Triển khai → Tùy chọn triển khai mới → Ứng dụng web**:
   - Thực thi dưới dạng: **Tôi**
   - Người có quyền truy cập: **Bất kỳ ai**
5. Sao chép đường dẫn dạng `https://script.google.com/macros/s/…/exec`.

Mỗi lần chụp, Sheet "Nhật ký ảnh" có thêm 1 dòng: thời điểm, ngày, giờ, thứ, người chụp,
địa chỉ, vĩ độ, kinh độ, sai số, ghi chú, link Google Maps, link ảnh trên Drive.
Ảnh lưu trong thư mục Drive "Ảnh Định Vị".

## 2. Xuất file APK bằng GitHub (không cần cài gì)
1. Đăng nhập github.com → **New repository** (để Private cũng được) → Create.
2. Bấm **uploading an existing file**, kéo thả toàn bộ nội dung đã giải nén
   (gồm thư mục `.github`, `android`, `apps-script` và file `index.html`) → Commit.
   Lưu ý: thư mục `.github` là thư mục ẩn, trên máy tính cần bật hiện file ẩn.
3. Vào tab **Actions** → chờ "Build APK" có dấu tích xanh (khoảng 3–5 phút).
4. Bấm vào lần chạy đó → mục **Artifacts** → tải `AnhDinhVi-apk` (file zip chứa `AnhDinhVi.apk`).
5. Chép APK vào điện thoại, mở để cài (cho phép "Cài ứng dụng không rõ nguồn gốc").

Cách khác: mở thư mục `android` bằng Android Studio → Build → Build APK(s).

## 3. Dùng app
1. Mở app, cho phép **Vị trí**.
2. Bấm biểu tượng bánh răng → dán đường dẫn Apps Script → **Kiểm tra kết nối** → Lưu.
   Có thể nhập tên người chụp và chọn logo đóng lên ảnh.
3. Nhập ghi chú → **Chụp ảnh**. Ảnh có dấu được lưu vào album *AnhDinhVi* và gửi lên Sheet.
4. Mất mạng: bản ghi được giữ lại và tự gửi khi có mạng (không bị trùng dòng).

## Sửa giao diện hoặc nội dung dấu ảnh
Chỉ cần sửa `index.html` rồi tải lên GitHub lại, APK mới sẽ tự được build.

## 4. Dùng trên iPhone (bản web)
Mở bằng **Safari**: https://ngocthien-qn1991.github.io/anh-dinh-vi/
→ bấm nút Chia sẻ → **Thêm vào MH chính**. Cho phép Máy ảnh và Vị trí khi được hỏi.
Vào ⚙️ dán cùng link Apps Script như bản Android.
Lưu ảnh về máy: sau khi chụp bấm **Lưu vào Ảnh** → **Lưu hình ảnh**.

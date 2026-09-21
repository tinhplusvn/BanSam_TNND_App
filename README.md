# Quản lý thiếu niên nhi đồng Bản Sấm

Ứng dụng Android native Kotlin + Jetpack Compose, dùng dữ liệu mẫu từ file `data.xlsx` đã cung cấp.

## Chức năng
- Thêm / sửa / xóa hồ sơ.
- Tìm kiếm theo họ tên, bố, mẹ, SĐT, địa chỉ, lớp.
- STT tự động khi xuất Excel.
- Tự tính tuổi theo ngày sinh.
- Tự chia khối: Mầm non 3–5, Tiểu học 6–10, THCS 11–15.
- Lưu riêng tên bố, tên mẹ và SĐT phụ huynh.
- Địa chỉ chọn nhanh: Thôn Sấm 1, Thôn Sấm 2, Thôn Sấm 3, Thôn Bãi Cả.
- Nhập `.xlsx` theo mẫu 9 cột của file gốc.
- Xuất `.xlsx`.
- Giao diện sáng/tối.
- 246 hồ sơ từ file mẫu được đóng gói làm dữ liệu ban đầu.

## Cách mở
1. Mở thư mục này bằng Android Studio.
2. Chờ Gradle Sync.
3. Chọn điện thoại Android hoặc Emulator.
4. Run.

## Mẫu Excel
Ứng dụng dùng các cột:
`STT | Họ và tên | Ngày sinh | Giới tính | Địa chỉ | Lớp | Họ tên Bố | Họ tên Mẹ | SĐT PH`

Ngày sinh khi nhập được nhận dạng dạng `dd/MM/yyyy`, `dd-MM-yyyy` hoặc `yyyy-MM-dd`.

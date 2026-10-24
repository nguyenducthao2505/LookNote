# LookNote – sổ công thức Creative Look cho máy ảnh Sony (Android)

Ứng dụng Android độc lập, chức năng tương tự AlphaLuma (iOS). **Không phải ứng dụng chính thức của Sony**, không liên kết với Sony Group Corporation hay nhà phát triển AlphaLuma. Mã nguồn, tên, biểu tượng và bộ công thức mẫu đều được viết mới.

Yêu cầu: Android 10 trở lên.

## Chức năng phiên bản 0.2.0

| Nhóm | Chức năng | Trạng thái |
|---|---|---|
| Sổ công thức | Lưu không giới hạn; Creative Look (ST, PT, NT, VV, VV2, FL, FL2, FL3, IN, SH, BW, SE); 8 thông số theo thứ tự menu máy (Phạm vi nét −4…+4); cân bằng trắng + dịch A–B (bước 0,5) và G–M (bước 0,25) trong ±7; DRO, bù sáng, ghi chú; yêu thích; tìm kiếm, lọc theo Look; 10 công thức mẫu | Hoàn chỉnh |
| Picture Profile | Lưu công thức PP: Gamma, Black Gamma, Black Level, Knee, Color Mode, Saturation, Color Phase, Color Depth (R/G/B/C/M/Y), Detail; cùng WB | Hoàn chỉnh |
| Công thức mẫu | 30 công thức: 10 do LookNote soạn, 11 Creative Look và 9 Picture Profile sưu tầm từ cộng đồng (ghi rõ nguồn) | Hoàn chỉnh |
| Chia sẻ | Sao chép thông số dạng chữ; mã chia sẻ `LOOKNOTE1:…`; mã QR; thẻ công thức (ảnh PNG) kèm QR; nhập từ ảnh QR hoặc dán mã; xuất/nhập toàn bộ ra tệp JSON (sao lưu) | Hoàn chỉnh |
| Đọc ảnh | EXIF đầy đủ (máy, ống kính, tiêu cự, khẩu, tốc, ISO, bù sáng, thời gian) từ JPEG/ARW; MakerNote Sony: Creative Look/Style, tương phản, bão hòa, độ nét, WB, nhiệt độ màu; xem toàn bộ thẻ MakerNote; tạo công thức từ ảnh | Hoàn chỉnh (một phần thông số Sony lưu dạng mã hóa – xem ghi chú) |
| Khung & huy hiệu | 4 kiểu: thanh trắng, thanh đen, viền trắng, huy hiệu trên ảnh; tự điền thông số chụp; huy hiệu Creative Look; chữ ký; lưu vào `Pictures/LookNote` hoặc chia sẻ | Hoàn chỉnh |
| Máy ảnh qua USB | Kết nối PTP qua cáp (chế độ PC Remote), đọc model/firmware, bắt tay giao thức Sony, liệt kê toàn bộ thuộc tính, **lưu mốc – so sánh** để dò mã thuộc tính, ghi giá trị thủ công, xuất báo cáo | Thử nghiệm |
| Gửi công thức sang máy, live view, Wi‑Fi/Bluetooth, cộng đồng, đồng bộ đám mây | — | Chưa làm (xem lộ trình) |

## Lấy file APK (không cần cài phần mềm lập trình)

1. Tạo tài khoản tại https://github.com (miễn phí).
2. Bấm **New repository** → đặt tên, ví dụ `LookNote` → chọn **Private** hoặc Public → **Create repository**.
3. Trong trang kho vừa tạo, bấm **uploading an existing file**. Giải nén tệp zip, **kéo toàn bộ nội dung bên trong thư mục `LookNote`** (gồm cả thư mục `.github`, `app`, `gradle`, các tệp `gradlew`, `build.gradle.kts`…) vào trang → **Commit changes**.
   - Nếu thư mục `.github` không được tải lên (máy tính ẩn thư mục có dấu chấm): bấm **Add file › Create new file**, gõ tên `.github/workflows/build-apk.yml`, dán nội dung tệp cùng tên trong gói zip, rồi **Commit**.
4. Mở tab **Actions** → chờ quy trình **Build APK** chạy xong (khoảng 5–8 phút, dấu tích xanh).
5. Mở mục **Releases** ở cột phải trang kho (hoặc `https://github.com/<tên-tài-khoản>/LookNote/releases`) → tải tệp `LookNote-build-N.apk` – có thể mở trang này ngay trên điện thoại.
6. Trên điện thoại: mở tệp APK → cho phép "Cài ứng dụng không rõ nguồn gốc" cho trình duyệt/Files → Cài đặt.

Mỗi lần sửa mã và commit, GitHub tự build bản mới. Có thể bấm **Actions › Build APK › Run workflow** để build lại thủ công.

Cách khác: mở thư mục bằng Android Studio → **Build › Build APK(s)**.

## Dò mã Creative Look trên α7C II (chức năng USB thử nghiệm)

Sony không công bố mã thuộc tính PTP cho Creative Look, nên chức năng "gửi công thức sang máy" cần dữ liệu thật từ máy:

1. Máy ảnh: MENU › Mạng › Chuyển/Điều khiển từ xa › Chức năng PC Remote › PC Remote: Bật, Phương thức kết nối: USB.
2. Nối điện thoại với máy bằng cáp USB‑C có truyền dữ liệu, mở LookNote › tab **Máy ảnh** › Tìm máy ảnh › Kết nối.
3. Tab **Thuộc tính** › **Lưu mốc**.
4. Trên máy ảnh đổi đúng một thiết lập (ví dụ Creative Look ST → FL) › **Đọc lại** › bật **Chỉ hiện thay đổi**.
5. Lặp lại cho từng thông số (Tương phản, Vùng sáng, … , A–B, G–M). Bấm biểu tượng **Chia sẻ** để gửi báo cáo.

Với bảng mã này, có thể bổ sung nút "Gửi công thức sang máy" ghi trọn công thức trong một lần.

## Ghi chú kỹ thuật

- MakerNote Sony: thẻ `0xB020` (Creative Style/Look), `0x2004–0x2006` (tương phản, bão hòa, độ nét), `0x0115` (WB), `0xB021` (nhiệt độ màu). Các thông số Vùng sáng/Vùng tối/Phai màu/Độ trong/Phạm vi nét và trục WB nằm trong khối dữ liệu mã hóa của Sony nên chưa đọc được; kết quả nhận diện phụ thuộc model và firmware.
- Ảnh đã gửi qua Zalo/Facebook thường bị xóa EXIF; dùng ảnh gốc từ thẻ nhớ hoặc Creators' App.
- Giao thức USB: PTP chuẩn + lệnh mở rộng Sony `0x9201/0x9202/0x9205/0x9209` theo tài liệu cộng đồng (libgphoto2). Định dạng dữ liệu thuộc tính khác nhau giữa các đời máy nên bộ đọc thử hai kiểu.

## Cấu trúc mã

```
app/src/main/java/com/looknote/app/
  model/   Recipe, RecipeCodec (JSON/mã chia sẻ), RecipeStore (lưu tệp), BuiltInRecipes
  exif/    ExifReader – đọc EXIF/TIFF và MakerNote Sony (thuần Kotlin)
  ptp/     Ptp, PtpSession – giao thức PTP và lệnh Sony (thuần Kotlin)
  usb/     UsbCamera (USB Host), CameraController (trạng thái kết nối)
  render/  FrameRenderer (khung ảnh), RecipeCardRenderer (thẻ công thức)
  util/    ImageUtils (đọc/lưu/chia sẻ ảnh), Qr (tạo/đọc mã QR – ZXing)
  ui/      Giao diện Jetpack Compose
app/src/test/  Kiểm thử đơn vị cho phần lõi (chạy tự động khi build)
```

Công nghệ: Kotlin 2.0, Jetpack Compose Material 3, Android Gradle Plugin 8.7, minSdk 29, targetSdk 35.

## Lộ trình đề xuất

1. Gửi công thức sang máy qua USB (sau khi có bảng mã từ α7C II).
2. Live view + chụp từ xa qua USB (lệnh Sony lấy ảnh xem trước).
3. Kết nối Wi‑Fi (PTP/IP) để dùng các chức năng trên không cần dây.
4. Peaking, zebra, histogram, waveform trên live view.

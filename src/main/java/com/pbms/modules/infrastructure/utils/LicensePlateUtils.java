/**
 * @Author: Nguyen Huu Thanh
 * @Date: 2026-07-21
 * @Description: Utility for normalising license plate strings into a single canonical
 *               form, so plates captured from different sources can be compared safely.
 * @Dependencies: None
 */
package com.pbms.modules.infrastructure.utils;

/**
 * =========================================================================================
 * BỘ CHUẨN HOÁ BIỂN SỐ XE (LICENSE PLATE UTILS)
 * =========================================================================================
 *
 * MỤC ĐÍCH:
 * Cùng 1 chiếc xe nhưng biển số của nó đi vào hệ thống từ nhiều nguồn khác nhau,
 * mỗi nguồn định dạng một kiểu:
 * - Camera nhận dạng biển số (LPR) có thể trả về "59-S3 123.45".
 * - Nhân viên gõ tay tại quầy có thể gõ "59s3 12345".
 * - Khách đăng ký vé tháng có thể nhập "59S3-123.45".
 * Nếu đem so sánh chuỗi thô, 3 giá trị trên bị coi là 3 xe KHÁC NHAU — dẫn tới
 * sai nghiệp vụ nghiêm trọng: không tìm ra đơn đặt chỗ của khách, không nhận ra
 * xe đang trong danh sách đen, hoặc để cùng 1 xe check-in 2 lần. Class này đưa
 * mọi biến thể về đúng 1 dạng chuẩn duy nhất ("59S312345") trước khi so sánh.
 *
 * BẰNG CHỨNG THIẾT KẾ:
 * - Minh chứng 1: Hàm được khai báo `static` và class không có trạng thái nội bộ
 *   (không field nào) — đây là công cụ thuần tính toán, gọi trực tiếp qua tên
 *   class, không cần khởi tạo Object và không thể bị lệch dữ liệu giữa các luồng.
 * - Minh chứng 2: Không phụ thuộc bất kỳ thư viện ngoài nào (`@Dependencies: None`)
 *   nên có thể dùng an toàn ở mọi tầng: Controller, Service hay Repository.
 * =========================================================================================
 */
public class LicensePlateUtils {

    /**
     * =========================================================================
     * HÀM: CHUẨN HOÁ 1 BIỂN SỐ VỀ DẠNG SO SÁNH ĐƯỢC
     * =========================================================================
     * MỤC ĐÍCH:
     * Biến mọi cách viết của cùng 1 biển số thành 1 chuỗi duy nhất, chỉ gồm chữ
     * in hoa và chữ số. Ví dụ: "59-S3 123.45" -> "59S312345".
     *
     * MÃ GIẢ CHI TIẾT:
     * 1. Nếu đầu vào là null -> trả về null luôn (KHÔNG ném ngoại lệ), để nơi gọi
     *    tự quyết định cách xử lý biển số rỗng thay vì bị văng lỗi giữa luồng.
     * 2. Đổi toàn bộ chuỗi sang CHỮ IN HOA.
     * 3. Xoá mọi ký tự KHÔNG phải chữ in hoa A-Z hoặc chữ số 0-9 (gạch ngang,
     *    dấu chấm, dấu cách...).
     *
     * LƯU Ý VỀ THỨ TỰ 2 BƯỚC TRÊN (dễ sửa sai nếu không biết):
     * Bước đổi in hoa BẮT BUỘC phải chạy TRƯỚC bước xoá ký tự lạ, vì biểu thức
     * lọc chỉ giữ lại `A-Z` (in hoa). Nếu đảo thứ tự, biển số gõ bằng chữ thường
     * như "59s312345" sẽ bị xoá sạch phần chữ, chỉ còn lại "59312345" — sai kết
     * quả mà không hề báo lỗi.
     */
    public static String normalize(String plate) {
        if (plate == null) return null;
        return plate.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}

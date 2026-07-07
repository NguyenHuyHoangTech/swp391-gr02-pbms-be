package com.pbms.common.context;

/**
 * Chức năng của file:
 * AuditContextHolder là class dùng để lưu AuditContext theo từng thread/request hiện tại.
 * File này giúp các class trong cùng một request có thể lấy lại thông tin audit
 * mà không cần truyền AuditContext trực tiếp qua tham số method.
 *
 * Luồng audit log liên quan:
 * - AuditLogAspect tạo AuditContext khi method có @LogAudit được gọi.
 * - AuditLogAspect lưu AuditContext vào AuditContextHolder bằng setContext().
 * - HibernateAuditListener lấy AuditContext bằng getContext() khi entity bị thay đổi.
 * - AuditLogAspect xóa AuditContext bằng clearContext() sau khi request xử lý xong.
 *
 * Liên quan backend:
 * - ThreadLocal giúp mỗi request giữ một AuditContext riêng.
 * - AuditContext chứa actor, action, resource, description, ipAddress, oldValue, newValue và dbModified.
 * - clearContext() giúp tránh việc request sau dùng nhầm dữ liệu audit của request trước.
 *
 * Pseudo code:
 * 1. Tạo ThreadLocal để lưu AuditContext theo từng thread.
 * 2. Khi bắt đầu xử lý audit, lưu AuditContext vào ThreadLocal.
 * 3. Khi HibernateAuditListener cần thông tin audit, lấy AuditContext từ ThreadLocal.
 * 4. Khi xử lý xong request, xóa AuditContext khỏi ThreadLocal.
 */
public class AuditContextHolder {
    private static final ThreadLocal<AuditContext> contextHolder = new ThreadLocal<>();

    /**
     * Lưu AuditContext của request hiện tại vào ThreadLocal.
     * Method này thường được gọi bởi AuditLogAspect trước khi method thật chạy.
     *
     * Pseudo code:
     * 1. Nhận AuditContext từ AuditLogAspect.
     * 2. Lưu AuditContext vào contextHolder.
     * 3. Cho phép các class khác trong cùng thread lấy lại context này.
     */
    public static void setContext(AuditContext context) {
        contextHolder.set(context);
    }

    /**
     * Lấy AuditContext của request hiện tại từ ThreadLocal.
     * Method này thường được gọi bởi HibernateAuditListener khi entity bị insert, update hoặc delete.
     *
     * Pseudo code:
     * 1. Truy cập contextHolder của thread hiện tại.
     * 2. Lấy AuditContext đang được lưu.
     * 3. Trả về AuditContext nếu request hiện tại có audit context.
     * 4. Trả về null nếu request hiện tại không có audit context.
     */
    public static AuditContext getContext() {
        return contextHolder.get();
    }

    /**
     * Xóa AuditContext khỏi ThreadLocal sau khi xử lý xong.
     * Method này giúp tránh rò rỉ dữ liệu audit giữa các request khác nhau.
     *
     * Pseudo code:
     * 1. Truy cập contextHolder của thread hiện tại.
     * 2. Xóa AuditContext đang được lưu.
     * 3. Đảm bảo request tiếp theo không dùng nhầm context của request trước.
     */
    public static void clearContext() {
        contextHolder.remove();
    }
}
package com.pbms.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * File LogAudit.java tạo Annotation dùng để đánh dấu các method cần được ghi audit log.
 * Khi method có annotation này được thực thi, hệ thống có thể ghi lại
 * hành động, tài nguyên bị tác động và mô tả hành động.
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogAudit {
    String action();
    String resource() default "";
    String description() default "";
}


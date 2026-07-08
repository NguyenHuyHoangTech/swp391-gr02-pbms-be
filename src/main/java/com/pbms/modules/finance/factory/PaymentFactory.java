package com.pbms.modules.finance.factory;

import com.pbms.modules.finance.strategy.PaymentStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PaymentFactory áp dụng mẫu thiết kế Factory Pattern.
 * Nhiệm vụ của nó là tự động tìm và trả về đúng lớp xử lý thanh toán (Strategy) 
 * dựa trên mã cổng thanh toán (ví dụ: "PAYOS", "PAYPAL", "VNPAY").
 * 
 * Ưu điểm: Khi cần thêm cổng thanh toán mới, chỉ cần tạo thêm class implements PaymentStrategy,
 * hệ thống sẽ tự động đăng ký vào Map này mà không cần sửa đổi code ở đây.
 */
@Component
public class PaymentFactory {

    private final Map<String, PaymentStrategy> strategies = new HashMap<>();

    @Autowired
    public PaymentFactory(List<PaymentStrategy> paymentStrategies) {
        for (PaymentStrategy strategy : paymentStrategies) {
            this.strategies.put(strategy.getProviderCode().toUpperCase(), strategy);
        }
    }

    /**
     * Lấy ra Strategy xử lý tương ứng với mã provider.
     * @param providerCode Mã cổng thanh toán (vd: "PAYOS")
     * @return Lớp implement PaymentStrategy tương ứng
     * @throws IllegalArgumentException Nếu mã cổng không được hỗ trợ
     */
    public PaymentStrategy getStrategy(String providerCode) {
        PaymentStrategy strategy = strategies.get(providerCode.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported Payment Provider: " + providerCode);
        }
        return strategy;
    }
}


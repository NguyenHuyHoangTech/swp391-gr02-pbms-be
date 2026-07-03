package com.pbms.modules.finance.factory;

import com.pbms.modules.finance.strategy.PaymentStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentFactory {

    private final Map<String, PaymentStrategy> strategies = new HashMap<>();

    @Autowired
    public PaymentFactory(List<PaymentStrategy> paymentStrategies) {
        for (PaymentStrategy strategy : paymentStrategies) {
            this.strategies.put(strategy.getProviderCode().toUpperCase(), strategy);
        }
    }

    public PaymentStrategy getStrategy(String providerCode) {
        PaymentStrategy strategy = strategies.get(providerCode.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported Payment Provider: " + providerCode);
        }
        return strategy;
    }
}


package com.pbms.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

// @Configuration
public class RedisConfig {

    /**
     * Tạo RedisTemplate để backend có thể lưu và đọc dữ liệu từ Redis.
     * RedisTemplate này dùng String cho key và JSON cho value.
     *
     * Pseudo code:
     * 1. Tạo RedisTemplate.
     * 2. Gắn kết nối Redis vào template.
     * 3. Cấu hình key của Redis lưu dưới dạng String.
     * 4. Tạo ObjectMapper để chuyển object Java thành JSON.
     * 5. Cấu hình serializer để lưu value dưới dạng JSON.
     * 6. Gắn serializer cho value và hash value.
     * 7. Khởi tạo template.
     * 8. Trả về RedisTemplate cho Spring sử dụng.
     */
    @Bean
    @SuppressWarnings("deprecation")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.activateDefaultTyping(
            com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class).build(), 
            ObjectMapper.DefaultTyping.NON_FINAL
        );

        org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<Object> serializer =
                new org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer<>(mapper, Object.class);
        
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
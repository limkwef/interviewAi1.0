package org.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }

    public <T> T get(String key, Class<T> clazz) {
        Object val = redisTemplate.opsForValue().get(key);
        return clazz.cast(val);
    }

    public void del(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 按前缀删除 key（使用 SCAN 替代 KEYS，避免阻塞 Redis）
     */
    public void deleteByPrefix(String prefix) {
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(100).build();
            try (var cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    public boolean tryLock(String key, long timeoutSeconds) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(key, "locked", timeoutSeconds, TimeUnit.SECONDS)
        );
    }

    public void unlock(String key) {
        redisTemplate.delete(key);
    }
}

package org.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /** Redis 是否可用（运作中首次异常时置为 false，之后跳过所有操作） */
    private volatile boolean redisAvailable = true;

    private boolean isRedisAvailable() {
        if (!redisAvailable) return false;
        if (redisTemplate == null) {
            redisAvailable = false;
            return false;
        }
        return true;
    }

    public void set(String key, Object value, long ttlSeconds) {
        if (!isRedisAvailable()) return;
        try {
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（set）：{}", e.getMessage());
        }
    }

    public <T> T get(String key, Class<T> clazz) {
        if (!isRedisAvailable()) return null;
        try {
            Object val = redisTemplate.opsForValue().get(key);
            return clazz.cast(val);
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（get）：{}", e.getMessage());
            return null;
        }
    }

    public void del(String key) {
        if (!isRedisAvailable()) return;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（del）：{}", e.getMessage());
        }
    }

    /**
     * 按前缀删除 key（使用 SCAN 替代 KEYS，避免阻塞 Redis）
     */
    public void deleteByPrefix(String prefix) {
        if (!isRedisAvailable()) return;
        try {
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
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（deleteByPrefix）：{}", e.getMessage());
        }
    }

    public boolean tryLock(String key, long timeoutSeconds) {
        if (!isRedisAvailable()) return true; // 无 Redis 时不限制并发
        try {
            return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(key, "locked", timeoutSeconds, TimeUnit.SECONDS)
            );
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（tryLock）：{}", e.getMessage());
            return true;
        }
    }

    public void unlock(String key) {
        if (!isRedisAvailable()) return;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（unlock）：{}", e.getMessage());
        }
    }

    public long increment(String key) {
        if (!isRedisAvailable()) return -1;
        if (stringRedisTemplate == null) return -1;
        try {
            Long result = stringRedisTemplate.opsForValue().increment(key);
            return result != null ? result : -1;
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（increment）：{}", e.getMessage());
            return -1;
        }
    }

    public Integer getInt(String key) {
        if (!isRedisAvailable()) return null;
        if (stringRedisTemplate == null) return null;
        try {
            String val = stringRedisTemplate.opsForValue().get(key);
            if (val != null) {
                return Integer.parseInt(val);
            }
            return null;
        } catch (Exception e) {
            logger.warn("getInt 解析失败，key={}：{}", key, e.getMessage());
            return null;
        }
    }

    public void setInt(String key, int value) {
        if (!isRedisAvailable()) return;
        if (stringRedisTemplate == null) return;
        try {
            stringRedisTemplate.opsForValue().set(key, String.valueOf(value));
        } catch (Exception e) {
            redisAvailable = false;
            logger.warn("Redis 不可用，已降级（setInt）：{}", e.getMessage());
        }
    }
}

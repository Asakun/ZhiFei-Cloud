package com.dji.sample.component.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.Cursor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author sean
 * @version 1.0
 * @date 2022/4/19
 */
@Component
public class RedisOpsUtils {

    private static RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public void setRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        RedisOpsUtils.redisTemplate = redisTemplate;
    }

    /**
     * HSET
     * @param key
     * @param field
     * @param value
     */
    public static void hashSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * HGET
     * @param key
     * @param field
     * @return
     */
    public static Object hashGet(String key, String field) {
        return redisTemplate.opsForHash().get(key, field);
    }

    /**
     * HKEYS
     * @param key
     * @return
     */
    public static Set<Object> hashKeys(String key) {
        return redisTemplate.opsForHash().keys(key);
    }

    public static Map<Object, Object> hashEntries(String key) {
        return redisTemplate.opsForHash().entries(key);
    }

    public static Object hashRandomKey(String key) {
        return redisTemplate.opsForHash().randomKey(key);
    }

    /**
     * HEXISTS
     * @param key
     * @param field
     * @return
     */
    public static boolean hashCheck(String key, String field) {
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    /**
     * HDEL
     * @param key
     * @param fields
     * @return
     */
    public static boolean hashDel(String key, Object[] fields) {
        return redisTemplate.opsForHash().delete(key, fields) > 0;
    }

    /**
     * HLEN
     * @param key
     * @return
     */
    public static long hashLen(String key) {
        return redisTemplate.opsForHash().size(key);
    }

    /**
     * EXPIRE
     * @param key
     * @param timeout
     * @return
     */
    public static boolean expireKey(String key, long timeout) {
        return redisTemplate.expire(key, timeout, TimeUnit.SECONDS);
    }

    /**
     * SET
     * @param key
     * @param value
     */
    public static void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * GET
     * @param key
     * @return
     */
    public static Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * SETEX
     * @param key
     * @param value
     * @param expire
     */
    public static void setWithExpire(String key, Object value, long expire) {
        redisTemplate.opsForValue().set(key, value, expire, TimeUnit.SECONDS);
    }

    /**
     * TTL
     * @param key
     * @return
     */
    public static long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * EXISTS
     * @param key
     * @return
     */
    public static boolean checkExist(String key) {
        return redisTemplate.hasKey(key);
    }

    /**
     * DEL
     * @param key
     * @return
     */
    public static boolean del(String key) {
        return RedisOpsUtils.checkExist(key) && redisTemplate.delete(key);
    }

    /**
     * KEYS
     * @param pattern
     * @return
     */
    public static Set<String> getAllKeys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    /**
     * RPUSH
     * @param key
     * @param value
     */
    public static void listRPush(String key, Object... value) {
        if (value.length == 0) {
            return;
        }
        for (Object val : value) {
            redisTemplate.opsForList().rightPush(key, val);
        }
    }

    public static Object listRPop(String key) {
        return redisTemplate.opsForList().rightPop(key);
    }

    public static Object listLPop(String key) {
        return redisTemplate.opsForList().leftPop(key);
    }

    /**
     * LRANGE
     * @param key
     * @param start
     * @param end
     * @return
     */
    public static List<Object> listGet(String key, long start, long end) {
        return redisTemplate.opsForList().range(key, start, end);
    }

    /**
     * LRANGE
     * @param key
     * @return
     */
    public static List<Object> listGetAll(String key) {
        return redisTemplate.opsForList().range(key, 0, -1);
    }

    /**
     * LLen
     * @param key
     * @return
     */
    public static Long listLen(String key) {
        return redisTemplate.opsForList().size(key);
    }

    public static Long addSetElement(String key, Object value) {
        return redisTemplate.opsForSet().add(key, value);
    }

    public static Long addSetElementWithExpire(String key, Object value, long expire) {
        Long ret = addSetElement(key, value);
        redisTemplate.expire(key, expire, TimeUnit.SECONDS);
        return ret;
    }

    public static Set<Object> getSetAll(String key) {
        return redisTemplate.opsForSet().members(key);
    }

    public static Boolean isSetMember(String key, Object value) {
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public static Object popSetElement(String key) {
        return redisTemplate.opsForSet().pop(key);
    }

    public static Boolean deleteSet(String key) {
        return redisTemplate.delete(key);
    }

    /**
     * ZADD
     * @param key
     * @param value
     * @param score
     */
    public static Boolean zAdd(String key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    /**
     * ZREM
     * @param key
     * @param value
     */
    public static Boolean zRemove(String key, Object... value) {
        return redisTemplate.opsForZSet().remove(key, value) > 0;
    }
    /**
     * ZRANGE
     * @param key
     * @param start
     * @param end
     * @return
     */
    public static Set<Object> zRange(String key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    /**
     * ZRANGE
     * @param key
     * @return
     */
    public static Object zGetMin(String key) {
        Set<Object> objects = zRange(key, 0, 0);
        if (CollectionUtils.isEmpty(objects)) {
            return null;
        }
        return objects.iterator().next();
    }

    /**
     * ZSCORE
     * @param key
     * @param value
     * @return
     */
    public static Double zScore(String key, Object value) {
        return redisTemplate.opsForZSet().score(key, value);
    }

    /**
     * ZINCRBY
     * @param key
     * @param value
     * @param delta
     */
    public static Double zIncrement(String key, Object value, double delta) {
        return redisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    /**
     * 获取以指定前缀开头的所有Redis Key
     * @param prefix 前缀（例如"xxx:"）
     * @return 符合条件的Key集合
     */
    public static Set<String> getKeysByPrefix(String prefix) {
        // 关键：使用通配符*匹配前缀后的所有字符，调用keys()方法
        String pattern = prefix + "*";
        // redisTemplate.keys()返回Set<Serializable>，强转为Set<String>（需确保Key为字符串类型）
        return redisTemplate.keys(pattern);
    }

    /**
     * 生产环境：非阻塞获取指定前缀的所有Key-Value键值对
     * @param prefix 前缀（例如"xxx:"）
     * @return Key-Value键值对Map
     */
    public static Map<String, Object> getKeyValByPrefix(String prefix) {
        // 1. 通过scan获取所有带前缀的Key（非阻塞）
        Set<String> prefixedKeySet = scanKeysByPrefix(prefix);
        Map<String, Object> keyValMap = new HashMap<>();
        if (prefixedKeySet.isEmpty()) {
            return keyValMap;
        }

        // 2. 遍历Key获取Value（批量优化：可选pipeline减少网络交互）
        for (String key : prefixedKeySet) {
            Object value = redisTemplate.opsForValue().get(key);
            // 可选：存储去前缀后的Key
            String keyWithoutPrefix = key.startsWith(prefix) ? key.substring(prefix.length()) : key;
            keyValMap.put(keyWithoutPrefix, value);
        }
        return keyValMap;
    }

    /**
     * 底层：scan命令获取带前缀的Key集合（非阻塞）
     */
    private static Set<String> scanKeysByPrefix(String prefix) {
        Set<String> keySet = new HashSet<>();
        // 使用execute方法获取Redis连接，自动释放无需手动关闭
        redisTemplate.execute((RedisConnection connection) -> {
            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(prefix + "*") // 匹配前缀规则
                    .count(100) // 每次迭代遍历的槽位数量，可根据场景调整（如500、1000）
                    .build();

            Cursor<byte[]> cursor = connection.scan(scanOptions);
            while (cursor.hasNext()) {
                // 字节数组转字符串，需与RedisTemplate的Key序列化方式一致（推荐StringRedisSerializer）
                String key = new String(cursor.next());
                keySet.add(key);
            }
            cursor.close();
            return null;
        });
        return keySet;
    }

    /**
     * 删除所有以指定前缀开头的Redis Key
     * @param prefix 键前缀（如xxx:）
     * @return 删除的key数量
     */
    public static Long deleteKeysByPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return 0L;
        }
        // 2. 用scan遍历匹配的key（替代keys，避免阻塞Redis）
        Set<String> matchKeys = scanKeysByPrefix(prefix);
        if (matchKeys.isEmpty()) {
            return 0L;
        }
        // 3. 批量删除（RedisTemplate的delete支持集合参数，底层是del命令）
        return redisTemplate.delete(matchKeys);
    }
}

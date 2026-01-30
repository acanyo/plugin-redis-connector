package com.xhhao.redisconnector.api;

import com.xhhao.redisconnector.api.internal.RedisClientHolder;
import reactor.core.publisher.Mono;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 静态入口类
 * <p>
 * 提供最简洁的静态方法 API，供其他插件直接调用。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 字符串操作
 * Redis.set("key", "value").subscribe();
 * Redis.get("key").subscribe(System.out::println);
 * Redis.setEx("session:123", "data", 3600).subscribe();
 *
 * // 计数器
 * Redis.incr("page:views").subscribe();
 *
 * // Hash 操作（用户信息）
 * Redis.hset("user:1", "name", "张三").subscribe();
 * Redis.hget("user:1", "name").subscribe(System.out::println);
 *
 * // Sorted Set（排行榜）
 * Redis.zadd("rank:score", 100, "player1").subscribe();
 * Redis.zrevrange("rank:score", 0, 9).subscribe(top10 -> {
 *     top10.forEach(System.out::println);
 * });
 * }</pre>
 *
 * @author Handsome
 * @since 1.0.0
 */
public final class Redis {

    private Redis() {
        // 工具类禁止实例化
    }

    /**
     * 获取 Redis 客户端实例
     */
    private static RedisClient getClient() {
        return RedisClientHolder.getClient();
    }

    /**
     * 检查客户端是否可用，不可用时抛出异常
     */
    private static void checkAvailable() {
        if (getClient() == null) {
            throw new IllegalStateException("Redis 未初始化，请确保 Redis Connector 插件已启动");
        }
    }

    /**
     * 检查 Redis 是否可用
     *
     * @return true 表示已连接且可用
     */
    public static boolean isAvailable() {
        RedisClient client = getClient();
        return client != null && client.isAvailable();
    }

    /**
     * 获取原始 JedisPool 连接池
     * <p>
     * 当封装的 API 无法满足需求时，可以直接获取 JedisPool 进行操作。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * JedisPool pool = Redis.getJedisPool();
     * if (pool != null) {
     *     try (Jedis jedis = pool.getResource()) {
     *         // 执行自定义操作
     *         jedis.lpush("mylist", "value1", "value2");
     *         Pipeline pipeline = jedis.pipelined();
     *         pipeline.set("key1", "value1");
     *         pipeline.set("key2", "value2");
     *         pipeline.sync();
     *     }
     * }
     * }</pre>
     *
     * @return JedisPool 实例，未初始化时返回 null
     */
    public static JedisPool getJedisPool() {
        RedisClient client = getClient();
        return client != null ? client.getJedisPool() : null;
    }

    /**
     * 设置字符串值
     */
    public static Mono<String> set(String key, String value) {
        checkAvailable();
        return getClient().set(key, value);
    }

    /**
     * 设置字符串值并指定过期时间
     */
    public static Mono<String> setEx(String key, String value, long seconds) {
        checkAvailable();
        return getClient().setEx(key, value, seconds);
    }

    /**
     * 获取字符串值
     */
    public static Mono<String> get(String key) {
        checkAvailable();
        return getClient().get(key);
    }

    /**
     * 删除键
     */
    public static Mono<Long> del(String key) {
        checkAvailable();
        return getClient().del(key);
    }

    /**
     * 将键的值自增 1
     */
    public static Mono<Long> incr(String key) {
        checkAvailable();
        return getClient().incr(key);
    }

    /**
     * 将键的值自增指定数值
     */
    public static Mono<Long> incrBy(String key, long increment) {
        checkAvailable();
        return getClient().incrBy(key, increment);
    }

    /**
     * 设置 Hash 字段值
     */
    public static Mono<Long> hset(String key, String field, String value) {
        checkAvailable();
        return getClient().hset(key, field, value);
    }

    /**
     * 获取 Hash 字段值
     */
    public static Mono<String> hget(String key, String field) {
        checkAvailable();
        return getClient().hget(key, field);
    }

    /**
     * 获取 Hash 所有字段和值
     */
    public static Mono<Map<String, String>> hgetAll(String key) {
        checkAvailable();
        return getClient().hgetAll(key);
    }

    /**
     * 向 Set 添加成员
     */
    public static Mono<Long> sadd(String key, String... members) {
        checkAvailable();
        return getClient().sadd(key, members);
    }

    /**
     * 获取 Set 所有成员
     */
    public static Mono<Set<String>> smembers(String key) {
        checkAvailable();
        return getClient().smembers(key);
    }

    /**
     * 判断是否是 Set 成员
     */
    public static Mono<Boolean> sismember(String key, String member) {
        checkAvailable();
        return getClient().sismember(key, member);
    }

    /**
     * 向 Sorted Set 添加成员
     */
    public static Mono<Long> zadd(String key, double score, String member) {
        checkAvailable();
        return getClient().zadd(key, score, member);
    }

    /**
     * 获取 Sorted Set 成员（按分数降序）
     */
    public static Mono<List<String>> zrevrange(String key, long start, long stop) {
        checkAvailable();
        return getClient().zrevrange(key, start, stop);
    }

    /**
     * 增加 Sorted Set 成员的分数
     */
    public static Mono<Double> zincrby(String key, double increment, String member) {
        checkAvailable();
        return getClient().zincrby(key, increment, member);
    }

    /**
     * 判断键是否存在
     */
    public static Mono<Boolean> exists(String key) {
        checkAvailable();
        return getClient().exists(key);
    }

    /**
     * 设置键的过期时间
     */
    public static Mono<Long> expire(String key, long seconds) {
        checkAvailable();
        return getClient().expire(key, seconds);
    }

    /**
     * 获取键的剩余过期时间
     */
    public static Mono<Long> ttl(String key) {
        checkAvailable();
        return getClient().ttl(key);
    }
}

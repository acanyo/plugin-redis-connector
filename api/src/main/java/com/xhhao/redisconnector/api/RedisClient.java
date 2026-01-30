package com.xhhao.redisconnector.api;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 客户端接口
 * <p>
 * 提供 Redis 基本操作能力，所有方法返回 Mono 响应式类型，
 * 在 boundedElastic 调度器上执行，避免阻塞事件循环。
 * </p>
 *
 * @author Handsome
 * @since 1.0.0
 */
public interface RedisClient {

    /**
     * 检查 Redis 连接是否可用
     *
     * @return true 表示已连接且可用
     */
    boolean isAvailable();

    /**
     * 设置字符串值
     *
     * @param key   键
     * @param value 值
     * @return "OK" 表示成功
     */
    Mono<String> set(String key, String value);

    /**
     * 设置字符串值并指定过期时间
     *
     * @param key     键
     * @param value   值
     * @param seconds 过期时间（秒）
     * @return "OK" 表示成功
     */
    Mono<String> setEx(String key, String value, long seconds);

    /**
     * 获取字符串值
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    Mono<String> get(String key);

    /**
     * 删除键
     *
     * @param key 键
     * @return 删除的键数量
     */
    Mono<Long> del(String key);

    /**
     * 将键的值自增 1
     *
     * @param key 键
     * @return 自增后的值
     */
    Mono<Long> incr(String key);

    /**
     * 将键的值自增指定数值
     *
     * @param key       键
     * @param increment 增量
     * @return 自增后的值
     */
    Mono<Long> incrBy(String key, long increment);

    /**
     * 设置 Hash 字段值
     *
     * @param key   键
     * @param field 字段名
     * @param value 字段值
     * @return 1 表示新增字段，0 表示更新已有字段
     */
    Mono<Long> hset(String key, String field, String value);

    /**
     * 获取 Hash 字段值
     *
     * @param key   键
     * @param field 字段名
     * @return 字段值，不存在返回 null
     */
    Mono<String> hget(String key, String field);

    /**
     * 获取 Hash 所有字段和值
     *
     * @param key 键
     * @return 字段-值映射
     */
    Mono<Map<String, String>> hgetAll(String key);

    /**
     * 向 Set 添加成员
     *
     * @param key     键
     * @param members 成员
     * @return 新增的成员数量
     */
    Mono<Long> sadd(String key, String... members);

    /**
     * 获取 Set 所有成员
     *
     * @param key 键
     * @return 成员集合
     */
    Mono<Set<String>> smembers(String key);

    /**
     * 判断是否是 Set 成员
     *
     * @param key    键
     * @param member 成员
     * @return true 表示是成员
     */
    Mono<Boolean> sismember(String key, String member);

    /**
     * 向 Sorted Set 添加成员
     *
     * @param key    键
     * @param score  分数
     * @param member 成员
     * @return 新增的成员数量
     */
    Mono<Long> zadd(String key, double score, String member);

    /**
     * 获取 Sorted Set 成员（按分数降序）
     *
     * @param key   键
     * @param start 起始索引
     * @param stop  结束索引
     * @return 成员列表
     */
    Mono<List<String>> zrevrange(String key, long start, long stop);

    /**
     * 增加 Sorted Set 成员的分数
     *
     * @param key       键
     * @param increment 增量
     * @param member    成员
     * @return 增加后的分数
     */
    Mono<Double> zincrby(String key, double increment, String member);

    /**
     * 判断键是否存在
     *
     * @param key 键
     * @return true 表示存在
     */
    Mono<Boolean> exists(String key);

    /**
     * 设置键的过期时间
     *
     * @param key     键
     * @param seconds 过期时间（秒）
     * @return 1 表示设置成功，0 表示键不存在
     */
    Mono<Long> expire(String key, long seconds);

    /**
     * 获取键的剩余过期时间
     *
     * @param key 键
     * @return 剩余秒数，-1 表示永不过期，-2 表示键不存在
     */
    Mono<Long> ttl(String key);
}

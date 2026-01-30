package com.xhhao.redisconnector.service;

import com.xhhao.redisconnector.api.RedisClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Redis 客户端实现
 * <p>
 * 使用 Jedis 作为底层客户端，避免 PF4J 类加载器冲突。
 * 所有操作在 boundedElastic 调度器上执行，不阻塞事件循环。
 * </p>
 *
 * @author Handsome
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisClientImpl implements RedisClient {

    private static final String LOG_PREFIX = "[RedisConnector]";

    private final Environment environment;

    @Getter
    @Nullable
    private volatile JedisPool jedisPool;

    @Getter
    private volatile boolean available = false;

    public RedisClientImpl(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean isAvailable() {
        return available && jedisPool != null;
    }

    /**
     * 使用 Halo 环境配置初始化 Redis 连接
     */
    public void initialize() {
        if (jedisPool != null) {
            return;
        }

        synchronized (this) {
            if (jedisPool != null) {
                return;
            }

            String haloRedisEnabled = environment.getProperty("halo.redis.enabled", "false");
            if (!"true".equalsIgnoreCase(haloRedisEnabled)) {
                log.info("{} Halo Redis not enabled", LOG_PREFIX);
                return;
            }

            String host = environment.getProperty("spring.data.redis.host", "localhost");
            int port = Integer.parseInt(environment.getProperty("spring.data.redis.port", "6379"));
            String password = environment.getProperty("spring.data.redis.password", "");
            int database = Integer.parseInt(environment.getProperty("spring.data.redis.database", "0"));

            log.info("{} Connecting to Redis: {}:{}/{}", LOG_PREFIX, host, port, database);
            doInitialize(host, port, password, database);
        }
    }

    /**
     * 使用自定义配置初始化 Redis 连接（插件配置）
     *
     * @param host     主机地址
     * @param port     端口
     * @param password 密码
     * @param database 数据库索引
     */
    public void initializeWithConfig(String host, int port, String password, int database) {
        synchronized (this) {
            if (jedisPool != null) {
                shutdown();
            }
            log.info("{} Connecting to Redis (plugin config): {}:{}/{}", LOG_PREFIX, host, port, database);
            doInitialize(host, port, password, database);
        }
    }

    /**
     * 关闭 Redis 连接
     */
    public void shutdown() {
        if (jedisPool != null) {
            try {
                jedisPool.close();
                log.info("{} Redis connection closed", LOG_PREFIX);
            } catch (Exception e) {
                log.error("{} Error closing Redis: {}", LOG_PREFIX, e.getMessage());
            } finally {
                jedisPool = null;
                available = false;
            }
        }
    }

    /**
     * 执行 Jedis 连接池初始化
     */
    private void doInitialize(String host, int port, String password, int database) {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            poolConfig.setTestOnBorrow(true);

            DefaultJedisClientConfig.Builder configBuilder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(10000)
                .socketTimeoutMillis(5000)
                .database(database);

            // Redis 6+ ACL 需要用户名
            if (password != null && !password.isEmpty()) {
                configBuilder.user("default");
                configBuilder.password(password);
            }

            jedisPool = new JedisPool(poolConfig, new HostAndPort(host, port), configBuilder.build());

            // 测试连接
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                available = true;
                log.info("{} Redis connected successfully", LOG_PREFIX);
            }
        } catch (Exception e) {
            log.error("{} Failed to connect Redis: {}", LOG_PREFIX, e.getMessage());
            available = false;
        }
    }

    /**
     * 在 boundedElastic 调度器上执行 Redis 操作
     *
     * @param callable     操作
     * @param defaultValue 失败时的默认值
     * @return Mono 包装的结果
     */
    private <T> Mono<T> execute(Callable<T> callable, T defaultValue) {
        if (!isAvailable()) {
            return Mono.just(defaultValue);
        }
        return Mono.fromCallable(callable)
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(e -> {
                log.error("{} Redis error: {}", LOG_PREFIX, e.getMessage());
                return Mono.just(defaultValue);
            });
    }

    /**
     * 执行 Jedis 操作的模板方法
     */
    private <T> T doWithJedis(java.util.function.Function<Jedis, T> action) {
        try (Jedis jedis = jedisPool.getResource()) {
            return action.apply(jedis);
        }
    }

    @Override
    public Mono<String> set(String key, String value) {
        return execute(() -> doWithJedis(jedis -> jedis.set(key, value)), null);
    }

    @Override
    public Mono<String> setEx(String key, String value, long seconds) {
        return execute(() -> doWithJedis(jedis -> jedis.setex(key, seconds, value)), null);
    }

    @Override
    public Mono<String> get(String key) {
        return execute(() -> doWithJedis(jedis -> jedis.get(key)), null);
    }

    @Override
    public Mono<Long> del(String key) {
        return execute(() -> doWithJedis(jedis -> jedis.del(key)), 0L);
    }

    @Override
    public Mono<Long> incr(String key) {
        return execute(() -> doWithJedis(jedis -> jedis.incr(key)), -1L);
    }

    @Override
    public Mono<Long> incrBy(String key, long increment) {
        return execute(() -> doWithJedis(jedis -> jedis.incrBy(key, increment)), -1L);
    }

    @Override
    public Mono<Long> hset(String key, String field, String value) {
        return execute(() -> doWithJedis(jedis -> jedis.hset(key, field, value)), 0L);
    }

    @Override
    public Mono<String> hget(String key, String field) {
        return execute(() -> doWithJedis(jedis -> jedis.hget(key, field)), null);
    }

    @Override
    public Mono<Map<String, String>> hgetAll(String key) {
        return execute(() -> doWithJedis(jedis -> jedis.hgetAll(key)), Collections.emptyMap());
    }

    @Override
    public Mono<Long> sadd(String key, String... members) {
        return execute(() -> doWithJedis(jedis -> jedis.sadd(key, members)), 0L);
    }

    @Override
    public Mono<Set<String>> smembers(String key) {
        return execute(() -> doWithJedis(jedis -> jedis.smembers(key)), Collections.emptySet());
    }

    @Override
    public Mono<Boolean> sismember(String key, String member) {
        return execute(() -> doWithJedis(jedis -> jedis.sismember(key, member)), false);
    }

    @Override
    public Mono<Long> zadd(String key, double score, String member) {
        return execute(() -> doWithJedis(jedis -> jedis.zadd(key, score, member)), 0L);
    }

    @Override
    public Mono<List<String>> zrevrange(String key, long start, long stop) {
        return execute(() -> doWithJedis(jedis -> jedis.zrevrange(key, start, stop)), Collections.emptyList());
    }

    @Override
    public Mono<Double> zincrby(String key, double increment, String member) {
        return execute(() -> doWithJedis(jedis -> jedis.zincrby(key, increment, member)), 0.0);
    }

    @Override
    public Mono<Boolean> exists(String key) {
        return execute(() -> doWithJedis(jedis -> jedis.exists(key)), false);
    }

    @Override
    public Mono<Long> expire(String key, long seconds) {
        return execute(() -> doWithJedis(jedis -> jedis.expire(key, seconds)), 0L);
    }

    @Override
    public Mono<Long> ttl(String key) {
        return execute(() -> doWithJedis(jedis -> jedis.ttl(key)), -2L);
    }

    /**
     * 获取 Halo Redis 启用状态
     */
    public String getHaloRedisEnabled() {
        return environment.getProperty("halo.redis.enabled", "false");
    }

    /**
     * 获取 Halo 配置的 Redis 主机地址
     */
    public String getRedisHost() {
        return environment.getProperty("spring.data.redis.host", "localhost");
    }

    /**
     * 获取 Halo 配置的 Redis 端口
     */
    public String getRedisPort() {
        return environment.getProperty("spring.data.redis.port", "6379");
    }

    /**
     * 获取 Halo 配置的 Redis 数据库索引
     */
    public String getRedisDatabase() {
        return environment.getProperty("spring.data.redis.database", "0");
    }
}

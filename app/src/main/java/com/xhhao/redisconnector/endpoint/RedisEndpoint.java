package com.xhhao.redisconnector.endpoint;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;

import com.xhhao.redisconnector.service.RedisClientImpl;
import com.xhhao.redisconnector.service.RedisConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import redis.clients.jedis.Jedis;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis 管理端点
 * <p>
 * 提供 Redis 连接状态查询、配置管理、数据浏览等 REST API。
 * </p>
 *
 * @author Handsome
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class RedisEndpoint implements CustomEndpoint {

    private final RedisClientImpl redisClient;
    private final RedisConfigService redisConfigService;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "api.redis.xhhao.com/v1alpha1/Redis";
        return route()
            // 连接管理
            .GET("redis/status", this::getStatus,
                builder -> builder.operationId("GetRedisStatus")
                    .description("获取 Redis 连接状态")
                    .tag(tag)
                    .response(responseBuilder().implementation(Map.class)))
            .GET("redis/test", this::testConnection,
                builder -> builder.operationId("TestRedisConnection")
                    .description("测试 Redis 连接（读写测试）")
                    .tag(tag)
                    .response(responseBuilder().implementation(Map.class)))
            .POST("redis/reconnect", this::reconnect,
                builder -> builder.operationId("ReconnectRedis")
                    .description("重新连接 Redis")
                    .tag(tag)
                    .response(responseBuilder().implementation(Map.class)))
            // 配置管理
            .GET("redis/config", this::getConfig,
                builder -> builder.operationId("GetRedisConfig")
                    .description("获取插件 Redis 配置")
                    .tag(tag)
                    .response(responseBuilder().implementation(Map.class)))
            .POST("redis/config", this::saveConfig,
                builder -> builder.operationId("SaveRedisConfig")
                    .description("保存插件 Redis 配置")
                    .tag(tag)
                    .requestBody(requestBodyBuilder().implementation(Map.class))
                    .response(responseBuilder().implementation(Map.class)))
            // 数据浏览
            .GET("redis/keys", this::listKeys,
                builder -> builder.operationId("ListRedisKeys")
                    .description("列出 Redis 键（支持模糊搜索）")
                    .tag(tag)
                    .response(responseBuilder().implementation(List.class)))
            .GET("redis/data/{key}", this::getData,
                builder -> builder.operationId("GetRedisData")
                    .description("获取指定键的数据")
                    .tag(tag)
                    .response(responseBuilder().implementation(Map.class)))
            .POST("redis/data", this::setData,
                builder -> builder.operationId("SetRedisData")
                    .description("设置键值数据")
                    .tag(tag)
                    .requestBody(requestBodyBuilder().implementation(Map.class))
                    .response(responseBuilder().implementation(Map.class)))
            .DELETE("redis/data/{key}", this::deleteData,
                builder -> builder.operationId("DeleteRedisData")
                    .description("删除指定键")
                    .tag(tag)
                    .response(responseBuilder().implementation(Map.class)))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.redis.xhhao.com/v1alpha1");
    }

    /**
     * 获取 Redis 连接状态
     */
    private Mono<ServerResponse> getStatus(ServerRequest request) {
        return redisConfigService.getFullStatus()
            .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    /**
     * 测试 Redis 连接（执行读写操作验证）
     */
    private Mono<ServerResponse> testConnection(ServerRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("available", redisClient.isAvailable());

        if (!redisClient.isAvailable()) {
            result.put("message", "Redis not available");
            return ServerResponse.ok().bodyValue(result);
        }

        String testKey = "redis-connector:test";
        String testValue = "Hello from Redis Connector!";

        return redisClient.set(testKey, testValue)
            .flatMap(setResult -> {
                result.put("writeSuccess", "OK".equals(setResult));
                return redisClient.get(testKey);
            })
            .flatMap(readValue -> {
                result.put("readValue", readValue);
                result.put("message", testValue.equals(readValue) ? "Redis working!" : "Read/write mismatch");
                return ServerResponse.ok().bodyValue(result);
            })
            .onErrorResume(e -> {
                result.put("error", e.getMessage());
                return ServerResponse.ok().bodyValue(result);
            });
    }

    /**
     * 重新连接 Redis
     */
    private Mono<ServerResponse> reconnect(ServerRequest request) {
        return redisConfigService.reconnect()
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    /**
     * 获取插件 Redis 配置
     */
    private Mono<ServerResponse> getConfig(ServerRequest request) {
        return redisConfigService.getRedisConfig()
            .flatMap(config -> ServerResponse.ok().bodyValue(config));
    }

    /**
     * 保存插件 Redis 配置
     */
    private Mono<ServerResponse> saveConfig(ServerRequest request) {
        return request.bodyToMono(Map.class)
            .flatMap(body -> {
                @SuppressWarnings("unchecked")
                Map<String, String> config = (Map<String, String>) body;
                return redisConfigService.saveRedisConfig(config);
            })
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    /**
     * 列出 Redis 键
     *
     * @param pattern 搜索模式，支持 * 通配符，不包含 * 时自动模糊匹配
     * @param limit   返回数量限制，默认 100
     */
    private Mono<ServerResponse> listKeys(ServerRequest request) {
        String pattern = request.queryParam("pattern").orElse("*");
        int limit = Integer.parseInt(request.queryParam("limit").orElse("100"));

        if (!redisClient.isAvailable()) {
            return ServerResponse.ok().bodyValue(List.of());
        }

        return Mono.fromCallable(() -> {
            var pool = redisClient.getJedisPool();
            if (pool == null) {
                return List.of();
            }

            try (Jedis jedis = pool.getResource()) {
                // 不包含 * 时自动加上模糊匹配
                String searchPattern = pattern.contains("*") ? pattern : "*" + pattern + "*";
                Set<String> keys = jedis.keys(searchPattern);

                List<Map<String, Object>> result = new ArrayList<>();
                int count = 0;
                for (String key : keys) {
                    if (count >= limit) {
                        break;
                    }
                    Map<String, Object> item = new HashMap<>();
                    item.put("key", key);
                    item.put("fullKey", key);
                    item.put("type", jedis.type(key));
                    item.put("ttl", jedis.ttl(key));
                    result.add(item);
                    count++;
                }
                return result;
            }
        }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(keys -> ServerResponse.ok().bodyValue(keys))
            .onErrorResume(e -> ServerResponse.ok().bodyValue(List.of()));
    }

    /**
     * 获取指定键的数据（支持所有数据类型）
     */
    private Mono<ServerResponse> getData(ServerRequest request) {
        String key = request.pathVariable("key");

        if (!redisClient.isAvailable()) {
            return ServerResponse.ok().bodyValue(Map.of("error", "Redis not available"));
        }

        return Mono.fromCallable(() -> {
            var pool = redisClient.getJedisPool();
            if (pool == null) {
                return Map.of("error", "Pool not available");
            }

            try (Jedis jedis = pool.getResource()) {
                String type = jedis.type(key);

                Map<String, Object> result = new HashMap<>();
                result.put("key", key);
                result.put("type", type);
                result.put("ttl", jedis.ttl(key));

                // 根据类型获取值
                Object value = switch (type) {
                    case "string" -> jedis.get(key);
                    case "list" -> jedis.lrange(key, 0, -1);
                    case "set" -> jedis.smembers(key);
                    case "zset" -> jedis.zrangeWithScores(key, 0, -1);
                    case "hash" -> jedis.hgetAll(key);
                    default -> null;
                };
                result.put("value", value);
                return result;
            }
        }).subscribeOn(Schedulers.boundedElastic())
            .flatMap(data -> ServerResponse.ok().bodyValue(data))
            .onErrorResume(e -> ServerResponse.ok().bodyValue(Map.of("error", e.getMessage())));
    }

    /**
     * 设置键值数据（仅支持 string 类型）
     */
    private Mono<ServerResponse> setData(ServerRequest request) {
        return request.bodyToMono(Map.class)
            .flatMap(body -> {
                String key = (String) body.get("key");
                String value = (String) body.get("value");
                Number ttlNum = (Number) body.get("ttl");
                long ttl = ttlNum != null ? ttlNum.longValue() : -1;

                if (key == null || value == null) {
                    return ServerResponse.ok().bodyValue(
                        Map.of("success", false, "message", "key 和 value 不能为空")
                    );
                }

                Mono<String> operation = ttl > 0
                    ? redisClient.setEx(key, value, ttl)
                    : redisClient.set(key, value);

                return operation
                    .flatMap(r -> ServerResponse.ok().bodyValue(
                        Map.of("success", true, "message", "保存成功")
                    ))
                    .onErrorResume(e -> ServerResponse.ok().bodyValue(
                        Map.of("success", false, "message", e.getMessage())
                    ));
            });
    }

    /**
     * 删除指定键
     */
    private Mono<ServerResponse> deleteData(ServerRequest request) {
        String key = request.pathVariable("key");

        return redisClient.del(key)
            .flatMap(count -> ServerResponse.ok().bodyValue(Map.of(
                "success", count > 0,
                "message", count > 0 ? "删除成功" : "Key 不存在"
            )))
            .onErrorResume(e -> ServerResponse.ok().bodyValue(
                Map.of("success", false, "message", e.getMessage())
            ));
    }
}

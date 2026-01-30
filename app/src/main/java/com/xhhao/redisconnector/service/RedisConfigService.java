package com.xhhao.redisconnector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ReactiveExtensionClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis 配置服务
 *
 * @author Handsome
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisConfigService {

    private static final String CONFIG_MAP_NAME = "redis-connector-configmap";
    private static final String REDIS_GROUP = "redis";

    private final Environment environment;
    private final ReactiveExtensionClient client;
    private final RedisClientImpl redisClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取完整状态信息
     */
    public Mono<Map<String, Object>> getFullStatus() {
        return getRedisConfig().map(pluginConfig -> {
            Map<String, Object> status = new HashMap<>();

            // Halo 环境配置
            String haloRedisEnabled = environment.getProperty("halo.redis.enabled", "false");
            String haloHost = environment.getProperty("spring.data.redis.host", "");
            String haloPort = environment.getProperty("spring.data.redis.port", "6379");
            String haloDatabase = environment.getProperty("spring.data.redis.database", "0");

            boolean haloConfigured = "true".equalsIgnoreCase(haloRedisEnabled) && !haloHost.isEmpty();

            status.put("haloRedisEnabled", haloRedisEnabled);
            status.put("haloConfigured", haloConfigured);
            status.put("haloHost", haloHost);
            status.put("haloPort", haloPort);
            status.put("haloDatabase", haloDatabase);

            // 插件配置
            String pluginHost = pluginConfig.getOrDefault("host", "");
            boolean pluginConfigured = !pluginHost.isEmpty();

            status.put("pluginConfigured", pluginConfigured);
            status.put("pluginHost", pluginHost);
            status.put("pluginPort", pluginConfig.getOrDefault("port", "6379"));
            status.put("pluginDatabase", pluginConfig.getOrDefault("database", "0"));

            // 当前使用的配置来源
            String configSource = haloConfigured ? "halo" : (pluginConfigured ? "plugin" : "none");
            status.put("configSource", configSource);

            // 连接状态
            status.put("available", redisClient.isAvailable());

            // 当前实际使用的配置
            if (haloConfigured) {
                status.put("activeHost", haloHost);
                status.put("activePort", haloPort);
                status.put("activeDatabase", haloDatabase);
            } else if (pluginConfigured) {
                status.put("activeHost", pluginHost);
                status.put("activePort", pluginConfig.getOrDefault("port", "6379"));
                status.put("activeDatabase", pluginConfig.getOrDefault("database", "0"));
            }

            return status;
        });
    }

    /**
     * 获取插件 Redis 配置
     */
    public Mono<Map<String, String>> getRedisConfig() {
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME)
            .map(configMap -> {
                Map<String, String> data = configMap.getData();
                if (data == null || !data.containsKey(REDIS_GROUP)) {
                    return new HashMap<String, String>();
                }
                try {
                    return objectMapper.readValue(data.get(REDIS_GROUP),
                        new TypeReference<Map<String, String>>() {});
                } catch (Exception e) {
                    log.error("Failed to parse redis config", e);
                    return new HashMap<String, String>();
                }
            })
            .defaultIfEmpty(new HashMap<>());
    }

    /**
     * 保存插件 Redis 配置
     */
    public Mono<Map<String, Object>> saveRedisConfig(Map<String, String> config) {
        return client.fetch(ConfigMap.class, CONFIG_MAP_NAME)
            .flatMap(configMap -> {
                try {
                    Map<String, String> data = configMap.getData();
                    if (data == null) {
                        data = new HashMap<>();
                        configMap.setData(data);
                    }
                    data.put(REDIS_GROUP, objectMapper.writeValueAsString(config));
                    return client.update(configMap);
                } catch (Exception e) {
                    return Mono.error(e);
                }
            })
            .map(saved -> {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "配置已保存，请点击重新连接");
                return result;
            })
            .onErrorResume(e -> {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "保存失败: " + e.getMessage());
                return Mono.just(result);
            });
    }

    /**
     * 重新连接 Redis
     */
    public Mono<Map<String, Object>> reconnect() {
        return getRedisConfig().map(pluginConfig -> {
            Map<String, Object> result = new HashMap<>();

            // 先关闭现有连接
            redisClient.shutdown();

            // 检查 Halo 配置
            String haloRedisEnabled = environment.getProperty("halo.redis.enabled", "false");
            String haloHost = environment.getProperty("spring.data.redis.host", "");

            boolean useHaloConfig = "true".equalsIgnoreCase(haloRedisEnabled) && !haloHost.isEmpty();

            if (useHaloConfig) {
                // 使用 Halo 配置
                redisClient.initialize();
                result.put("configSource", "halo");
            } else {
                // 使用插件配置
                String host = pluginConfig.getOrDefault("host", "");
                if (host.isEmpty()) {
                    result.put("success", false);
                    result.put("message", "未配置 Redis 连接信息");
                    return result;
                }

                int port = Integer.parseInt(pluginConfig.getOrDefault("port", "6379"));
                String password = pluginConfig.getOrDefault("password", "");
                int database = Integer.parseInt(pluginConfig.getOrDefault("database", "0"));

                redisClient.initializeWithConfig(host, port, password, database);
                result.put("configSource", "plugin");
            }

            result.put("success", redisClient.isAvailable());
            result.put("available", redisClient.isAvailable());
            result.put("message", redisClient.isAvailable() ? "连接成功" : "连接失败");

            return result;
        });
    }
}

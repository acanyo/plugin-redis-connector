package com.xhhao.redisconnector;

import com.xhhao.redisconnector.api.internal.RedisClientHolder;
import com.xhhao.redisconnector.service.RedisClientImpl;
import com.xhhao.redisconnector.service.RedisConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Redis Connector 插件主类
 *
 * @author Handsome
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisConnectorPlugin extends BasePlugin {

    private final RedisClientImpl redisClient;
    private final RedisConfigService redisConfigService;

    public RedisConnectorPlugin(PluginContext pluginContext, 
                                RedisClientImpl redisClient,
                                RedisConfigService redisConfigService) {
        super(pluginContext);
        this.redisClient = redisClient;
        this.redisConfigService = redisConfigService;
    }

    @Override
    public void start() {
        log.info("[RedisConnector] 插件启动中...");
        redisConfigService.reconnect().subscribe(result -> {
            log.info("[RedisConnector] 连接结果: {}", result);
        });
        RedisClientHolder.setClient(redisClient);
    }

    @Override
    public void stop() {
        RedisClientHolder.clear();
        redisClient.shutdown();
    }
}

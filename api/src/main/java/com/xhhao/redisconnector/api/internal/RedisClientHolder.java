package com.xhhao.redisconnector.api.internal;

import com.xhhao.redisconnector.api.RedisClient;

/**
 * RedisClient 持有者（内部使用）
 * <p>
 * 由 app 模块在插件启动时注入实现。
 * </p>
 *
 * @author Handsome
 * @since 1.0.0
 */
public final class RedisClientHolder {

    private static volatile RedisClient client;

    private RedisClientHolder() {
    }

    public static RedisClient getClient() {
        return client;
    }

    public static void setClient(RedisClient client) {
        RedisClientHolder.client = client;
    }

    public static void clear() {
        client = null;
    }
}

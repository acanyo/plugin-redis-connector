# Redis Connector

Halo 2.x Redis 连接器插件，为其他插件提供统一的 Redis 操作能力。

## 功能特性

- 复用 Halo 主程序的 Redis 配置，无需重复配置
- 支持插件独立配置 Redis 连接（当 Halo 未配置时）
- 提供简洁的静态 API，其他插件可直接调用
- 内置数据浏览器，可视化管理 Redis 数据
- 完善的权限控制

## 配置

### 方式一：使用 Halo 环境配置（推荐）

在 Docker 启动参数中添加：

```bash
docker run -d \
  --name halo \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATA_REDIS_DATABASE=0 \
  -e SPRING_DATA_REDIS_PASSWORD=your_password \
  -e HALO_REDIS_ENABLED=true \
  halohub/halo:2.22
```

参考文档：[Halo Redis 配置](https://docs.halo.run/getting-started/install/config/#redis-%E9%9B%86%E6%88%90)

### 方式二：插件配置

在插件设置页面填写 Redis 连接信息。

## 开发

```bash
# 启动开发服务器
./gradlew haloServer

# 构建插件
./gradlew build
```

构建产物位于 `build/libs` 目录。

## 许可证

[GPL-3.0](./LICENSE)

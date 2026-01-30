<script lang="ts" setup>
import { ref, onMounted, computed } from 'vue'
import { VCard, VButton, VSpace, VLoading, Toast, VStatusDot, VEntity, VEntityField, HasPermission } from '@halo-dev/components'
import { FormKit } from '@formkit/vue'
import { axiosInstance } from '@halo-dev/api-client'
import RiRefreshLine from '~icons/ri/refresh-line'
import 'uno.css'

interface RedisStatus {
  available: boolean
  haloConfigured: boolean
  haloRedisEnabled: string
  haloHost: string
  haloPort: string
  haloDatabase: string
  pluginConfigured: boolean
  configSource: 'halo' | 'plugin' | 'none'
  activeHost?: string
  activePort?: string
  activeDatabase?: string
}

interface RedisConfig {
  host: string
  port: string
  password: string
  database: string
}

const loading = ref(true)
const saving = ref(false)
const reconnecting = ref(false)
const status = ref<RedisStatus | null>(null)
const config = ref<RedisConfig>({
  host: '',
  port: '6379',
  password: '',
  database: '0'
})

const API_BASE = '/apis/api.redis.xhhao.com/v1alpha1'

const needPluginConfig = computed(() => status.value && !status.value.haloConfigured)

const fetchStatus = async () => {
  try {
    const { data } = await axiosInstance.get<RedisStatus>(`${API_BASE}/redis/status`)
    status.value = data
  } catch (e) {
    console.error('Failed to fetch status', e)
  }
}

const fetchConfig = async () => {
  try {
    const { data } = await axiosInstance.get<RedisConfig>(`${API_BASE}/redis/config`)
    if (data.host) {
      config.value = data
    }
  } catch (e) {
    console.error('Failed to fetch config', e)
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    const { data } = await axiosInstance.post(`${API_BASE}/redis/config`, config.value)
    if (data.success) {
      Toast.success('配置已保存')
    } else {
      Toast.error(data.message)
    }
  } catch (e: unknown) {
    Toast.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

const saveAndReconnect = async () => {
  await saveConfig()
  await reconnect()
}

const reconnect = async () => {
  reconnecting.value = true
  try {
    const { data } = await axiosInstance.post(`${API_BASE}/redis/reconnect`)
    if (data.success) {
      Toast.success('连接成功')
    } else {
      Toast.error(data.message || '连接失败')
    }
    await fetchStatus()
  } catch (e: unknown) {
    Toast.error(e instanceof Error ? e.message : '连接失败')
  } finally {
    reconnecting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  await Promise.all([fetchStatus(), fetchConfig()])
  loading.value = false
})
</script>

<template>
  <div v-if="loading" class=":uno: flex h-96 items-center justify-center">
    <VLoading />
  </div>

  <div v-else class=":uno: space-y-4 p-4">
    <!-- 连接状态卡片 -->
    <VCard :body-class="['!p-4']">
      <template #header>
        <div class=":uno: flex w-full items-center justify-between bg-gray-50 px-4 py-3">
          <div class=":uno: flex items-center gap-2">
            <span class=":uno: text-sm font-medium">连接状态</span>
            <VStatusDot v-if="status?.available" state="success" text="已连接" />
            <VStatusDot v-else state="error" text="未连接" />
          </div>
          <HasPermission :permissions="['plugin:redis-connector:manage']">
            <VButton size="sm" :loading="reconnecting" @click="reconnect">
              <template #icon><RiRefreshLine /></template>
              重新连接
            </VButton>
          </HasPermission>
        </div>
      </template>

      <VEntity v-if="status?.available">
        <template #start>
          <VEntityField :description="status?.configSource === 'halo' ? 'Halo 环境配置' : '插件配置'">
            <template #title>配置来源</template>
          </VEntityField>
        </template>
        <template #end>
          <VEntityField :description="`${status?.activeHost}:${status?.activePort}/${status?.activeDatabase}`">
            <template #title>连接地址</template>
          </VEntityField>
        </template>
      </VEntity>
      <div v-else class=":uno: text-sm text-gray-500">
        Redis 未连接，请检查配置
      </div>
    </VCard>

    <!-- Halo 环境配置 -->
    <VCard :body-class="['!p-4']">
      <template #header>
        <div class=":uno: block w-full bg-gray-50 px-4 py-3">
          <span class=":uno: text-sm font-medium">Halo 环境配置</span>
        </div>
      </template>

      <div class=":uno: grid grid-cols-2 gap-3">
        <div class=":uno: flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
          <span class=":uno: text-sm text-gray-600">Redis 启用</span>
          <VStatusDot 
            :state="status?.haloRedisEnabled === 'true' ? 'success' : 'default'" 
            :text="status?.haloRedisEnabled === 'true' ? '是' : '否'" 
          />
        </div>
        <div class=":uno: flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
          <span class=":uno: text-sm text-gray-600">主机地址</span>
          <span class=":uno: text-sm font-medium">{{ status?.haloHost || '未配置' }}</span>
        </div>
        <div class=":uno: flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
          <span class=":uno: text-sm text-gray-600">端口</span>
          <span class=":uno: text-sm font-medium">{{ status?.haloPort }}</span>
        </div>
        <div class=":uno: flex items-center justify-between rounded-lg bg-gray-50 px-3 py-2">
          <span class=":uno: text-sm text-gray-600">数据库</span>
          <span class=":uno: text-sm font-medium">{{ status?.haloDatabase }}</span>
        </div>
      </div>

      <div v-if="status?.haloConfigured" class=":uno: mt-4 rounded-lg bg-green-50 p-3">
        <p class=":uno: text-sm text-green-700">
          ✓ Halo 已配置 Redis，将优先使用 Halo 配置
        </p>
      </div>
      <div v-else class=":uno: mt-4 rounded-lg bg-amber-50 p-3">
        <p class=":uno: text-sm text-amber-700">
          Halo 未配置 Redis，请在下方配置或参考
          <a href="https://docs.halo.run/getting-started/install/config/#redis-%E9%9B%86%E6%88%90" target="_blank" class=":uno: text-blue-600 underline">官方文档</a>
          在 Docker 启动参数中添加
        </p>
      </div>
    </VCard>

    <!-- 插件配置 -->
    <VCard :body-class="['!p-4']" :class="{ 'ring-2 ring-blue-500': needPluginConfig }">
      <template #header>
        <div class=":uno: flex w-full items-center justify-between bg-gray-50 px-4 py-3">
          <span class=":uno: text-sm font-medium">插件 Redis 配置</span>
          <HasPermission :permissions="['plugin:redis-connector:manage']">
            <VSpace>
              <VButton size="sm" :loading="saving" @click="saveConfig">保存</VButton>
              <VButton size="sm" type="primary" :loading="reconnecting || saving" @click="saveAndReconnect">
                保存并连接
              </VButton>
            </VSpace>
          </HasPermission>
        </div>
      </template>

      <div class=":uno: mb-4 text-sm text-gray-500">
        {{ status?.haloConfigured ? 'Halo 已配置，以下配置作为备用' : '请填写 Redis 连接信息' }}
      </div>

      <FormKit type="form" :actions="false" v-model="config">
        <FormKit
          type="text"
          name="host"
          label="主机地址"
          placeholder="localhost 或 192.168.1.100"
          validation="required"
          validation-visibility="blur"
        />
        <FormKit
          type="text"
          name="port"
          label="端口"
          placeholder="6379"
        />
        <FormKit
          type="password"
          name="password"
          label="密码"
          placeholder="留空表示无密码"
        />
        <FormKit
          type="text"
          name="database"
          label="数据库索引"
          placeholder="0"
        />
      </FormKit>
    </VCard>
  </div>
</template>

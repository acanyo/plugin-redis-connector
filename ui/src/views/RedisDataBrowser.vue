<script lang="ts" setup>
import { ref, onMounted } from 'vue'
import { VCard, VButton, VSpace, VLoading, Toast, VEmpty, VEntity, VEntityField, VEntityContainer, VModal, Dialog, VDropdownItem } from '@halo-dev/components'
import { FormKit } from '@formkit/vue'
import { axiosInstance } from '@halo-dev/api-client'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiAddLine from '~icons/ri/add-line'
import 'uno.css'

interface RedisKey {
  key: string
  fullKey: string
  type: string
  ttl: number
}

interface RedisData {
  key: string
  type: string
  ttl: number
  value: unknown
}

const API_BASE = '/apis/api.redis.xhhao.com/v1alpha1'

const loading = ref(false)
const keys = ref<RedisKey[]>([])
const pattern = ref('')
const selectedKey = ref<RedisData | null>(null)
const showDetailModal = ref(false)
const showAddModal = ref(false)
const newData = ref({ key: '', value: '', ttl: '' })

const fetchKeys = async () => {
  loading.value = true
  try {
    const { data } = await axiosInstance.get<RedisKey[]>(`${API_BASE}/redis/keys`, {
      params: { pattern: pattern.value || '*', limit: 100 }
    })
    keys.value = data
  } catch (e) {
    console.error('Failed to fetch keys', e)
    Toast.error('获取 Key 列表失败')
  } finally {
    loading.value = false
  }
}

const viewKey = async (item: RedisKey) => {
  try {
    const { data } = await axiosInstance.get<RedisData>(`${API_BASE}/redis/data/${encodeURIComponent(item.key)}`)
    selectedKey.value = data
    showDetailModal.value = true
  } catch (e) {
    Toast.error('获取数据失败')
  }
}

const deleteKey = (item: RedisKey) => {
  Dialog.warning({
    title: '确认删除',
    description: `确定要删除 Key "${item.key}" 吗？`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      try {
        const { data } = await axiosInstance.delete(`${API_BASE}/redis/data/${encodeURIComponent(item.key)}`)
        if (data.success) {
          Toast.success('删除成功')
          await fetchKeys()
        } else {
          Toast.error(data.message)
        }
      } catch (e) {
        Toast.error('删除失败')
      }
    }
  })
}

const saveNewData = async () => {
  if (!newData.value.key || !newData.value.value) {
    Toast.error('Key 和 Value 不能为空')
    return
  }
  try {
    const payload: Record<string, unknown> = {
      key: newData.value.key,
      value: newData.value.value
    }
    if (newData.value.ttl) {
      payload.ttl = parseInt(newData.value.ttl)
    }
    const { data } = await axiosInstance.post(`${API_BASE}/redis/data`, payload)
    if (data.success) {
      Toast.success('保存成功')
      showAddModal.value = false
      newData.value = { key: '', value: '', ttl: '' }
      await fetchKeys()
    } else {
      Toast.error(data.message)
    }
  } catch (e) {
    Toast.error('保存失败')
  }
}

const formatValue = (data: RedisData | null): string => {
  if (!data) return ''
  const val = data.value
  if (typeof val === 'string') return val
  return JSON.stringify(val, null, 2)
}

const formatTtl = (ttl: number): string => {
  if (ttl === -1) return '永久'
  if (ttl === -2) return '已过期'
  return `${ttl}s`
}

const getTypeColor = (type: string) => {
  const colors: Record<string, string> = {
    string: 'bg-blue-50 text-blue-600',
    list: 'bg-green-50 text-green-600',
    set: 'bg-purple-50 text-purple-600',
    zset: 'bg-orange-50 text-orange-600',
    hash: 'bg-pink-50 text-pink-600'
  }
  return colors[type] || 'bg-gray-50 text-gray-600'
}

onMounted(() => {
  fetchKeys()
})
</script>

<template>
  <div class=":uno: p-4">
    <!-- 提示信息 -->
    <div class=":uno: mb-4 rounded-lg bg-blue-50 p-3">
      <p class=":uno: text-sm text-blue-700">
        💡 专业的 Redis 可视化管理推荐使用
        <a href="https://github.com/qishibo/AnotherRedisDesktopManager" target="_blank" class=":uno: font-medium underline">Another Redis Desktop Manager</a>
      </p>
    </div>

    <!-- Key 列表 -->
    <VCard :body-class="['!p-0']">
      <template #header>
        <div class=":uno: flex w-full items-center justify-between bg-gray-50 px-4 py-3">
          <div class=":uno: flex items-center gap-3">
            <span class=":uno: text-sm font-medium">Key 列表</span>
            <span class=":uno: text-xs text-gray-400">共 {{ keys.length }} 条</span>
          </div>
          <div class=":uno: flex items-center gap-2">
            <input
              v-model="pattern"
              type="text"
              placeholder="搜索关键词"
              class=":uno: h-7 w-40 rounded border border-gray-200 px-2 text-xs focus:border-blue-400 focus:outline-none"
              @keyup.enter="fetchKeys"
            />
            <VButton size="sm" @click="fetchKeys" :loading="loading">
              <template #icon><RiRefreshLine /></template>
              刷新
            </VButton>
            <HasPermission :permissions="['plugin:redis-connector:manage']">
              <VButton size="sm" type="primary" @click="showAddModal = true">
                <template #icon><RiAddLine /></template>
                新增
              </VButton>
            </HasPermission>
          </div>
        </div>
      </template>

      <VLoading v-if="loading" />

      <VEmpty v-else-if="!keys.length" message="暂无数据" title="没有找到 Key" />

      <VEntityContainer v-else>
        <VEntity v-for="item in keys" :key="item.fullKey">
          <template #start>
            <VEntityField :title="item.key" />
          </template>
          <template #end>
            <VEntityField>
              <template #description>
                <span :class="[':uno: rounded px-1.5 py-0.5 text-xs font-medium', getTypeColor(item.type)]">
                  {{ item.type }}
                </span>
              </template>
            </VEntityField>
            <VEntityField :description="formatTtl(item.ttl)" />
          </template>
          <template #dropdownItems>
            <VDropdownItem @click="viewKey(item)">查看详情</VDropdownItem>
            <HasPermission :permissions="['plugin:redis-connector:manage']">
              <VDropdownItem type="danger" @click="deleteKey(item)">删除</VDropdownItem>
            </HasPermission>
          </template>
        </VEntity>
      </VEntityContainer>
    </VCard>

    <!-- 查看详情弹窗 -->
    <VModal v-model:visible="showDetailModal" title="Key 详情" :width="550">
      <div v-if="selectedKey" class=":uno: space-y-3">
        <div class=":uno: flex items-center gap-4 text-sm">
          <div class=":uno: flex items-center gap-2">
            <span class=":uno: text-gray-500">Key:</span>
            <span class=":uno: font-medium">{{ selectedKey.key }}</span>
          </div>
          <span :class="[':uno: rounded px-1.5 py-0.5 text-xs', getTypeColor(selectedKey.type)]">
            {{ selectedKey.type }}
          </span>
          <div class=":uno: flex items-center gap-1 text-gray-500">
            <span>TTL:</span>
            <span>{{ formatTtl(selectedKey.ttl) }}</span>
          </div>
        </div>
        <pre class=":uno: max-h-72 overflow-auto rounded bg-gray-50 p-3 text-xs leading-relaxed">{{ formatValue(selectedKey) }}</pre>
      </div>
      <template #footer>
        <VButton @click="showDetailModal = false">关闭</VButton>
      </template>
    </VModal>

    <!-- 新增弹窗 -->
    <VModal v-model:visible="showAddModal" title="新增 Key" :width="450">
      <FormKit type="form" :actions="false" v-model="newData">
        <FormKit type="text" name="key" label="Key" placeholder="输入 Key 名称" validation="required" />
        <FormKit type="textarea" name="value" label="Value" placeholder="输入值" rows="3" validation="required" />
        <FormKit type="text" name="ttl" label="TTL（秒）" placeholder="留空表示永久" />
      </FormKit>
      <template #footer>
        <VSpace>
          <VButton @click="showAddModal = false">取消</VButton>
          <VButton type="primary" @click="saveNewData">保存</VButton>
        </VSpace>
      </template>
    </VModal>
  </div>
</template>

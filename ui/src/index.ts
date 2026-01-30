import { definePlugin } from '@halo-dev/ui-shared'
import type { PluginTab } from '@halo-dev/ui-shared'
import RedisSettings from './views/RedisSettings.vue'
import RedisDataBrowser from './views/RedisDataBrowser.vue'
import { markRaw } from 'vue'
import RiDatabase2Line from '~icons/ri/database-2-line'
import 'uno.css'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'ToolsRoot',
      route: {
        path: '/redis-connector',
        name: 'RedisConnector',
        redirect: '/plugins/redis-connector?tab=redis-settings',
        meta: {
          title: 'Redis 连接',
          description: 'Redis 连接器，管理 Redis 配置和数据',
          searchable: true,
          permissions: ['plugin:redis-connector:view'],
          menu: {
            name: 'Redis 连接',
            icon: markRaw(RiDatabase2Line),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {
    'plugin:self:tabs:create': (): PluginTab[] => {
      return [
        {
          id: 'redis-settings',
          label: 'Redis 配置',
          component: markRaw(RedisSettings),
          permissions: ['plugin:redis-connector:view'],
        },
        {
          id: 'redis-data',
          label: '数据浏览',
          component: markRaw(RedisDataBrowser),
          permissions: ['plugin:redis-connector:view'],
        },
      ]
    },
  },
})

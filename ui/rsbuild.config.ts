import { rsbuildConfig } from '@halo-dev/ui-plugin-bundler-kit'
import Icons from 'unplugin-icons/rspack'
import { pluginSass } from '@rsbuild/plugin-sass'
import { UnoCSSRspackPlugin } from '@unocss/webpack/rspack'
import type { RsbuildConfig } from '@rsbuild/core'
import { resolve } from 'path'

export default rsbuildConfig({
  manifestPath: '../app/src/main/resources/plugin.yaml',
  rsbuild: {
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    plugins: [pluginSass()],
    tools: {
      rspack: {
        plugins: [Icons({ compiler: 'vue3' }), UnoCSSRspackPlugin()],
      },
    },
  },
}) as RsbuildConfig

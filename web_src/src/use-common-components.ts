import { App, DefineComponent } from 'vue'
import SvgIcon from './components/svgIcon.vue'
import DronePropertiesPanel from './components/drone-properties/DronePropertiesPanel.vue'

const components: Record<string, DefineComponent<{}, {}, any>> = {
  SvgIcon,
  DronePropertiesPanel: DronePropertiesPanel as any
}

export const CommonComponents = {
  install (app: App): void {
    Object.keys(components).forEach(name => {
      app.component(name, components[name])
    })
  }
}

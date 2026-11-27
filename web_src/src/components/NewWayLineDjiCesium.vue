<template>
  <div ref="viewerContainer" class="h-full relative">
    <!-- 提示用户设置参考起飞点 -->
    <div v-if="!isPreviewMode && !takeoffPoint"
      class="absolute top-4 left-1/2 -translate-x-1/2 z-[100] bg-yellow-500/90 backdrop-blur text-black px-6 py-2.5 rounded-lg shadow-2xl border border-yellow-400 flex items-center gap-2 font-medium text-sm animate-pulse">
      <svg class="w-5 h-5 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
        <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
      </svg>
      <span>请在地图上右键，选择【设为参考起飞点】来开始规划航线</span>
    </div>

    <div ref="cesiumContainerRef" class="h-full w-full"></div>

        <!-- Realtime height tooltip during vertical drag -->
        <div v-if="!isPreviewMode && waypointDragState.isDragging && waypointDragState.mode === 'vertical'" class="drag-height-tooltip"
          :style="{ top: (dragTooltipPos.y - 50) + 'px', left: (dragTooltipPos.x + 20) + 'px' }">
          <div class="tooltip-content">
            <div class="tooltip-value" v-if="takeoffPoint">相对高: {{ (dragCurrentHeight - (takeoffPoint.height || 0)).toFixed(1) }}m</div>
            <div class="tooltip-value" v-else>{{ dragCurrentHeight.toFixed(1) }}m</div>
            <div class="tooltip-delta" :class="dragHeightDelta >= 0 ? 'positive' : 'negative'">
              {{ dragHeightDelta >= 0 ? '+' : '' }}{{ dragHeightDelta.toFixed(1) }}m
            </div>
          </div>
        </div>

        <!-- Drag mode badge -->
        <div v-if="!isPreviewMode && waypointDragState.isDragging" class="drag-mode-badge"
          :class="waypointDragState.mode === 'vertical' ? 'mode-vertical' : 'mode-horizontal'">
          <svg v-if="waypointDragState.mode === 'vertical'" xmlns="http://www.w3.org/2000/svg" width="14" height="14"
            viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"
            stroke-linejoin="round">
            <path d="M12 2v20M5 9l7-7 7 7M5 15l7 7 7-7" />
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <path d="M2 12h20M9 5l-7 7 7 7M15 5l7 7-7 7" />
          </svg>
          {{ waypointDragState.mode === 'vertical' ? 'ALT' : 'XY' }}
        </div>

        <!-- 航点动作编辑面板（使用抽屉，从右侧弹出） -->
        <a-drawer
          :visible="!isPreviewMode && actionPopup.visible"
          @close="closeActionPopup"
          placement="right"
          :width="420"
          :mask="false"
          :maskClosable="false"
          :closable="false"
          class="action-drawer-panel"
        >
          <div v-if="actionPopupPoint" class="action-popup-inner">
            <div class="action-popup-header">
              <span class="popup-title">航点{{ actionPopup.pointIndex + 1 }} · 到达动作</span>
              <CloseOutlined class="popup-close" @click="closeActionPopup" />
            </div>

            <!-- ⚡ 一键组合快捷操作横幅：录像 + 悬停 + 拍照 + 停录 -->
            <div class="combo-action-banner" @click="oneClickAddRecordPhotoCombo" title="一键添加：开始录像 + 悬停5秒 + 拍照，并在其后自动创建同经纬度停录航点">
              <div class="combo-banner-left">
                <div class="combo-banner-icon">⚡</div>
                <div class="combo-banner-text">
                  <div class="combo-banner-title">快捷绑定「录像+悬停+拍照+停录」</div>
                  <div class="combo-banner-desc">自动绑定 #{{ getExistingRecordCount() + 1 }} 组号 · 同经纬度闭环</div>
                </div>
              </div>
              <div class="combo-banner-badge">一键生成 ➔</div>
            </div>

            <!-- 快捷添加按钮行 -->
            <div class="quick-actions">
              <div v-for="opt in actionActuatorFuncOptions" :key="opt.value" class="quick-action-chip"
                @click="quickAddAction(opt.value)" :title="opt.label">
                <span class="chip-icon">{{ actionIconMap[opt.value] || '⚡' }}</span>
                <span class="chip-label">{{ opt.label }}</span>
              </div>
            </div>
            <!-- 已添加的动作（垂直卡片列表） -->
            <div v-if="actionPopupGroup && actionPopupGroup.actions.length > 0" class="action-cards-col">
              <div v-for="(action, aIdx) in actionPopupGroup.actions" :key="aIdx"
                :id="`action-card-${actionPopup.pointIndex}-${action.action_index}`"
                :ref="el => setCardRef(el, aIdx)"
                class="action-card"
                :class="{ 'active': selectedActionIndex === action.action_index }"
                @click="selectedActionIndex = action.action_index"
              >
                <div class="card-top">
                  <div class="flex items-center gap-1.5">
                    <span class="card-index-badge">{{ aIdx + 1 }}</span>
                    <span class="card-icon">{{ actionIconMap[action.action_func] || '⚡' }}</span>
                    <span class="card-name">{{ actionLabelMap[action.action_func] || action.action_func }}</span>
                  </div>
                  <CloseOutlined class="card-remove" @click="removePopupAction(aIdx)" />
                </div>
                <!-- 参数编辑区 -->
                <div class="card-params"
                  v-if="['hover', 'zoom', 'gimbalRotate', 'rotateYaw', 'takePhoto', 'startRecord', 'stopRecord', 'panoShot', 'customDirName', 'orientedShoot'].includes(action.action_func)">
                  
                  <template v-if="action.action_func === 'hover'">
                    <div class="flex items-center gap-2 w-full">
                      <span class="card-unit flex-shrink-0">悬停时间</span>
                      <a-input-number v-model:value="action.hover_time" size="small" :min="0" :step="1" class="card-input-field" />
                      <span class="card-unit flex-shrink-0">秒</span>
                    </div>
                  </template>

                  <template v-if="action.action_func === 'zoom'">
                    <div class="flex items-center gap-2 w-full">
                      <span class="card-unit flex-shrink-0">变焦倍数</span>
                      <a-input-number v-model:value="action.zoom" size="small" :min="1" :step="1" class="card-input-field" @change="() => onActionParamChange(action)" />
                      <span class="card-unit flex-shrink-0">倍</span>
                    </div>
                  </template>

                  <template v-if="action.action_func === 'gimbalRotate'">
                    <div class="flex flex-col gap-2 w-full">
                      <div class="flex items-center justify-between">
                        <span class="card-unit">云台偏航(相对正北)</span>
                        <div class="flex items-center gap-1">
                          <a-input-number v-model:value="action.gimbal_yaw_rotate_angle" size="small" :min="-180" :max="180" :step="0.1" class="card-input-field" @change="() => onActionParamChange(action)" />
                          <span class="card-unit">°</span>
                        </div>
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="card-unit">云台俯仰</span>
                        <div class="flex items-center gap-1">
                          <a-input-number v-model:value="action.gimbal_pitch_rotate_angle" size="small" :min="-90" :max="35" :step="0.1" class="card-input-field" @change="() => onActionParamChange(action)" />
                          <span class="card-unit">°</span>
                        </div>
                      </div>
                    </div>
                  </template>

                  <template v-if="action.action_func === 'rotateYaw'">
                    <div class="flex items-center gap-2 w-full">
                      <span class="card-unit flex-shrink-0">飞行器偏航(相对正北)</span>
                      <a-input-number v-model:value="action.aircraft_heading" size="small" :min="-180" :max="180" :step="0.1" class="card-input-field" @change="() => onActionParamChange(action)" />
                      <span class="card-unit flex-shrink-0">°</span>
                    </div>
                  </template>

                  <template v-if="['takePhoto', 'startRecord', 'panoShot', 'orientedShoot'].includes(action.action_func)">
                    <div class="flex flex-col gap-2 w-full">
                      <div class="flex items-center justify-between">
                        <span class="card-unit">全局存储格式</span>
                        <a-switch v-model:checked="action.use_global_image_format" :checkedValue="1" :unCheckedValue="0" size="small" />
                      </div>
                      <div v-if="['takePhoto', 'startRecord', 'orientedShoot'].includes(action.action_func) && action.use_global_image_format === 0" class="flex items-center justify-between">
                        <span class="card-unit">相机镜头</span>
                        <a-select v-model:value="action.image_format" size="small" style="width: 120px">
                          <a-select-option value="visable,ir">可见光+红外</a-select-option>
                          <a-select-option value="visable">可见光</a-select-option>
                          <a-select-option value="ir">红外</a-select-option>
                        </a-select>
                      </div>
                      <!-- 录像动作：支持文件命名后缀，自动编号，以及一键快捷添加同经纬度停录航点 -->
                      <div v-if="action.action_func === 'startRecord'" class="flex flex-col gap-2 border-t border-gray-800/80 pt-2.5 mt-1">
                        <div class="flex items-center justify-between">
                          <span class="card-unit font-medium text-blue-400">文件命名后缀</span>
                          <a-input v-model:value="action.file_suffix" size="small" placeholder="如: #1" class="card-input-text" style="width: 210px;" />
                        </div>
                        <div class="flex justify-end">
                          <a-button type="primary" ghost size="small" class="text-xs" @click="() => quickAddStopRecordWaypoint(actionPopup.pointIndex)">
                            ➕ 快捷添加同经纬度停录航点
                          </a-button>
                        </div>
                      </div>

                      <!-- 拍照/定向拍照动作：文件命名后缀 -->
                      <div v-if="['takePhoto', 'orientedShoot'].includes(action.action_func)" 
                        class="flex flex-col gap-2 border-t border-gray-800/80 pt-2.5 mt-1">
                        <div class="flex items-center justify-between">
                          <span class="card-unit font-medium text-blue-400">文件命名后缀</span>
                        </div>
                        <div class="flex flex-col gap-1">
                          <a-input v-model:value="action.file_suffix" size="small" placeholder="zxsl-YYYYMMDDhhmm-" class="card-input-text" />
                        </div>
                      </div>
                    </div>
                  </template>

                  <!-- 停录动作：提示与一键对齐录像航点经纬度 -->
                  <template v-if="action.action_func === 'stopRecord'">
                    <div class="flex flex-col gap-2 w-full pt-1">
                      <div class="flex items-center justify-between">
                        <span class="card-unit text-xs text-gray-400">关联录像航点</span>
                        <span class="text-xs text-blue-400 font-mono">
                          {{ getPrecedingStartRecordInfo(actionPopup.pointIndex).desc }}
                        </span>
                      </div>
                      <div v-if="getPrecedingStartRecordInfo(actionPopup.pointIndex).hasStart">
                        <div v-if="isCoordSameAsStartRecord(actionPopup.pointIndex)" class="flex items-center gap-1.5 text-xs text-green-400 bg-green-950/40 p-1.5 rounded border border-green-800/60">
                          <span>✓ 经纬度已与录像航点一致</span>
                        </div>
                        <div v-else class="flex flex-col gap-1.5 bg-amber-950/30 p-2 rounded border border-amber-800/50">
                          <span class="text-[11px] text-amber-300">提示：停止录像航点最好与录像航点经纬度一致</span>
                          <a-button type="dashed" size="small" class="text-xs text-amber-300 border-amber-600 hover:text-amber-200" @click="() => alignStopRecordWithStart(actionPopup.pointIndex)">
                            📍 对齐录像航点经纬度
                          </a-button>
                        </div>
                      </div>
                    </div>
                  </template>

                  <template v-if="action.action_func === 'customDirName'">
                    <div class="flex items-center gap-2 w-full">
                      <span class="card-unit flex-shrink-0">文件夹名称</span>
                      <a-input v-model:value="action.directory_name" size="small" placeholder="输入目录名" class="card-input-text" />
                    </div>
                  </template>

                  <template v-if="action.action_func === 'orientedShoot'">
                    <div class="flex flex-col gap-2 w-full">
                      <div class="flex items-center justify-between">
                        <span class="card-unit">云台偏航(相对正北)</span>
                        <div class="flex items-center gap-1">
                          <a-input-number v-model:value="action.gimbal_yaw_rotate_angle" size="small" :min="-180" :max="180" :step="0.1" class="card-input-field" @change="() => onActionParamChange(action)" />
                          <span class="card-unit">°</span>
                        </div>
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="card-unit">云台俯仰</span>
                        <div class="flex items-center gap-1">
                          <a-input-number v-model:value="action.gimbal_pitch_rotate_angle" size="small" :min="-90" :max="35" :step="0.1" class="card-input-field" @change="() => onActionParamChange(action)" />
                          <span class="card-unit">°</span>
                        </div>
                      </div>
                      <div class="flex items-center justify-between">
                        <span class="card-unit">相机变焦</span>
                        <div class="flex items-center gap-1">
                          <a-input-number v-model:value="action.zoom" size="small" :min="1" :step="1" class="card-input-field" @change="() => onActionParamChange(action)" />
                          <span class="card-unit">倍</span>
                        </div>
                      </div>
                    </div>
                  </template>

                </div>
              </div>
            </div>
            <div v-else class="action-cards-empty">点击上方按钮添加动作</div>
          </div>
        </a-drawer>

        <div v-if="!isPreviewMode && contextMenu.visible"
          class="absolute bg-gray-900 border border-gray-700 text-white rounded-lg p-3 shadow-xl z-[999] min-w-[260px]"
          :style="{ top: contextMenu.y + 'px', left: contextMenu.x + 'px' }">
          <div class="space-y-1 py-1">
            <div v-if="!takeoffPoint" @click="setTakeoffPointFromMenu"
              class="px-4 py-2 hover:bg-gray-700 cursor-pointer flex items-center gap-2 text-sm transition-colors text-blue-400 font-semibold border-b border-gray-800 pb-2 mb-1">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="w-4 h-4">
                <path d="M12 2a8 8 0 0 0-8 8c0 5.25 8 12 8 12s8-6.75 8-12a8 8 0 0 0-8-8z"/>
                <circle cx="12" cy="10" r="3"/>
              </svg>
              <span>设为参考起飞点</span>
            </div>

            <div @click="addWaypoint"
              class="px-4 py-2 hover:bg-gray-700 cursor-pointer flex items-center gap-2 text-sm transition-colors"
              :class="{ 'opacity-50 cursor-not-allowed': !takeoffPoint }">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 5v14M5 12h14" />
              </svg>
              <span>追加航点</span>
            </div>
          </div>
        </div>

        <div v-if="!isPreviewMode && waypointMenu.visible"
          class="absolute bg-gray-900 border border-gray-700 text-white rounded-lg shadow-xl z-[999] min-w-[180px]"
          :style="{ top: waypointMenu.y + 'px', left: waypointMenu.x + 'px' }">
          <div class="py-1">
            <div class="px-4 py-2 text-xs text-gray-400 border-b border-gray-600">
              <div>航点 {{ waypointMenu.waypointIndex + 1 }}</div>
              <div class="mt-1">海拔: {{ waypointMenu.height.toFixed(1) }}m</div>
            </div>
          </div>
        </div>



    <!-- 图例开关 -->
    <div class="flight-range-legend" :class="{ 'drawer-open': actionPopup.visible }">
      <div class="legend-item">
        <span class="legend-color"
          style="background: linear-gradient(to right, rgba(0,255,0,0.3), rgba(255,255,0,0.3), rgba(255,0,0,0.3));"></span>
        <span class="legend-label">预估机场可飞行范围</span>
        <a-switch v-model:checked="showFlightRange" size="small" />
      </div>
      <div class="legend-item" style="margin-top: 6px;">
        <span class="legend-color"
          style="background: linear-gradient(135deg, rgba(160,255,50,0.4), rgba(255,165,0,0.6));"></span>
        <span class="legend-label">相机可视范围</span>
        <a-switch v-model:checked="showFrustum" size="small" />
      </div>
      <div class="legend-item" style="margin-top: 6px;">
        <span class="legend-color"
          style="background: linear-gradient(135deg, rgba(79,195,247,0.5), rgba(79,195,247,0.2));"></span>
        <span class="legend-label">无人机鹰眼图</span>
        <a-switch v-model:checked="showEagleEye" size="small" />
      </div>
      <!-- 俯视角一键切换 -->
      <div style="margin-top: 10px; border-top: 1px solid rgba(255, 255, 255, 0.15); padding-top: 10px;">
        <a-button type="primary" size="small" class="w-full text-xs" style="background: #1890ff; border-color: #1890ff; display: flex; align-items: center; justify-content: center; gap: 4px;" @click="switchToTopDownView">
          🌍 切换为地图俯视角
        </a-button>
      </div>
    </div>

    <!-- FPV 控制面板 -->
    <div v-if="!isPreviewMode && fpvState.isActive" class="absolute top-4 right-[420px] bg-gray-900 border border-gray-700 text-white rounded-lg p-3 shadow-xl z-[1000] flex items-center gap-4">
      <div class="flex flex-col">
        <span class="text-sm font-bold text-blue-400">FPV云台微调模式 (航点{{ fpvState.waypointIndex + 1 }})</span>
        <span class="text-xs text-gray-400 mt-1">按住鼠标左键拖拽以调整相机朝向，黄框为视角范围</span>
      </div>
      <div class="flex gap-2 ml-4">
        <button class="px-3 py-1 bg-red-600 hover:bg-red-700 rounded text-sm transition-colors" @click="exitFpvMode(false)">撤销</button>
        <button class="px-3 py-1 bg-blue-600 hover:bg-blue-700 rounded text-sm transition-colors" @click="exitFpvMode(true)">保存当前姿态</button>
      </div>
    </div>

    <!-- 鹰眼图：无人机第一视角画中画 -->
    <div v-if="showEagleEye" class="eagle-eye-pip" :class="{ 'drawer-open': actionPopup.visible }">
      <div class="eagle-eye-header">
        <div class="flex items-center gap-2 flex-1 min-w-0">
          <span class="eagle-eye-title">
            <svg class="eagle-eye-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            {{ eagleEyeCamera ? `航点${(selectedWaypointIndex >= 0 ? selectedWaypointIndex : 0) + 1} · 第一视角` : '第一视角预览' }}
          </span>
          <!-- 航点切换导航 -->
          <div v-if="pointEntities.length > 0" class="eagle-eye-wp-nav">
            <button 
              class="nav-btn"
              :disabled="(selectedWaypointIndex <= 0 && selectedWaypointIndex !== -1) || pointEntities.length <= 1"
              @click.stop="switchWaypointPrev"
              title="上一航点"
            >
              ◀
            </button>
            <a-select
              :value="(selectedWaypointIndex >= 0 ? selectedWaypointIndex : 0)"
              size="small"
              class="wp-selector"
              :dropdownMatchSelectWidth="false"
              @change="onSwitchWaypoint"
            >
              <a-select-option v-for="(_, idx) in pointEntities" :key="idx" :value="idx">
                航点 {{ idx + 1 }}
              </a-select-option>
            </a-select>
            <button 
              class="nav-btn"
              :disabled="(selectedWaypointIndex >= pointEntities.length - 1) || pointEntities.length <= 1"
              @click.stop="switchWaypointNext"
              title="下一航点"
            >
              ▶
            </button>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <span v-if="eagleEyeCamera && !isPreviewMode" class="eagle-eye-hint">🖱️ 拖拽画面微调云台</span>
          <button class="eagle-eye-close" @click="showEagleEye = false" title="关闭鹰眼图">✕</button>
        </div>
      </div>
      <div class="eagle-eye-body">
        <template v-if="eagleEyeCamera">
          <div ref="eagleEyeContainerRef" class="h-full w-full"></div>
          <!-- 十字准星叠加层 -->
          <div class="eagle-eye-crosshair">
            <div class="crosshair-h"></div>
            <div class="crosshair-v"></div>
          </div>
          <!-- 姿态信息与指北针叠加 -->
          <div class="eagle-eye-overlay">
            <span class="flex items-center gap-1" title="指北针">
              <span class="inline-block transform transition-transform" :style="{ transform: `rotate(${-eagleEyeCamera.yaw}deg)` }">⬆</span>
              <span>北</span>
            </span>
            <span title="镜头绝对世界朝向 = 飞行偏航 + 云台相对偏航">朝向 {{ eagleEyeCamera.yaw.toFixed(1) }}°</span>
            <span title="云台俯仰角">俯仰 {{ eagleEyeCamera.pitch.toFixed(1) }}°</span>
            <span v-if="eagleEyeCamera.zoom > 1">ZOOM {{ eagleEyeCamera.zoom.toFixed(1) }}x</span>
          </div>
        </template>
        <template v-else>
          <div class="eagle-eye-placeholder">
            <div class="placeholder-text">暂无航点，请在地图上点击添加航点</div>
          </div>
        </template>
      </div>

      <!-- 第一视角姿态控制栏 (飞行器偏航、云台偏航、云台俯仰) -->
      <div v-if="eagleEyeCamera" class="eagle-eye-controls">
        <div class="ctrl-grid" v-if="!isPreviewMode">
          <!-- 1. 飞行器偏航角 (相对正北) -->
          <div class="ctrl-cell" title="飞行器偏航角 (相对正北，0°为北，90°为东，-90°为西)">
            <span class="ctrl-cell-label">🛫 飞行器偏航</span>
            <div class="ctrl-cell-input-wrap">
              <a-input-number
                :value="Number(eagleEyeCamera.aircraftYaw.toFixed(1))"
                size="small"
                :min="-180"
                :max="180"
                :step="1"
                class="ctrl-num-input"
                @change="onEagleEyeHeadingChange"
              />
              <span class="ctrl-cell-unit">°</span>
            </div>
          </div>

          <!-- 2. 云台偏航角 (0°正北，不受飞行器偏航角影响) -->
          <div class="ctrl-cell" title="云台偏航角 (0°为正北，不受飞行器偏航角影响)">
            <span class="ctrl-cell-label">📷 云台偏航</span>
            <div class="ctrl-cell-input-wrap">
              <a-input-number
                :value="Number(eagleEyeCamera.gimbalYaw.toFixed(1))"
                size="small"
                :min="-180"
                :max="180"
                :step="1"
                class="ctrl-num-input"
                @change="onEagleEyeGimbalYawChange"
              />
              <span class="ctrl-cell-unit">°</span>
            </div>
          </div>

          <!-- 3. 云台俯仰角 -->
          <div class="ctrl-cell" title="云台俯仰角 (-90°垂直朝下 ~ +35°抬头水平上仰)">
            <span class="ctrl-cell-label">📐 云台俯仰</span>
            <div class="ctrl-cell-input-wrap">
              <a-input-number
                :value="Number(eagleEyeCamera.pitch.toFixed(1))"
                size="small"
                :min="-90"
                :max="35"
                :step="1"
                class="ctrl-num-input"
                @change="onEagleEyeGimbalPitchChange"
              />
              <span class="ctrl-cell-unit">°</span>
            </div>
          </div>
        </div>

        <div class="ctrl-footer">
          <div class="ctrl-footer-info">
            <span class="calc-label">镜头拍摄朝向:</span>
            <span class="calc-val">{{ eagleEyeCamera.yaw.toFixed(1) }}°</span>
            <span class="calc-label" style="margin-left: 10px;">俯仰:</span>
            <span class="calc-val">{{ eagleEyeCamera.pitch.toFixed(1) }}°</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 地图图例组件 -->
    <MapLegend ref="mapLegendRef" @change="handleLegendChange" @change-tileset="handleTilesetChange" />

    <div v-if="!isPreviewMode" class="waypoint-panel">
      <div class="panel-header">
        <div class="flex items-center">
          <LeftOutlined class="mr-2 cursor-pointer hover:text-blue-500" @click="goBack" />
          <div class="title">{{ isEditMode ? '修改航线' : '航线规划' }}</div>
          <span v-if="isEditMode" class="ml-2 text-xs text-yellow-400 bg-yellow-900/30 px-2 py-0.5 rounded">编辑模式</span>
        </div>
        <div class="actions flex items-center gap-3">
          <a-tooltip :title="isEditMode ? '保存修改' : '保存航线'">
            <SaveOutlined class="cursor-pointer hover:text-blue-500 text-lg" @click="generateWaylineJson" />
          </a-tooltip>
        </div>
      </div>
      <div class="stats-grid">
        <div class="stat-item">
          <div class="value">{{ (totalDistance / 1000).toFixed(1) }} km</div>
          <div class="label">总距离</div>
        </div>
        <div class="stat-item">
          <div class="value">{{ Math.ceil(totalDuration / 60) }}分{{ Math.ceil(totalDuration % 60) }}秒</div>
          <div class="label">预计耗时</div>
        </div>
        <div class="stat-item">
          <div class="value">{{ pointEntities.length }}</div>
          <div class="label">航点数</div>
        </div>
      </div>

      <!-- drag mode toggle + hints -->
      <div class="shortcut-hints">
        <div class="drag-mode-toggle" @click="toggleDragMode">
          <div class="toggle-label">拖拽模式</div>
          <div class="toggle-btn" :class="waypointDragState.altKeyPressed ? 'active-vertical' : 'active-horizontal'">
            <span class="toggle-option" :class="{ active: !waypointDragState.altKeyPressed }">XY 水平</span>
            <span class="toggle-option" :class="{ active: waypointDragState.altKeyPressed }">↕ 高度</span>
          </div>
        </div>
        <div class="hint-item">
          <kbd>Alt</kbd> / <kbd>Shift</kbd> + 拖拽航点 = 调节高度
        </div>
        <div class="hint-item">
          <kbd>右键</kbd> 地图 = 添加航点
        </div>
        <!-- <div class="hint-item"
          style="display: flex; align-items: center; justify-content: space-between; margin-top: 8px;">
          <span>建目录名默认前缀</span>
          <a-input v-model:value="waylineGlobalParams.custom_dir_prefix" size="small"
            style="width: 90px; background: rgba(0,0,0,0.3); border: 1px solid #4B5563; color: white; text-align: center;"
            placeholder="前缀名" />
        </div> -->
      </div>


      <!-- 等间隔拍照管理 -->
      <div class="interval-photo-section" v-if="pointEntities.length >= 2">
        <div class="interval-header">
          <span class="interval-title">等间隔拍照</span>
          <PlusOutlined class="interval-add-btn" title="添加等间隔拍照" @click="openAddIntervalPhoto" />
        </div>
        <!-- 新增/编辑表单 -->
        <div v-if="intervalPhotoEditing" class="interval-form">
          <div class="interval-form-title">
            <span>{{ editingIntervalId ? '编辑等间隔拍照' : '添加等间隔拍照' }}</span>
          </div>
          <div class="interval-form-row">
            <span class="interval-label">航段</span>
            <a-input-number v-model:value="intervalPhotoForm.startIndex" size="small" :min="0"
              :max="pointEntities.length - 2" class="interval-input" />
            <span class="interval-sep">→</span>
            <a-input-number v-model:value="intervalPhotoForm.endIndex" size="small" :min="1"
              :max="pointEntities.length - 1" class="interval-input" />
          </div>
          <div class="interval-form-row">
            <span class="interval-label">类型</span>
            <a-select v-model:value="intervalPhotoForm.triggerType" size="small" class="interval-select">
              <a-select-option value="multipleTiming">等时间</a-select-option>
              <a-select-option value="multipleDistance">等距离</a-select-option>
            </a-select>
          </div>
          <div class="interval-form-row">
            <span class="interval-label">间隔</span>
            <a-input-number v-model:value="intervalPhotoForm.triggerParam" size="small" :min="0.1" :step="1"
              class="interval-input" />
            <span class="interval-unit">{{ intervalPhotoForm.triggerType === 'multipleTiming' ? '秒' : '米' }}</span>
          </div>
          <div class="interval-form-row">
            <span class="interval-label">全局格式</span>
            <a-switch v-model:checked="intervalPhotoForm.use_global_image_format" :checkedValue="1" :unCheckedValue="0" size="small" />
          </div>
          <div v-if="intervalPhotoForm.use_global_image_format === 0" class="interval-form-row">
            <span class="interval-label">相机镜头</span>
            <a-select v-model:value="intervalPhotoForm.image_format" size="small" class="interval-select">
              <a-select-option value="visable,ir">可见光+红外</a-select-option>
              <a-select-option value="visable">可见光</a-select-option>
              <a-select-option value="ir">红外</a-select-option>
            </a-select>
          </div>

          <!-- 文件命名后缀配置 -->
          <div class="flex flex-col gap-2 border-t border-gray-800/80 pt-2.5 mt-1">
            <div class="flex items-center justify-between">
              <span class="card-unit font-medium text-blue-400">文件命名后缀</span>
            </div>
            <div class="flex flex-col gap-1">
              <a-input v-model:value="intervalPhotoForm.file_suffix" size="small" placeholder="zxsl-YYYYMMDDhhmm-" class="interval-input-text" />
            </div>
          </div>

          <div class="interval-form-actions">
            <button class="interval-btn cancel" @click="cancelIntervalPhotoEdit">取消</button>
            <button class="interval-btn confirm" @click="saveIntervalPhoto">{{ editingIntervalId ? '保存' : '添加' }}</button>
          </div>
        </div>
        <!-- 已有列表 -->
        <div v-for="g in intervalPhotoGroups" :key="g.id" class="interval-item" :class="{ 'active': editingIntervalId === g.id }" @click="editIntervalPhoto(g)">
          <div class="interval-item-info">
            <span class="interval-badge">{{ g.triggerType === 'multipleTiming' ? '⏱' : '📏' }}</span>
            <div class="flex flex-col gap-0.5 min-w-0 flex-1">
              <div class="flex items-center gap-1.5">
                <span>航点{{ g.startIndex + 1 }} → {{ g.endIndex + 1 }}</span>
                <span class="interval-param">{{ g.triggerParam }}{{ g.triggerType === 'multipleTiming' ? '秒' : '米' }}</span>
              </div>
              <div v-if="g.file_suffix" class="text-[10px] text-blue-400/90 font-mono truncate max-w-[180px]" :title="g.file_suffix">
                {{ g.file_suffix }}
              </div>
            </div>
          </div>
          <div class="interval-item-ops" @click.stop>
            <EditOutlined class="interval-op-btn edit" title="编辑" @click="editIntervalPhoto(g)" />
            <CloseOutlined class="interval-op-btn remove" title="删除" @click="removeIntervalPhoto(g.id)" />
          </div>
        </div>
        <div v-if="intervalPhotoGroups.length === 0 && !intervalPhotoEditing" class="text-gray-500 text-xs"
          style="padding: 0 16px 8px;">
          点击 + 添加等间隔拍照航段
        </div>
      </div>

      <div class="waypoint-list">

        <div v-for="(point, index) in pointEntities" :key="index" class="waypoint-item"
          :class="{ 'active': waypointDragState.waypointIndex === index || selectedWaypointIndex === index }"
          @click="selectWaypoint(index)">
          <div class="wp-header">
            <div class="wp-index">
              <CaretDownOutlined v-if="selectedWaypointIndex === index" />
              <CaretRightOutlined v-else />
              <span class="ml-2 font-bold">航点{{ index + 1 }}</span>
              <span class="ml-2 text-xs text-blue-400 bg-blue-950/30 border border-blue-900/30 rounded px-1.5 py-0.5 whitespace-nowrap font-medium animate-pulse" v-if="takeoffPoint">
                相对高: {{ (point.position[2] - (takeoffPoint.height || 0)).toFixed(0) }}m
              </span>
            </div>
            <div class="wp-ops">
              <SearchOutlined class="icon-btn" title="定位" @click.stop="flyToWaypointIndex(index)" />
              <EyeOutlined class="icon-btn" title="FPV视角微调" @click.stop="enterFpvMode(index)" />
              <DeleteOutlined class="icon-btn delete" title="删除" @click.stop="deleteWaypoint(index)" />
            </div>
          </div>

          <!-- 动作摘要（始终可见，无需展开） -->
          <div class="action-bar" @click.stop="selectWaypoint(index)">
            <template
              v-if="point.actionGroupList && point.actionGroupList.length > 0 && point.actionGroupList.some(g => g.actions.length > 0)">
              <span v-for="(group, gIdx) in point.actionGroupList" :key="'g' + gIdx">
                <span v-for="(action, aIdx) in group.actions" :key="action.action_index" class="action-tag-mini" @click.stop="selectWaypointAction(index, action)">
                  {{ actionIconMap[action.action_func] || '⚡' }} {{ actionLabelMap[action.action_func] ||
                    action.action_func }}
                  <CloseOutlined class="tag-remove" @click.stop="deleteAction(group, aIdx)" />
                </span>
              </span>
            </template>
            <span class="action-add-chip" @click.stop="openActionPopup(index)" title="编辑动作">
              <PlusOutlined />
            </span>
          </div>

          <div v-if="selectedWaypointIndex === index" class="wp-details" @click.stop>
            <div class="input-group">
              <span class="label">经度</span>
              <a-input-number v-model:value="point.position[0]" :step="0.000001" size="small" class="dark-input"
                @change="() => updateWaypointPosition(index)" />
            </div>
            <div class="input-group">
              <span class="label">纬度</span>
              <a-input-number v-model:value="point.position[1]" :step="0.000001" size="small" class="dark-input"
                @change="() => updateWaypointPosition(index)" />
            </div>
            <div class="input-group">
              <span class="label">海拔高度</span>
              <a-input-number v-model:value="point.position[2]" :step="1" size="small" class="dark-input"
                addon-after="m" @change="() => updateWaypointPosition(index)" />
            </div>
            <div class="input-group" v-if="takeoffPoint">
              <span class="label">相对高度</span>
              <a-input-number :value="Number((point.position[2] - (takeoffPoint.height || 0)).toFixed(1))" :step="1" size="small" class="dark-input"
                addon-after="m" @change="(val) => updateWaypointRelativeHeight(index, val)" />
            </div>
            <div class="input-group">
              <span class="label">机身偏航</span>
              <a-input-number :value="getWaypointYaw(point)" :min="-180" :max="180" :step="1" size="small" class="dark-input"
                addon-after="°" @change="(val) => updateWaypointYaw(index, val)" />
            </div>
            <div class="input-group">
              <span class="label">云台偏航</span>
              <a-input-number :value="getWaypointGimbalYaw(point)" :min="-180" :max="180" :step="1" size="small" class="dark-input"
                addon-after="°" @change="(val) => updateWaypointGimbalYaw(index, val)" />
            </div>
            <div class="input-group">
              <span class="label">云台俯仰</span>
              <a-input-number :value="getWaypointPitch(point)" :min="-90" :max="35" :step="1" size="small" class="dark-input"
                addon-after="°" @change="(val) => updateWaypointPitch(index, val)" />
            </div>
            <div class="warning-text" v-if="point.agl !== undefined">
              距地高度(AGL): <span :class="point.agl < 30 ? 'text-red-500' : 'text-green-500'">{{ point.agl.toFixed(1)
                }}m</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { watch, ref, reactive, computed, inject, markRaw, shallowRef, onMounted as onMountedHook, onUnmounted as onUnmountedHook, nextTick, onBeforeUnmount } from 'vue'
import * as Cesium from 'cesium'
import { useRouter } from 'vue-router'
import { ELocalStorageKey } from '@/types'
import SvgIcon from "@/components/svgIcon.vue";
import MapLegend from "@/components/MapLegend.vue";
import { message } from 'ant-design-vue';
import { DeleteOutlined, SaveOutlined, InfoCircleOutlined, SettingOutlined, CaretDownOutlined, CaretRightOutlined, SearchOutlined, LeftOutlined, PlusOutlined, EditOutlined, CloseOutlined, EyeOutlined } from '@ant-design/icons-vue'
import { debounce } from "lodash-es";
import mapBjIcon from '../assets/airControl/map/map-bj-icon.png'
import { hexToRgba } from '@/utils/doubleLayerIcon'
import { postElementsGroupsReq, postElementsReq, getElementGroupsReq } from '@/api/layer'
import { useKeyPress, useLocalStorageState, useTimeout, useDrag, useDrop } from "vue-hooks-plus";
import TiandituGeocoderService from "@/services/TiandituGeocoderService";
import { CURRENT_CONFIG } from '@/api/http/config';
import { saveWaylineEdit, modifyWaylineEdit } from '@/api/wayline';
import { getFlightAreaList } from '@/api/flight-area';
import { EFlightAreaType, EGeometryType } from '@/types/flight-area';
import { PAYLOAD_TYPE } from '@/types/device';

const CAMERA_FOV_TABLE = {
  [PAYLOAD_TYPE.M30D]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M30T]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M3E]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M3T]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M3D]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M3TD]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M4D]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M4TD]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M4E]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.M4T]: { hFov: 84, vFov: 63 },
  [PAYLOAD_TYPE.H20]: { hFov: 82.9, vFov: 66.3 },
  [PAYLOAD_TYPE.H20T]: { hFov: 82.9, vFov: 66.3 },
  [PAYLOAD_TYPE.H20N]: { hFov: 45.4, vFov: 34.4 },
}
const DEFAULT_FOV = { hFov: 84, vFov: 63 }

const props = defineProps(['osdInfo', 'dockMapLocation', 'config', 'takeoffPoint', 'waylineName', 'isEditMode', 'editWaylineId', 'editWaylineData', 'dock_sn', 'previewMode', 'readOnly', 'isReadOnly'])
const emit = defineEmits(['viewer-ready', 'save-wayline', 'update:takeoffPoint'])

const isPreviewMode = computed(() => props.previewMode === true || props.readOnly === true || props.isReadOnly === true)

const cesiumContainerRef = ref(null)
    const eagleEyeContainerRef = ref(null)
    const viewerRef = shallowRef(null)
    const show = ref(false)
    const router = useRouter()
    const goBack = () => router.back()

    // 核心数据结构
    const map = reactive({ viewer: null, handler: null, Cesium: null })
    const [dockMap] = useLocalStorageState('dockMap')
    const pointEntities = ref([])     // 航点数据
    const dynamicRouteLine = ref(null) // 核心优化：动态航线实体
    const takeoffLine = ref(null) // 【新增】起飞折线实体配置
    const waypointDistanceLabels = ref([]) // 航点间距离标签
    const verticalLines = shallowRef([]) // 垂直投影线
    const showFlightRange = ref(false) // 机场预估飞行范围显示开关
    const mapLegendRef = ref(null) // 图例组件引用
    const legendShowData = ref([]) // 图例显示的实体列表
    const layerEntitiesCache = new Map() // 用来缓存每个图层的 Cesium Entity 列表 (Key: 图层名称)

    // 统计当前航线中已存在的数据绑定组号最大值/组数
    const getExistingRecordCount = () => {
      let maxNum = 0
      let recordCount = 0
      if (!pointEntities.value) return 0
      for (const pt of pointEntities.value) {
        if (!pt.actionGroupList) continue
        for (const g of pt.actionGroupList) {
          if (!g.actions) continue
          for (const act of g.actions) {
            if (act.action_func === 'startRecord') {
              recordCount++
              const match = (act.file_suffix || '').match(/#(\d+)/)
              if (match) {
                const n = parseInt(match[1], 10)
                if (n > maxNum) maxNum = n
              }
            }
            // 检查 takePhoto / orientedShoot 或任何动作中的 #N
            const suffixMatch = (act.file_suffix || '').match(/#(\d+)/)
            if (suffixMatch) {
              const n = parseInt(suffixMatch[1], 10)
              if (n > maxNum) maxNum = n
            }
            if (act._llm_bind_record && act._llm_record_num) {
              const n = Number(act._llm_record_num)
              if (!isNaN(n) && n > maxNum) maxNum = n
            }
          }
        }
      }
      return Math.max(maxNum, recordCount)
    }

    // 推断当前动作应关联的数据绑定组号 (优先同航点，其次前序航点)
    const inferRecordNum = (act) => {
      const wpIdx = actionPopup.pointIndex
      if (wpIdx >= 0 && pointEntities.value && pointEntities.value[wpIdx]) {
        const pt = pointEntities.value[wpIdx]
        if (pt.actionGroupList) {
          for (const g of pt.actionGroupList) {
            if (!g.actions) continue
            for (const a of g.actions) {
              if (a === act) continue
              if (a.action_func === 'startRecord') {
                const match = (a.file_suffix || '').match(/#(\d+)/)
                if (match) return parseInt(match[1], 10)
              }
              if (a._llm_bind_record && a._llm_record_num) {
                return Number(a._llm_record_num)
              }
              const match = (a.file_suffix || '').match(/#(\d+)/)
              if (match) return parseInt(match[1], 10)
            }
          }
        }
      }
      if (wpIdx > 0 && pointEntities.value) {
        for (let i = wpIdx - 1; i >= 0; i--) {
          const pt = pointEntities.value[i]
          if (pt && pt.actionGroupList) {
            for (const g of pt.actionGroupList) {
              if (!g.actions) continue
              for (const a of g.actions) {
                if (a.action_func === 'startRecord') {
                  const match = (a.file_suffix || '').match(/#(\d+)/)
                  if (match) return parseInt(match[1], 10)
                }
                if (a._llm_bind_record && a._llm_record_num) {
                  return Number(a._llm_record_num)
                }
                const match = (a.file_suffix || '').match(/#(\d+)/)
                if (match) return parseInt(match[1], 10)
              }
            }
          }
        }
      }
      const existing = getExistingRecordCount()
      return existing > 0 ? existing : 1
    }

    // 获取前序关联录像航点信息
    const getPrecedingStartRecordInfo = (wpIndex) => {
      if (wpIndex < 0 || !pointEntities.value) return { hasStart: false, desc: '未发现录像动作', startIndex: -1 }
      for (let i = wpIndex - 1; i >= 0; i--) {
        const pt = pointEntities.value[i]
        if (pt && pt.actionGroupList) {
          for (const g of pt.actionGroupList) {
            for (const act of g.actions) {
              if (act.action_func === 'startRecord') {
                const suffix = act.file_suffix || '#1'
                return {
                  hasStart: true,
                  startIndex: i,
                  suffix: suffix,
                  desc: `航点${i + 1} (${suffix})`
                }
              }
            }
          }
        }
      }
      return { hasStart: false, desc: '无前序录像', startIndex: -1 }
    }
    onBeforeUnmount(() => {
      // 1. 优先销毁鹰眼图小地图实例
      if (typeof destroyEagleEyeViewer === 'function') {
        destroyEagleEyeViewer()
      }
      // 2. 彻底销毁主 Cesium Viewer 实例，向 GPU 归还 WebGL 上下文
      if (map.viewer && !map.viewer.isDestroyed()) {
        map.viewer.destroy()
        map.viewer = null
      }
      map.Cesium = null
      map.handler = null
    })
    // 判断停录航点是否与录像航点经纬度一致
    const isCoordSameAsStartRecord = (wpIndex) => {
      const info = getPrecedingStartRecordInfo(wpIndex)
      if (!info.hasStart || info.startIndex < 0) return false
      const startPt = pointEntities.value[info.startIndex]
      const curPt = pointEntities.value[wpIndex]
      if (!startPt || !curPt) return false
      const dLng = Math.abs(curPt.position[0] - startPt.position[0])
      const dLat = Math.abs(curPt.position[1] - startPt.position[1])
      return dLng < 0.000001 && dLat < 0.000001
    }

    // 停录航点一键对齐录像航点经纬度
    const alignStopRecordWithStart = (stopWpIndex) => {
      if (stopWpIndex < 0 || !pointEntities.value || stopWpIndex >= pointEntities.value.length) return
      const info = getPrecedingStartRecordInfo(stopWpIndex)
      if (!info.hasStart || info.startIndex < 0) {
        message.warn('未找到关联的前序录像航点')
        return
      }
      const startPt = pointEntities.value[info.startIndex]
      const curPt = pointEntities.value[stopWpIndex]
      if (!startPt || !curPt) return

      curPt.position[0] = startPt.position[0]
      curPt.position[1] = startPt.position[1]

      updateAuxiliaryGraphics()
      syncFrustumEntities()
      updateActionPopupPosition()
      message.success(`已将航点${stopWpIndex + 1}经纬度对齐至录像航点${info.startIndex + 1}`)
    }

    // 快捷添加同经纬度停录航点 (跨航点停录专用快捷操作)
    const quickAddStopRecordWaypoint = (startWpIndex) => {
      if (startWpIndex < 0 || !pointEntities.value || startWpIndex >= pointEntities.value.length) return
      const startPt = pointEntities.value[startWpIndex]
      if (!startPt) return
      const [lng, lat, height] = startPt.position
      const insertIdx = startWpIndex + 1

      createWaypointData(lng, lat, height, insertIdx)

      const newPt = pointEntities.value[insertIdx]
      if (newPt) {
        newPt.actionGroupList = [
          {
            action_group_id: 0,
            action_group_start_index: insertIdx,
            action_group_end_index: insertIdx,
            action_trigger_type: 'reachPoint',
            action_trigger_param: 0,
            actions: [
              {
                action_index: 0,
                action_func: 'stopRecord'
              }
            ]
          }
        ]
        reindexAllActionGroups()
      }

      openActionPopup(insertIdx)
      message.success(`已在航点${startWpIndex + 1}后快捷创建同经纬度停录航点 (航点${insertIdx + 1})`)
    }

    // 一键快捷添加组合：录像 + 悬停5秒 + 拍照(LLM) + 自动创建同经纬度停录航点
    const oneClickAddRecordPhotoCombo = () => {
      const curWpIdx = actionPopup.pointIndex
      if (curWpIdx < 0 || !pointEntities.value || curWpIdx >= pointEntities.value.length) {
        message.warn('请先选择一个航点')
        return
      }

      let group = actionPopupGroup.value
      if (!group) {
        const pt = actionPopupPoint.value
        if (!pt) return
        if (!pt.actionGroupList || pt.actionGroupList.length === 0) {
          addActionGroup(pt, curWpIdx)
        }
        group = pt.actionGroupList[0]
        if (!group) return
      }

      // 1. 获取下一个录像组序号 (如 #1, #2...)
      const nextRecordNum = getExistingRecordCount() + 1

      // 2. 依次在当前航点添加动作
      // (a) 录像动作 (startRecord)
      const recordAction = {
        action_index: group.actions.length,
        action_func: 'startRecord',
        use_global_image_format: 1,
        image_format: 'visable,ir',
        file_suffix: `#${nextRecordNum}`
      }
      group.actions.push(recordAction)

      // (b) 悬停动作 (hover, 默认5秒)
      const hoverAction = {
        action_index: group.actions.length,
        action_func: 'hover',
        hover_time: 5
      }
      group.actions.push(hoverAction)

      // (c) 拍照动作 (takePhoto)
      const photoAction = {
        action_index: group.actions.length,
        action_func: 'takePhoto',
        use_global_image_format: 1,
        image_format: 'visable,ir',
        file_suffix: ''
      }
      group.actions.push(photoAction)

      // 重新索引当前动作组中的 action_index
      reindexActions(group)

      // 3. 自动在当前航点之后紧接着创建同经纬度新航点，并添加停录动作 (stopRecord)
      const curPt = pointEntities.value[curWpIdx]
      const [lng, lat, height] = curPt.position
      const insertIdx = curWpIdx + 1

      createWaypointData(lng, lat, height, insertIdx)

      const stopPt = pointEntities.value[insertIdx]
      if (stopPt) {
        stopPt.actionGroupList = [
          {
            action_group_id: 0,
            action_group_start_index: insertIdx,
            action_group_end_index: insertIdx,
            action_trigger_type: 'reachPoint',
            action_trigger_param: 0,
            actions: [
              {
                action_index: 0,
                action_func: 'stopRecord'
              }
            ]
          }
        ]
        reindexAllActionGroups()
      }

      // 4. 保持在当前航点弹窗，并高亮拍照动作
      openActionPopup(curWpIdx)
      selectedActionIndex.value = photoAction.action_index

      nextTick(() => {
        const el = document.getElementById(`action-card-${curWpIdx}-${photoAction.action_index}`)
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
        }
      })

      message.success(`已一键生成 #${nextRecordNum} 组「录像+悬停+拍照」，并在航点${insertIdx + 1}创建同经纬度停录航点！`)
    }

    // 动态生成 SVG 图标模板 (同步 DjiCesium)
    const svgTemplate = `
<svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <filter id="shadow" x="0" y="0" width="64" height="64" filterUnits="userSpaceOnUse">
      <feGaussianBlur in="SourceAlpha" stdDeviation="2"/>
      <feOffset dx="0" dy="2" result="offsetblur"/>
      <feComponentTransfer>
        <feFuncA type="linear" slope="0.5"/>
      </feComponentTransfer>
      <feMerge> 
        <feMergeNode/>
        <feMergeNode in="SourceGraphic"/> 
      </feMerge>
    </filter>
  </defs>
  <ellipse cx="32" cy="58" rx="10" ry="3" fill="#000000" fill-opacity="0.3" />
  <path d="M32 4 C18.745 4 8 14.745 8 28 C8 42 32 60 32 60 C32 60 56 42 56 28 C56 14.745 45.255 4 32 4Z" fill="{COLOR}" filter="url(#shadow)" stroke="#ffffff" stroke-width="1.5"/>
  <circle cx="32" cy="28" r="10" fill="white" fill-opacity="0.9"/>
</svg>`

    const iconCache = new Map()

    function getMarkerIcon(color) {
      if (!color) return mapBjIcon
      if (iconCache.has(color)) {
        return iconCache.get(color)
      }

      // 将 SVG 转换为 Data URI (base64)
      const svgContent = svgTemplate.replace('{COLOR}', color)
      const base64 = btoa(unescape(encodeURIComponent(svgContent)))
      const dataUri = `data:image/svg+xml;base64,${base64}`

      iconCache.set(color, dataUri)
      return dataUri
    }

    // 图例开关变化处理
    const handleLegendChange = async (data) => {
      if (!map.viewer || !map.Cesium) return

      const allLayers = [...data.legendList, ...data.legendList1]

      console.log('图例变化:', allLayers)
      // 处理每个图层的显示/隐藏
      for (const layer of allLayers) {
        const cacheKey = layer.name
        if (!layerEntitiesCache.has(cacheKey)) {
          layerEntitiesCache.set(cacheKey, [])
        }
        const cachedEntities = layerEntitiesCache.get(cacheKey)

        if (layer.checked) {
          // 检查是否已完全创建。如果未完全创建，增量分批创建
          if (layer.isFlightArea) {
            if (cachedEntities.length === 0) {
              await loadFlightAreas(layer.flightAreaType, layer)
            }
          } else {
            if (cachedEntities.length < layer.elements.length) {
              const createdIds = new Set(cachedEntities.map(e => e.id))
              const elementsToCreate = layer.elements.filter(ite => !createdIds.has(ite.id))

              const chunkSize = 30
              const addChunk = () => {
                if (elementsToCreate.length === 0 || !layer.checked) return
                const chunk = elementsToCreate.splice(0, chunkSize)
                chunk.forEach(ite => {
                  const entity = addLayerElement(ite, layer)
                  if (entity) {
                    cachedEntities.push(entity)
                  }
                })
                if (elementsToCreate.length > 0) {
                  requestAnimationFrame(addChunk)
                }
              }
              addChunk()
            }
          }

          // 将已存在的实体显式设为可见
          cachedEntities.forEach(entity => {
            entity.show = true
          })
        } else {
          // 如果是未选中状态，直接设置 show = false
          if (cachedEntities.length > 0) {
            cachedEntities.forEach(entity => {
              entity.show = false
            })
          }
        }
      }
    }

    // 加载并绘制官方飞行区域 (分批 chunk 加载)
    const loadFlightAreas = async (flightAreaType, layer) => {
      if (!map.viewer || !map.Cesium) return
      const cacheKey = layer.name
      if (!layerEntitiesCache.has(cacheKey)) {
        layerEntitiesCache.set(cacheKey, [])
      }
      const cachedEntities = layerEntitiesCache.get(cacheKey)

      try {
        const res = await getFlightAreaList()
        if (res.code !== 0 || !res.data) {
          console.warn('获取飞行区域列表失败:', res.message)
          return
        }

        const filteredAreas = res.data.filter(area => area.type === flightAreaType)
        const chunkSize = 30

        const addChunk = () => {
          if (filteredAreas.length === 0 || !layer.checked) return
          const chunk = filteredAreas.splice(0, chunkSize)
          chunk.forEach((area) => {
            const content = area.content
            if (!content || !content.geometry) return

            const geometryType = content.geometry.type
            const coordinates = content.geometry.coordinates

            const areaColor = flightAreaType === EFlightAreaType.NFZ
              ? map.Cesium.Color.RED.withAlpha(0.3)
              : map.Cesium.Color.GREEN.withAlpha(0.3)
            const outlineColor = flightAreaType === EFlightAreaType.NFZ
              ? map.Cesium.Color.RED
              : map.Cesium.Color.GREEN

            let entity = null
            if (geometryType === EGeometryType.CIRCLE) {
              const radius = content.geometry.radius
              const center = coordinates

              entity = map.viewer.entities.add({
                id: `flight-area-${area.area_id}`,
                name: `[${flightAreaType === EFlightAreaType.NFZ ? '禁飞区' : '可飞区'}] ${area.name}`,
                position: map.Cesium.Cartesian3.fromDegrees(center[0], center[1]),
                ellipse: {
                  semiMajorAxis: radius,
                  semiMinorAxis: radius,
                  material: areaColor,
                  outline: true,
                  outlineColor: outlineColor,
                  outlineWidth: 2,
                  heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND
                }
              })
            } else if (geometryType === EGeometryType.POLYGON) {
              const polygonCoords = coordinates[0]
              const positions = polygonCoords.map(coord =>
                map.Cesium.Cartesian3.fromDegrees(coord[0], coord[1])
              )

              entity = map.viewer.entities.add({
                id: `flight-area-${area.area_id}`,
                name: `[${flightAreaType === EFlightAreaType.NFZ ? '禁飞区' : '可飞区'}] ${area.name}`,
                polygon: {
                  hierarchy: new map.Cesium.PolygonHierarchy(positions),
                  material: areaColor,
                  outline: true,
                  outlineColor: outlineColor,
                  outlineWidth: 2,
                  heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND
                }
              })
            }
            if (entity) {
              cachedEntities.push(entity)
              legendShowData.value.push(entity)
            }
          })
          if (filteredAreas.length > 0) {
            requestAnimationFrame(addChunk)
          }
        }
        addChunk()

        console.log(`已启动分批加载 ${filteredAreas.length} 个${flightAreaType === EFlightAreaType.NFZ ? '禁飞' : '可飞'}区域`)
      } catch (error) {
        console.error('加载飞行区域失败:', error)
        message.error('加载飞行区域失败')
      }
    }

    // 添加单个图层元素 (修正 LineString 与 Polygon 的坐标解析 bug 并同步样式表现)
    const addLayerElement = (element, layer) => {
      if (!map.viewer || !map.Cesium) return null

      // 优先取图元自身的颜色配置，不存在则取图层默认颜色
      const elementColor = element.resource?.content?.properties?.color || layer.color || '#2D8CF0'

      if (element.type === 'Point') {
        // 使用动态生成的彩色图标，保持图层图标跟首页 DjiCesium 完全一致
        let finalIcon = layer.url
        if (elementColor && (layer.url.includes('map-icon-marker') || layer.url.includes('svg'))) {
          finalIcon = getMarkerIcon(elementColor)
        }

        const entity = map.viewer.entities.add({
          id: element.id,
          position: map.Cesium.Cartesian3.fromDegrees(element.lonLat[0], element.lonLat[1]),
          billboard: {
            image: finalIcon,
            width: 28,
            height: 34,
            verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          },
          label: {
            text: element.name,
            font: '14px sans-serif',
            fillColor: map.Cesium.Color.WHITE,
            showBackground: true,
            backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
            pixelOffset: new map.Cesium.Cartesian2(0, -40),
            verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          }
        })
        legendShowData.value.push(entity)
        return entity
      } else if (element.type === 'LineString') {
        if (!element.lonLat || element.lonLat.length === 0) return null
        const positions = map.Cesium.Cartesian3.fromDegreesArray(element.lonLat.flat())
        const entity = map.viewer.entities.add({
          id: element.id,
          polyline: {
            positions: positions,
            width: 3, // 同步 DjiCesium 的 setLine 宽度
            material: map.Cesium.Color.fromCssColorString(elementColor),
            depthFailMaterial: map.Cesium.Color.fromCssColorString(elementColor),
            clampToGround: true
          }
        })
        legendShowData.value.push(entity)
        return entity
      } else if (element.type === 'Polygon') {
        if (!element.lonLat || element.lonLat.length === 0 || element.lonLat[0].length === 0) return null
        const pts = map.Cesium.Cartesian3.fromDegreesArray(element.lonLat[0].flat())
        
        // 核心同步：计算多边形几何中心点（用于悬挂 Label）
        const sphere = map.Cesium.BoundingSphere.fromPoints(pts)
        const carto = map.Cesium.Cartographic.fromCartesian(sphere.center)
        const centerLon = map.Cesium.Math.toDegrees(carto.longitude)
        const centerLat = map.Cesium.Math.toDegrees(carto.latitude)

        const entity = map.viewer.entities.add({
          id: element.id,
          polygon: {
            hierarchy: new map.Cesium.PolygonHierarchy(pts),
            material: new map.Cesium.ColorMaterialProperty(map.Cesium.Color.fromCssColorString(elementColor).withAlpha(0.4)), // 同步透明度 0.4
            outline: true,
            outlineColor: map.Cesium.Color.BLACK, // 同步黑色描边
            outlineWidth: 10, // 同步描边粗细 10
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND,
            perPositionHeight: false
          },
          label: {
            text: element.name,
            font: '14px sans-serif',
            fillColor: map.Cesium.Color.WHITE,
            showBackground: true,
            backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
            verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
            pixelOffset: new map.Cesium.Cartesian2(0, -20),
            heightReference: map.Cesium.HeightReference.CLAMP_TO_GROUND
          },
          position: map.Cesium.Cartesian3.fromDegrees(centerLon, centerLat)
        })
        legendShowData.value.push(entity)
        return entity
      }
      return null
    }

    // 状态管理
    const selectedWaypointIndex = ref(-1)
    const selectedActionIndex = ref(-1)
    const cardRefs = Array.from({ length: 50 }, () => ref(null))
    const takeoffPoint = ref(null)
    const contextMenu = reactive({ visible: false, x: 0, y: 0, position: { longitude: 0, latitude: 0, height: 0 } })
    const waypointMenu = reactive({ visible: false, x: 0, y: 0, waypointIndex: -1, height: 0 })

    // 拖拽状态
    const waypointDragState = reactive({
      isDragging: false,
      waypointIndex: -1,
      mode: 'horizontal',
      startMouseY: 0,
      startHeight: 0,
      altKeyPressed: false
    })

    // Drag height tooltip data
    const dragTooltipPos = reactive({ x: 0, y: 0 })
    const dragCurrentHeight = ref(0)
    const dragHeightDelta = ref(0)


    // 全局航线参数
    const waylineGlobalParams = reactive({
      global_height: 100,
      auto_flight_speed: 10,
      take_off_ref_point: '',
      custom_dir_prefix: props.waylineName || ''
    })

    watch(() => props.waylineName, (val) => {
      if (val && !waylineGlobalParams.custom_dir_prefix) {
        waylineGlobalParams.custom_dir_prefix = val;
      }
    }, { immediate: true })

    watch(() => props.config, (val) => {
      if (val) {
        if (val.global_height !== undefined) waylineGlobalParams.global_height = val.global_height;
        if (val.auto_flight_speed !== undefined) waylineGlobalParams.auto_flight_speed = val.auto_flight_speed;
        if (val.take_off_ref_point) waylineGlobalParams.take_off_ref_point = val.take_off_ref_point;
      }
    }, { deep: true, immediate: true })

    // 监听传入的起飞点并自动设置
    watch(() => props.takeoffPoint, (val) => {
      if (val) {
        takeoffPoint.value = {
          longitude: val.longitude,
          latitude: val.latitude,
          height: val.height
        };
        waylineGlobalParams.take_off_ref_point = `${val.latitude},${val.longitude},${val.height}`;
        message.success('起飞点已自动设置');
      }
    }, { deep: true, immediate: true })

    // 监听编辑模式航线数据，加载航点到地图
    // 添加地图初始化标志
    const isMapReady = ref(false);

    // 独立的航点加载函数
    const loadEditWaylineData = () => {
      const val = props.editWaylineData;
      if (!val || !val.route_point_list || val.route_point_list.length === 0) {
        pointEntities.value = [];
        intervalPhotoGroups.value = [];
        selectedWaypointIndex.value = -1;
        updateAuxiliaryGraphics();
        return;
      }

      // 检查地图是否已初始化
      if (!map.viewer || !map.Cesium) {
        console.log('地图未初始化，等待初始化后再加载航点');
        return;
      }

      // 清空现有航点
      pointEntities.value = [];
      intervalPhotoGroups.value = [];
      selectedWaypointIndex.value = 0;

      // 更新航线起飞参考点
      if (val.take_off_ref_point) {
        let lat = NaN, lon = NaN, h = 0;
        if (typeof val.take_off_ref_point === 'string') {
          const parts = val.take_off_ref_point.split(',');
          if (parts.length >= 2) {
            lat = parseFloat(parts[0]);
            lon = parseFloat(parts[1]);
            h = parts.length >= 3 ? parseFloat(parts[2]) : 0;
          }
        } else if (typeof val.take_off_ref_point === 'object') {
          lat = parseFloat(val.take_off_ref_point.latitude ?? val.take_off_ref_point.lat);
          lon = parseFloat(val.take_off_ref_point.longitude ?? val.take_off_ref_point.lon ?? val.take_off_ref_point.lng);
          h = parseFloat(val.take_off_ref_point.height ?? val.take_off_ref_point.alt ?? 0);
        }
        if (!isNaN(lat) && !isNaN(lon)) {
          takeoffPoint.value = { latitude: lat, longitude: lon, height: isNaN(h) ? 0 : h };
          waylineGlobalParams.take_off_ref_point = `${lat},${lon},${isNaN(h) ? 0 : h}`;
        }
      } else if (!props.takeoffPoint) {
        // 如果当前航线没有起飞点，且外部未传入默认起飞点，则清空起飞点，避免残留上一条航线数据
        takeoffPoint.value = null;
        waylineGlobalParams.take_off_ref_point = '';
      }

      const takeoffHeight = takeoffPoint.value?.height || 0;

      val.route_point_list.forEach((point, index) => {
        // 计算绝对高度（航线数据中的高度是相对起飞点的高度）
        const absoluteHeight = Number(point.height) + Number(takeoffHeight);

        let normalActionGroups = [];
        if (point.action_group_list) {
          point.action_group_list.forEach(group => {
            if (group.action_trigger_type === 'multipleTiming' || group.action_trigger_type === 'multipleDistance') {
              const photoAct = (group.actions && group.actions[0]) || {}
              const groupObj = {
                id: Date.now() + '_' + Math.random().toString(36).substr(2, 5),
                startIndex: group.action_group_start_index,
                endIndex: group.action_group_end_index,
                triggerType: group.action_trigger_type,
                triggerParam: group.action_trigger_param,
                use_global_image_format: photoAct.use_global_image_format !== undefined ? photoAct.use_global_image_format : (photoAct.useGlobalImageFormat !== undefined ? photoAct.useGlobalImageFormat : 1),
                image_format: photoAct.image_format || photoAct.imageFormat || photoAct.payload_lens_index || 'visable,ir',
                file_suffix: photoAct.file_suffix || '',
                payload_position_index: photoAct.payload_position_index !== undefined ? photoAct.payload_position_index : 0
              }
              intervalPhotoGroups.value.push(groupObj);
            } else {
              const gCopy = JSON.parse(JSON.stringify(group));
              if (gCopy.actions) {
                gCopy.actions.forEach(act => {
                  if (act.imageFormat && !act.image_format) act.image_format = act.imageFormat;
                  if (act.payload_lens_index && !act.image_format) act.image_format = act.payload_lens_index;
                  if (['takePhoto', 'orientedShoot'].includes(act.action_func)) {
                    if (!act.image_format) act.image_format = 'visable,ir';
                  }
                });
              }
              normalActionGroups.push(gCopy);
            }
          });
        }

        // 检查动作列表中是否包含 gimbalRotate、rotateYaw、zoom，若缺失则自动补充
        const hasGimbalRotate = normalActionGroups.some(g => g.actions && g.actions.some(a => a.action_func === 'gimbalRotate'));
        const hasRotateYaw = normalActionGroups.some(g => g.actions && g.actions.some(a => a.action_func === 'rotateYaw'));
        const hasZoom = normalActionGroups.some(g => g.actions && g.actions.some(a => a.action_func === 'zoom'));

        const actionsToPrepend = [];
        const pitchVal = point.gimbal_pitch !== undefined ? point.gimbal_pitch : (point.custom_gimbal_pitch !== undefined ? point.custom_gimbal_pitch : (point.gimbalPitch !== undefined ? point.gimbalPitch : undefined));
        const gimbalYawVal = point.gimbal_yaw_rotate_angle !== undefined ? point.gimbal_yaw_rotate_angle : (point.gimbal_yaw !== undefined ? point.gimbal_yaw : (point.gimbalYaw !== undefined ? point.gimbalYaw : undefined));
        if (!hasGimbalRotate && (pitchVal !== undefined || gimbalYawVal !== undefined)) {
          actionsToPrepend.push({
            action_func: 'gimbalRotate',
            gimbal_pitch_rotate_angle: pitchVal !== undefined ? Number(pitchVal) : -90,
            gimbal_yaw_rotate_angle: gimbalYawVal !== undefined ? Number(gimbalYawVal) : 0,
            payload_position_index: 0,
            use_global_payload_lens_index: 1
          });
        }

        const yawVal = point.aircraft_heading !== undefined ? point.aircraft_heading : (point.yaw !== undefined ? point.yaw : (point.heading !== undefined ? point.heading : (point.waypoint_heading_param?.waypoint_heading_angle !== undefined ? point.waypoint_heading_param.waypoint_heading_angle : undefined)));
        if (!hasRotateYaw && yawVal !== undefined) {
          actionsToPrepend.push({
            action_func: 'rotateYaw',
            aircraft_heading: Number(yawVal),
            aircraft_path_mode: 'clockwise'
          });
        }

        const zoomVal = point.zoom !== undefined ? point.zoom : (point.zoom_factor !== undefined ? point.zoom_factor : (point.zoomFactor !== undefined ? point.zoomFactor : undefined));
        if (!hasZoom && zoomVal !== undefined && Number(zoomVal) > 1) {
          actionsToPrepend.push({
            action_func: 'zoom',
            zoom: Number(zoomVal)
          });
        }

        if (actionsToPrepend.length > 0) {
          if (normalActionGroups.length === 0) {
            normalActionGroups.push({
              action_group_id: index,
              action_group_start_index: index,
              action_group_end_index: index,
              action_trigger_type: 'reachPoint',
              actions: actionsToPrepend.map((a, i) => ({ ...a, action_index: i }))
            });
          } else {
            actionsToPrepend.reverse().forEach(act => {
              normalActionGroups[0].actions.unshift(act);
            });
            normalActionGroups[0].actions.forEach((a, i) => { a.action_index = i; });
          }
        }

        const wp = {
          updateKey: Date.now() + index,
          position: [point.longitude, point.latitude, absoluteHeight],
          agl: undefined,
          terrainHeight: undefined,
          yaw: yawVal !== undefined ? Number(yawVal) : 0,
          aircraft_heading: yawVal !== undefined ? Number(yawVal) : 0,
          gimbalPitch: pitchVal !== undefined ? Number(pitchVal) : -90,
          gimbal_pitch: pitchVal !== undefined ? Number(pitchVal) : -90,
          gimbalYaw: gimbalYawVal !== undefined ? Number(gimbalYawVal) : 0,
          gimbal_yaw: gimbalYawVal !== undefined ? Number(gimbalYawVal) : 0,
          billboard: {
            image: mapBjIcon,
            width: 28, height: 34,
            verticalOrigin: 1,
            heightReference: 0
          },
          label: {
            text: `航点${index + 1} H:${Number(point.height).toFixed(0)}m(相对)`,
            font: '14px sans-serif',
            fillColor: [255, 255, 255, 255],
            showBackground: true,
            backgroundColor: [0, 0, 0, 153],
            pixelOffset: [0, -40],
            verticalOrigin: 1,
            heightReference: 0
          },
          actionGroupList: normalActionGroups
        };

        pointEntities.value.push(wp);
      });

      // 增加航线ID，用于防止快速切换航线时的数据污染
      currentWaylineId.value++;

      // 等待地形提供者就绪后再采样，避免返回 undefined
      const waitForTerrainReady = async () => {
        // 如果地形提供者还在加载中，等待其完成
        if (map.viewer.terrainProvider && map.viewer.terrainProvider.readyPromise) {
          try {
            await map.viewer.terrainProvider.readyPromise;
          } catch (e) {
            console.warn('等待地形提供者就绪失败:', e);
          }
        }
        
        // 额外延迟确保地形瓦片已开始加载（对低配置设备很重要）
        return new Promise(resolve => setTimeout(resolve, 800));
      };

      // 优化：使用 Cesium.sampleTerrainMostDetailed 批量采样所有航点
      const batchUpdateTerrainHeights = async () => {
        if (!map.viewer.terrainProvider || pointEntities.value.length === 0) return;

        try {
          // 准备所有航点的地理坐标
          const positions = pointEntities.value.map(point => {
            const [lng, lat] = point.position;
            return map.Cesium.Cartographic.fromDegrees(lng, lat);
          });

          // 批量采样地形高度（使用最高精度）
          const sampledPositions = await map.Cesium.sampleTerrainMostDetailed(
            map.viewer.terrainProvider,
            positions
          );

          // 更新每个航点的 AGL 和标签
          sampledPositions.forEach((sampledPos, idx) => {
            if (idx >= pointEntities.value.length) return;
            
            const point = pointEntities.value[idx];
            const [lng, lat, height] = point.position;
            const terrainHeight = sampledPos.height;

            // 验证地形高度有效性
            if (terrainHeight === undefined || Number.isNaN(terrainHeight) || terrainHeight === null) {
              console.warn(`航点${idx + 1} 批量采样失败，使用单点重试`);
              // 回退到单点采样
              setTimeout(() => updateTerrainHeight(idx), 1000);
              return;
            }

            const agl = height - terrainHeight;
            point.agl = agl;
            point.terrainHeight = terrainHeight;
            point._retryCount = 0; // 标记为已成功

            // 更新标签
            const relH = takeoffPoint.value ? (height - takeoffPoint.value.height) : height;
            let text = `航点${idx + 1} H:${relH.toFixed(0)}m(相对)`;
            const displayAgl = Math.abs(agl) < 0.05 ? 0 : Math.round(agl);
            if (agl < 30) {
              text += `\n⚠️距地:${displayAgl}m`;
              if (agl < -0.5) {
                text += ` (撞地!)`;
              }
              point.label.fillColor = [255, 0, 0, 255]; // 红色
            } else {
              point.label.fillColor = [255, 255, 255, 255]; // 白色
            }
            point.label.text = text;
            point.updateKey = Date.now();
          });

          console.log(`✅ 批量采样完成：${sampledPositions.length} 个航点`);

        } catch (error) {
          console.warn('批量地形采样失败，降级为逐个采样:', error);
          // 批量采样失败时，降级为逐个采样
          pointEntities.value.forEach((_, idx) => {
            setTimeout(() => updateTerrainHeight(idx), idx * 100);
          });
        }
      };

      // 延迟执行辅助图形和地形高度更新，确保航点已渲染
      setTimeout(async () => {
        // 更新辅助图形
        updateAuxiliaryGraphics();

        // 等待地形数据准备就绪
        await waitForTerrainReady();

        // 🔥 关键优化：预加载整条航线的地形数据
        await preloadTerrainForWaypoints();

        // 优先使用批量采样（更快、更准确）
        await batchUpdateTerrainHeights();

        // 飞到第一个航点位置
        if (map.viewer && map.Cesium && pointEntities.value.length > 0) {
          const firstPoint = pointEntities.value[0];
          map.viewer.camera.flyTo({
            destination: map.Cesium.Cartesian3.fromDegrees(
              firstPoint.position[0],
              firstPoint.position[1],
              firstPoint.position[2] + 3500
            ),
            duration: 2
          });
        }
      }, 500);

      // message.success(`已加载 ${val.route_point_list.length} 个航点`);
    };

    // 监听编辑数据变化（当地图已初始化时）
    watch(() => props.editWaylineData, (val) => {
      if (val && isMapReady.value) {
        loadEditWaylineData();
      } else if (!val && isMapReady.value) {
        // 当数据清空时（例如关闭当前航线），清空地图上的航点和起飞点
        // 增加航线ID，确保旧航线的地形重试任务不会影响新航线
        currentWaylineId.value++;
        pointEntities.value = [];
        intervalPhotoGroups.value = [];
        selectedWaypointIndex.value = -1;
        if (!props.takeoffPoint) {
          takeoffPoint.value = null;
          waylineGlobalParams.take_off_ref_point = '';
        }
        updateAuxiliaryGraphics();
      }
    }, { deep: true })

    // 监听地图初始化状态（当有待加载的编辑数据时）
    watch(isMapReady, (ready) => {
      if (ready && props.editWaylineData) {
        // 地图刚初始化完成，加载编辑数据
        setTimeout(() => {
          loadEditWaylineData();
        }, 500);
      }
    })


    // ---- Reliable keyboard listener for Alt/Shift (raw DOM) ----
    // useKeyPress from vue-hooks-plus may NOT work when Cesium canvas has focus.
    // Use raw document-level listeners instead.
    const onKeyDown = (event) => {
      if (event.key === 'Alt' || event.key === 'Shift') {
        event.preventDefault() // prevent browser menu activation on Alt
        waypointDragState.altKeyPressed = true
        if (waypointDragState.isDragging) {
          waypointDragState.mode = 'vertical'
        }
        console.log('[WaypointDrag] key down:', event.key, '→ altKeyPressed = true')
      }
    }
    const onKeyUp = (event) => {
      if (event.key === 'Alt' || event.key === 'Shift') {
        waypointDragState.altKeyPressed = false
        if (waypointDragState.isDragging) {
          waypointDragState.mode = 'horizontal'
        }
        console.log('[WaypointDrag] key up:', event.key, '→ altKeyPressed = false')
      }
    }

    // Manual toggle for UI button fallback (no keyboard needed)
    const toggleDragMode = () => {
      waypointDragState.altKeyPressed = !waypointDragState.altKeyPressed
      if (waypointDragState.isDragging) {
        waypointDragState.mode = waypointDragState.altKeyPressed ? 'vertical' : 'horizontal'
      }
      console.log('[WaypointDrag] manual toggle → altKeyPressed =', waypointDragState.altKeyPressed)
    }

    const globalTerrainPromise = shallowRef(null)

    const getGlobalTerrainProvider = (Cesium) => {
      if (!Cesium) return Promise.resolve(null)
      if (Cesium.Ion) {
        Cesium.Ion.defaultAccessToken = CURRENT_CONFIG.cesiumIonToken || CURRENT_CONFIG.VITE_CESIUM_ION_TOKEN
      }
      if (globalTerrainPromise.value) {
        return globalTerrainPromise.value
      }

      globalTerrainPromise.value = (async () => {
        try {
          if (typeof Cesium.createWorldTerrainAsync === 'function') {
            const terrain = await Cesium.createWorldTerrainAsync({
              requestWaterMask: true,
              requestVertexNormals: true
            })
            return terrain
          } else if (typeof Cesium.createWorldTerrain === 'function') {
            return Cesium.createWorldTerrain({
              requestWaterMask: true,
              requestVertexNormals: true
            })
          } else if (typeof Cesium.CesiumTerrainProvider?.fromUrl === 'function') {
            const terrain = await Cesium.CesiumTerrainProvider.fromUrl(
              'https://assets.ion.cesium.com/1',
              { requestWaterMask: true, requestVertexNormals: true }
            )
            return terrain
          }
        } catch (e) {
          console.warn('3D 地形加载异常，降级为椭球高程:', e)
        }
        return null
      })()

      return globalTerrainPromise.value
    }

    const initNativeCesium = () => {
      if (!cesiumContainerRef.value || map.isDestroyed) return

      // 配置 Cesium Ion Token (激活真实 3D DEM 起伏地形)
      if (Cesium.Ion) {
        Cesium.Ion.defaultAccessToken = CURRENT_CONFIG.cesiumIonToken || CURRENT_CONFIG.VITE_CESIUM_ION_TOKEN
      }

      const tdtToken = CURRENT_CONFIG.tiandituToken || '4db514621ce57a692181a8bc4ddb9d72'
      const imgProvider = new Cesium.UrlTemplateImageryProvider({
        url: `https://t{s}.tianditu.gov.cn/DataServer?T=img_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
        subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
        maximumLevel: 18
      })
      const ciaProvider = new Cesium.UrlTemplateImageryProvider({
        url: `https://t{s}.tianditu.gov.cn/DataServer?T=cia_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
        subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
        maximumLevel: 18
      })

      const _viewer = new Cesium.Viewer(cesiumContainerRef.value, {
        animation: false,
        timeline: false,
        fullscreenButton: false,
        infoBox: false,
        selectionIndicator: false,
        skyAtmosphere: false,
        skyBox: false,
        sceneModePicker: false,
        homeButton: false,
        navigationHelpButton: false,
        showCredit: false,
        targetFrameRate: 60,
        geocoder: true,
        baseLayerPicker: false,
        baseLayer: new Cesium.ImageryLayer(imgProvider)
      })

      _viewer.imageryLayers.addImageryProvider(ciaProvider)

      // 开启 3D 真实地形 (参考 DjiCesium 方案)
      _viewer.scene.globe.depthTestAgainstTerrain = true
      getGlobalTerrainProvider(Cesium).then(terrain => {
        if (terrain && _viewer && !_viewer.isDestroyed()) {
          _viewer.terrainProvider = terrain
        }
      })

      _viewer.camera.setView({
        destination: Cesium.Cartesian3.fromDegrees(106.11, 29.8000, 100000)
      })

      const rawViewer = markRaw(_viewer)
      viewerRef.value = rawViewer
      map.viewer = rawViewer
      map.Cesium = Cesium

      onViewerReady({ Cesium, viewer: rawViewer })
    }

    // Register/unregister raw DOM listeners
    onMountedHook(() => {
      document.addEventListener('keydown', onKeyDown, true) // use capture phase
      document.addEventListener('keyup', onKeyUp, true)
      console.log('[WaypointDrag] keyboard listeners registered')

      nextTick(() => {
        setTimeout(() => {
          if (!map.isDestroyed) {
            show.value = true
            initNativeCesium()
          }
        }, 100)
      })
    })
    onUnmountedHook(() => {
      destroyEagleEyeViewer()
      removeFrustumEntities()
      document.removeEventListener('keydown', onKeyDown, true)
      document.removeEventListener('keyup', onKeyUp, true)

      // 清除图层缓存实体，避免组件卸载后内存泄漏
      layerEntitiesCache.forEach((entities) => {
        entities.forEach(entity => {
          if (map.viewer) {
            map.viewer.entities.remove(entity)
          }
        })
      })
      layerEntitiesCache.clear()

      // 清理 3D 建模 primitives，避免内存泄漏
      viewerTilesetPrimitives.forEach((primitivesMap, viewer) => {
        try {
          if (!viewer.isDestroyed()) {
            primitivesMap.forEach((tileset) => {
              try {
                viewer.scene.primitives.remove(tileset)
              } catch (e) {}
            })
          }
        } catch (e) {}
      })
      viewerTilesetPrimitives.clear()
    })

    // ----------------------------------------------------------------
    // 核心优化 1 & 2: 动态回调线 + 样条插值 (解决拖拽卡顿 + 实现平滑曲线)
    // ----------------------------------------------------------------

    /**
     * 计算航线的所有点（包含插值）
     * 这是一个高性能计算函数，会被 Cesium 每帧调用，因此不能有重操作
     */
    const getSmoothedPositions = () => {
      if (pointEntities.value.length < 2 || !map.Cesium) return [];

      const positions = [];
      for (const p of pointEntities.value) {
        if (!p || !p.position) continue;
        const cartesian = map.Cesium.Cartesian3.fromDegrees(p.position[0], p.position[1], p.position[2]);
        
        // 过滤与上一个点完全相同的坐标，避免 Cesium 渲染 Polyline 时因距离为 0 导致法线计算 NaN 从而丢失线段
        if (positions.length > 0) {
          const last = positions[positions.length - 1];
          if (map.Cesium.Cartesian3.equalsEpsilon(last, cartesian, map.Cesium.Math.EPSILON5)) {
            continue;
          }
        }
        positions.push(cartesian);
      }
      return positions;
    }

    /**
    * 【新增】计算起飞折线的点位 (L型：垂直 -> 水平)
    */
    const getTakeoffLinePositions = () => {
      // 安全检查
      if (!takeoffPoint.value || pointEntities.value.length === 0 || !map.Cesium) return [];

      const to = takeoffPoint.value; // 起飞点
      const wp1 = pointEntities.value[0]; // 第一个航点

      // 获取航点1的高度（绝对高度）
      const targetHeight = wp1.position[2];

      // 点1: 地面起飞点
      const p1 = map.Cesium.Cartesian3.fromDegrees(to.longitude, to.latitude, to.height);

      // 点2: 起飞点正上方，高度等于航点1的高度 (垂直爬升段)
      // 注意：如果航点1高度低于起飞点，视觉上会表现为下降，逻辑上兼容
      const p2 = map.Cesium.Cartesian3.fromDegrees(to.longitude, to.latitude, targetHeight);

      // 点3: 航点1的位置 (水平飞行段)
      const p3 = map.Cesium.Cartesian3.fromDegrees(wp1.position[0], wp1.position[1], wp1.position[2]);

      return [p1, p2, p3];
    }
    /**
     * 初始化动态航线实体与折线
     * 使用 CallbackProperty 绑定 geometry，无需手动 update
     */
    const initDynamicRoute = (Cesium) => {
      if (!map.viewer) return;

      dynamicRouteLine.value = {
        // 核心：使用 CallbackProperty 实现高性能动态更新
        positions: new Cesium.CallbackProperty(() => {
          return getSmoothedPositions();
        }, false),
        material: new Cesium.ColorMaterialProperty(Cesium.Color.CYAN.withAlpha(0.8))
      };

      if (!map.viewer.entities.getById('dynamic-route-line')) {
        map.viewer.entities.add({
          id: 'dynamic-route-line',
          polyline: {
            positions: dynamicRouteLine.value.positions,
            width: 5,
            material: dynamicRouteLine.value.material,
            depthFailMaterial: new Cesium.ColorMaterialProperty(Cesium.Color.CYAN.withAlpha(0.4)),
            clampToGround: false
          }
        });
      }

      // 2. 起飞折线 (使用 CallbackProperty 保证拖拽航点1时不卡顿)
      takeoffLine.value = {
        positions: new Cesium.CallbackProperty(() => {
          return getTakeoffLinePositions();
        }, false),
        // 使用虚线材质以示区分
        material: new Cesium.PolylineDashMaterialProperty({
          color: Cesium.Color.LIME, // 亮绿色
          dashLength: 16.0
        }),
        // 当被地形遮挡时显示的颜色（可选，保证线条始终可见）
        depthFailMaterial: new Cesium.PolylineDashMaterialProperty({
          color: Cesium.Color.LIME.withAlpha(0.4),
          dashLength: 16.0
        })
      };

      if (!map.viewer.entities.getById('takeoff-route-line')) {
        map.viewer.entities.add({
          id: 'takeoff-route-line',
          polyline: {
            positions: takeoffLine.value.positions,
            width: 3,
            material: takeoffLine.value.material,
            depthFailMaterial: takeoffLine.value.depthFailMaterial
          }
        });
      }
    }

    // ----------------------------------------------------------------
    // 原生 Cesium 实体同步 (航点、航线、起飞点、辅助线条与图例)
    // ----------------------------------------------------------------

    // 参考起飞点实体同步
    const syncTakeoffPointEntity = () => {
      if (!map.viewer || !map.Cesium) return;
      let entity = map.viewer.entities.getById('takeoff-point-entity');

      if (takeoffPoint.value) {
        if (!entity) {
          entity = map.viewer.entities.add({
            id: 'takeoff-point-entity',
            position: new map.Cesium.CallbackProperty(() => {
              if (!takeoffPoint.value) return null;
              return map.Cesium.Cartesian3.fromDegrees(
                takeoffPoint.value.longitude,
                takeoffPoint.value.latitude,
                Number(takeoffPoint.value.height || 0) + 10
              );
            }, false),
            point: {
              pixelSize: 14,
              color: map.Cesium.Color.RED,
              outlineColor: map.Cesium.Color.WHITE,
              outlineWidth: 2,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            },
            label: {
              text: '参考起飞点',
              font: '14px sans-serif',
              fillColor: map.Cesium.Color.WHITE,
              showBackground: true,
              backgroundColor: map.Cesium.Color.RED.withAlpha(0.8),
              pixelOffset: new map.Cesium.Cartesian2(0, -25),
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            }
          });
        }
      } else {
        if (entity) {
          map.viewer.entities.remove(entity);
        }
      }
    }

    // 航点实体同步
    const syncWaypointEntities = () => {

      if (!map.viewer || !map.Cesium) return;

      const currentCount = pointEntities.value.length;

      for (let i = 0; i < currentCount; i++) {
        const id = `waypoint-${i}`;
        let entity = map.viewer.entities.getById(id);

        if (!entity) {
          const index = i;
          entity = map.viewer.entities.add({
            id,
            position: new map.Cesium.CallbackProperty(() => {
              const pt = pointEntities.value[index];
              if (!pt || !pt.position) return null;
              return map.Cesium.Cartesian3.fromDegrees(pt.position[0], pt.position[1], pt.position[2]);
            }, false),
            billboard: {
              image: new map.Cesium.CallbackProperty(() => {
                const pt = pointEntities.value[index];
                return pt?.billboard?.image || mapBjIcon;
              }, false),
              width: 28,
              height: 34,
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            },
            label: {
              text: new map.Cesium.CallbackProperty(() => {
                const pt = pointEntities.value[index];
                return pt?.label?.text || `航点${index + 1}`;
              }, false),
              font: '14px sans-serif',
              fillColor: new map.Cesium.CallbackProperty(() => {
                const curIdx = selectedWaypointIndex.value >= 0 ? selectedWaypointIndex.value : 0;
                if (curIdx === index) {
                  return map.Cesium.Color.fromCssColorString('#00f0ff');
                }
                const pt = pointEntities.value[index];
                if (pt?.label?.fillColor) {
                  return map.Cesium.Color.fromBytes(...pt.label.fillColor);
                }
                return map.Cesium.Color.WHITE;
              }, false),
              showBackground: true,
              backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.6),
              pixelOffset: new map.Cesium.Cartesian2(0, -40),
              verticalOrigin: map.Cesium.VerticalOrigin.BOTTOM,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            }
          });
        }
      }

      // 清理超过当前数量的旧航点实体
      let extraIndex = currentCount;
      while (true) {
        const extraEntity = map.viewer.entities.getById(`waypoint-${extraIndex}`);
        if (extraEntity) {
          map.viewer.entities.remove(extraEntity);
          extraIndex++;
        } else {
          break;
        }
      }
    }

    // 辅助线条 (垂直投影线 + 段距离标签) 实体同步
    const syncAuxiliaryEntities = () => {
      if (!map.viewer || !map.Cesium) return;

      // 1. 同步垂直线
      const vCount = verticalLines.value.length;
      for (let i = 0; i < vCount; i++) {
        const vlineData = verticalLines.value[i];
        const id = vlineData.id;
        let entity = map.viewer.entities.getById(id);
        if (!entity) {
          entity = map.viewer.entities.add({
            id,
            polyline: {
              positions: vlineData.positions,
              width: 2,
              material: vlineData.material
            }
          });
        }
      }
      let extraV = vCount;
      while (true) {
        const extraEntity = map.viewer.entities.getById(`vline-${extraV}`);
        if (extraEntity) {
          map.viewer.entities.remove(extraEntity);
          extraV++;
        } else {
          break;
        }
      }

      // 2. 同步距离标签
      const distCount = waypointDistanceLabels.value.length;
      for (let i = 0; i < distCount; i++) {
        const labelData = waypointDistanceLabels.value[i];
        const id = labelData.id;
        let entity = map.viewer.entities.getById(id);
        const labelIndex = i;
        if (!entity) {
          entity = map.viewer.entities.add({
            id,
            position: new map.Cesium.CallbackProperty(() => {
              const item = waypointDistanceLabels.value[labelIndex];
              if (!item || !item.position) return null;
              return map.Cesium.Cartesian3.fromDegrees(...item.position);
            }, false),
            label: {
              text: new map.Cesium.CallbackProperty(() => {
                const item = waypointDistanceLabels.value[labelIndex];
                return item ? item.text : '';
              }, false),
              font: '12px sans-serif',
              fillColor: map.Cesium.Color.YELLOW,
              showBackground: true,
              backgroundColor: map.Cesium.Color.BLACK.withAlpha(0.5),
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            }
          });
        }
      }
      let extraDist = distCount;
      while (true) {
        const extraEntity = map.viewer.entities.getById(`dist-${extraDist}`);
        if (extraEntity) {
          map.viewer.entities.remove(extraEntity);
          extraDist++;
        } else {
          break;
        }
      }
    }

    const FRUSTUM_FACE_IDS = Array.from({ length: 4 }, (_, index) => `waypoint-frustum-face-${index}`)
    const FRUSTUM_ENTITY_IDS = [
      ...FRUSTUM_FACE_IDS,
      'waypoint-frustum-far-plane',
      'waypoint-frustum-edges',
      'waypoint-frustum-center-line',
      'waypoint-frustum-target',
      'waypoint-frustum-pyramid'
    ]

    const removeFrustumEntities = () => {
      if (!map.viewer || map.viewer.isDestroyed?.()) return
      FRUSTUM_ENTITY_IDS.forEach(id => {
        const entity = map.viewer.entities.getById(id)
        if (entity) map.viewer.entities.remove(entity)
      })
    }

    // 立体视锥实体同步
    const syncFrustumEntities = () => {
      if (!map.viewer || !map.Cesium || map.viewer.isDestroyed?.()) return

      const frustum = waypointFrustums.value?.[0]
      if (!showFrustum.value || !frustum?.origin || frustum.groundPoints?.length !== 4 || !frustum.centerPoint) {
        removeFrustumEntities()
        return
      }

      const Cesium = map.Cesium
      const origin = Cesium.Cartesian3.fromDegrees(...frustum.origin)
      const farPoints = frustum.groundPoints.map(point => Cesium.Cartesian3.fromDegrees(...point))
      const centerPoint = Cesium.Cartesian3.fromDegrees(...frustum.centerPoint)
      const faceColor = Cesium.Color.fromCssColorString('#FFB23E').withAlpha(0.13)
      const farPlaneColor = Cesium.Color.fromCssColorString('#FFC247').withAlpha(0.08)
      const edgeColor = Cesium.Color.fromCssColorString('#FFC247')
      const centerColor = Cesium.Color.fromCssColorString('#FFE6A3')

      // 清除旧版单线框实体，避免热更新后叠加
      const legacyEntity = map.viewer.entities.getById('waypoint-frustum-pyramid')
      if (legacyEntity) map.viewer.entities.remove(legacyEntity)

      FRUSTUM_FACE_IDS.forEach((id, index) => {
        const nextIndex = (index + 1) % farPoints.length
        const hierarchy = new Cesium.PolygonHierarchy([origin, farPoints[index], farPoints[nextIndex]])
        let entity = map.viewer.entities.getById(id)
        if (!entity) {
          entity = map.viewer.entities.add({
            id,
            polygon: {
              hierarchy,
              material: new Cesium.ColorMaterialProperty(faceColor),
              perPositionHeight: true,
              arcType: Cesium.ArcType.NONE,
              outline: false
            }
          })
        } else {
          entity.polygon.hierarchy = hierarchy
        }
      })

      const farPlaneHierarchy = new Cesium.PolygonHierarchy(farPoints)
      let farPlaneEntity = map.viewer.entities.getById('waypoint-frustum-far-plane')
      if (!farPlaneEntity) {
        farPlaneEntity = map.viewer.entities.add({
          id: 'waypoint-frustum-far-plane',
          polygon: {
            hierarchy: farPlaneHierarchy,
            material: new Cesium.ColorMaterialProperty(farPlaneColor),
            perPositionHeight: true,
            arcType: Cesium.ArcType.NONE,
            outline: false
          }
        })
      } else {
        farPlaneEntity.polygon.hierarchy = farPlaneHierarchy
      }

      const edgePositions = []
      farPoints.forEach(point => edgePositions.push(origin, point))
      farPoints.forEach((point, index) => edgePositions.push(point, farPoints[(index + 1) % farPoints.length]))
      let edgeEntity = map.viewer.entities.getById('waypoint-frustum-edges')
      if (!edgeEntity) {
        edgeEntity = map.viewer.entities.add({
          id: 'waypoint-frustum-edges',
          polyline: {
            positions: edgePositions,
            width: 2.5,
            material: new Cesium.ColorMaterialProperty(edgeColor.withAlpha(0.95)),
            arcType: Cesium.ArcType.NONE
          }
        })
      } else {
        edgeEntity.polyline.positions = edgePositions
      }

      const centerLinePositions = [origin, centerPoint]
      let centerLineEntity = map.viewer.entities.getById('waypoint-frustum-center-line')
      if (!centerLineEntity) {
        centerLineEntity = map.viewer.entities.add({
          id: 'waypoint-frustum-center-line',
          polyline: {
            positions: centerLinePositions,
            width: 1.5,
            material: new Cesium.PolylineDashMaterialProperty({
              color: centerColor.withAlpha(0.85),
              dashLength: 12
            }),
            arcType: Cesium.ArcType.NONE
          }
        })
      } else {
        centerLineEntity.polyline.positions = centerLinePositions
      }

      let targetEntity = map.viewer.entities.getById('waypoint-frustum-target')
      if (!targetEntity) {
        targetEntity = map.viewer.entities.add({
          id: 'waypoint-frustum-target',
          position: centerPoint,
          point: {
            pixelSize: 10,
            color: edgeColor,
            outlineColor: Cesium.Color.WHITE,
            outlineWidth: 2
          },
          label: {
            text: '🎯 镜头瞄准中心点',
            font: '12px sans-serif',
            fillColor: Cesium.Color.WHITE,
            showBackground: true,
            backgroundColor: Cesium.Color.fromBytes(0, 0, 0, 180),
            pixelOffset: new Cesium.Cartesian2(0, -18),
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            disableDepthTestDistance: Number.POSITIVE_INFINITY
          }
        })
      } else {
        targetEntity.position = centerPoint
      }

      map.viewer.scene.requestRender()
    }

    // 机场实体与可飞范围同步
    const syncDockEntities = () => {
      if (!map.viewer || !map.Cesium) return;
      if (!triColorGradientMaterial.value) {
        initMaterials();
      }

      const docks = dockMap.value || [];
      docks.forEach(dock => {
        const id = `dock-${dock.sn}`;
        let entity = map.viewer.entities.getById(id);
        if (!entity) {
          entity = map.viewer.entities.add({
            id,
            position: map.Cesium.Cartesian3.fromDegrees(dock.longitude, dock.latitude, Number(dock.height) + 30),
            billboard: {
              image: '/map_png/dockBilibord.png',
              scale: 1.5,
              color: map.Cesium.Color.RED,
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            },
            label: {
              text: dock.callsign || dock.sn,
              font: '16px Microsoft YaHei',
              fillColor: map.Cesium.Color.WHITE,
              showBackground: true,
              backgroundColor: new map.Cesium.Color(0, 0.47, 0.84, 0.8),
              pixelOffset: new map.Cesium.Cartesian2(0, -35),
              disableDepthTestDistance: Number.POSITIVE_INFINITY
            },
            ellipse: {
              semiMajorAxis: 10000,
              semiMinorAxis: 10000,
              material: triColorGradientMaterial.value || new map.Cesium.ColorMaterialProperty(map.Cesium.Color.GREEN.withAlpha(0.2)),
              outline: true,
              outlineColor: map.Cesium.Color.GREEN,
              outlineWidth: 2,
              show: new map.Cesium.CallbackProperty(() => showFlightRange.value, false)
            }
          });
        } else {
          if (entity.label) {
            entity.label.backgroundColor = new map.Cesium.Color(0, 0.47, 0.84, 0.8);
          }
        }
      });
    }

    // ----------------------------------------------------------------
    // 辅助显示逻辑 (距离标签、垂直投影) - 拖拽结束后更新即可
    // ----------------------------------------------------------------

    const updateAuxiliaryGraphics = () => {
      if (!map.Cesium || pointEntities.value.length === 0) return;

      const labels = [];

      // 临时数组
      const vLinesRaw = [];

      // 预先创建材质属性，避免循环中重复创建
      // 显式创建 ColorMaterialProperty，不要直接传 Color 对象
      const lineMaterial = new map.Cesium.PolylineDashMaterialProperty({
        color: map.Cesium.Color.YELLOW.withAlpha(0.4),
        dashLength: 2
      });

      for (let i = 0; i < pointEntities.value.length; i++) {
        const curr = pointEntities.value[i];

        vLinesRaw.push({
          id: `vline-${i}`,
          // 直接存入 Cartesian3 对象数组
          positions: new map.Cesium.CallbackProperty(() => {
            // 每次渲染时都从 pointEntities 读取最新位置
            if (!pointEntities.value[i]) return [];
            const point = pointEntities.value[i];
            const topPos = map.Cesium.Cartesian3.fromDegrees(
              point.position[0],
              point.position[1],
              point.position[2]
            );

            // 获取地面高度：优先使用采样出的地形/模型高程或通过 AGL 推算的地面高程，其次尝试读取当前视口加载的 globe 高度，兜底使用参考起飞点高程，坚决避免硬编码 0m 导致引线下穿渗入地下
            let groundH = 0;
            if (point.terrainHeight !== undefined && point.terrainHeight !== null && !Number.isNaN(point.terrainHeight)) {
              groundH = point.terrainHeight;
            } else if (point.agl !== undefined && point.agl !== null && !Number.isNaN(point.agl)) {
              groundH = point.position[2] - point.agl;
            } else {
              const carto = map.Cesium.Cartographic.fromDegrees(point.position[0], point.position[1]);
              const gh = map.viewer?.scene?.globe?.getHeight ? map.viewer.scene.globe.getHeight(carto) : undefined;
              if (gh !== undefined && gh !== null && !Number.isNaN(gh)) {
                groundH = gh;
              } else if (takeoffPoint.value && takeoffPoint.value.height !== undefined && !Number.isNaN(takeoffPoint.value.height)) {
                groundH = Number(takeoffPoint.value.height);
              }
            }

            const bottomPos = map.Cesium.Cartesian3.fromDegrees(
              point.position[0],
              point.position[1],
              groundH
            );
            return [topPos, bottomPos];
          }, false),
          material: lineMaterial
        });

        // 起飞点到航点1显示
        if (i === 0 && takeoffPoint.value) {
          const prev = takeoffPoint.value;
          const prevPos = map.Cesium.Cartesian3.fromDegrees(prev.longitude, prev.latitude, curr.position[2]);
          const currPos = map.Cesium.Cartesian3.fromDegrees(curr.position[0], curr.position[1], curr.position[2]);
          const distance = map.Cesium.Cartesian3.distance(prevPos, currPos);

          const midPoint = map.Cesium.Cartesian3.lerp(prevPos, currPos, 0.5, new map.Cesium.Cartesian3());
          const midCarto = map.Cesium.Cartographic.fromCartesian(midPoint);

          labels.push({
            id: `dist-${i}`,
            position: [
              map.Cesium.Math.toDegrees(midCarto.longitude),
              map.Cesium.Math.toDegrees(midCarto.latitude),
              midCarto.height + 10
            ],
            text: `${(distance).toFixed(0)}m`
          });
        }

        // 距离标签 (只在点之间显示)
        if (i > 0) {
          const prev = pointEntities.value[i - 1];
          const prevPos = map.Cesium.Cartesian3.fromDegrees(prev.position[0], prev.position[1], prev.position[2]);
          const currPos = map.Cesium.Cartesian3.fromDegrees(curr.position[0], curr.position[1], curr.position[2]);
          const distance = map.Cesium.Cartesian3.distance(prevPos, currPos);

          const midPoint = map.Cesium.Cartesian3.lerp(prevPos, currPos, 0.5, new map.Cesium.Cartesian3());
          const midCarto = map.Cesium.Cartographic.fromCartesian(midPoint);

          labels.push({
            id: `dist-${i}`,
            position: [
              map.Cesium.Math.toDegrees(midCarto.longitude),
              map.Cesium.Math.toDegrees(midCarto.latitude),
              midCarto.height + 10
            ],
            text: `${(distance).toFixed(0)}m`
          });
        }

      }

      waypointDistanceLabels.value = labels;
      verticalLines.value = vLinesRaw;

      // 同步渲染原生 Cesium 辅助实体
      syncAuxiliaryEntities();
    }

    // ----------------------------------------------------------------
    // 优化 4: 安全飞行 - 地形高度检测 (AGL)
    // ----------------------------------------------------------------

    // 用于跟踪当前航线的唯一标识，防止切换航线时旧数据污染
    let currentWaylineId = ref(0);

    // 🎯 优化：智能预加载航点区域的地形数据（用户无感知）
    const preloadTerrainForWaypoints = async () => {
      if (!map.viewer || !map.Cesium || pointEntities.value.length === 0) return;

      try {
        // 1. 准备所有航点的地理坐标
        const cartographics = pointEntities.value.map(point => {
          return map.Cesium.Cartographic.fromDegrees(point.position[0], point.position[1]);
        });

        // 2. 使用 Cesium 的地形采样 API 触发地形瓦片请求（不移动相机）
        // sampleTerrain 会自动向服务器请求所需的地形瓦片
        const terrainProvider = map.viewer.terrainProvider;
        
        // 先触发低精度采样（快速预热）
        await map.Cesium.sampleTerrain(terrainProvider, 11, cartographics);
        
        // 再触发高精度采样（确保数据完整）
        await map.Cesium.sampleTerrain(terrainProvider, 15, cartographics);

        console.log('✅ 地形预加载完成（无相机移动）：', cartographics.length, '个航点');

      } catch (error) {
        console.warn('地形预加载失败（将回退到批量采样）:', error);
      }
    };

    const updateTerrainHeight = async (index) => {
      if (!map.viewer.terrainProvider || !pointEntities.value[index]) return;

      const point = pointEntities.value[index];
      const [lng, lat, height] = point.position;
      const cartoPos = map.Cesium.Cartographic.fromDegrees(lng, lat);

      // 记录当前航线ID，用于检测航线切换
      if (!point._waylineId) {
        point._waylineId = currentWaylineId.value;
      }

      try {
        let terrainHeight = undefined;

        // 1. 优先尝试从 terrainProvider 进行最高精度采样（不受场景图元、3D 视口、航点自身图标遮挡影响）
        if (map.viewer.terrainProvider && map.viewer.terrainProvider.availability) {
          try {
            const sampled = await map.Cesium.sampleTerrainMostDetailed(
              map.viewer.terrainProvider,
              [cartoPos]
            );
            if (sampled && sampled[0] && sampled[0].height !== undefined && !Number.isNaN(sampled[0].height) && sampled[0].height !== null) {
              terrainHeight = sampled[0].height;
            }
          } catch (e) {}
        }

        // 2. 备选：射线拾取 globe 地表
        if (terrainHeight === undefined) {
          const rayOrigin = map.Cesium.Cartesian3.fromDegrees(lng, lat, 50000); // 从50000米高空
          const rayDirection = map.Cesium.Cartesian3.normalize(
            map.Cesium.Cartesian3.subtract(
              map.Cesium.Cartesian3.ZERO,
              rayOrigin,
              new map.Cesium.Cartesian3()
            ),
            new map.Cesium.Cartesian3()
          );
          const ray = new map.Cesium.Ray(rayOrigin, rayDirection);

          const intersection = map.viewer.scene.globe.pick(ray, map.viewer.scene);

          if (intersection) {
            const carto = map.Cesium.Cartographic.fromCartesian(intersection);
            terrainHeight = carto.height;
          } else {
            // Fallback: 如果射线没打中，尝试直接获取高度
            terrainHeight = map.viewer.scene.globe.getHeight(cartoPos);
          }
        }

        // 如果地形高度尚未加载出来（返回 undefined），则延时重试，且不更新 AGL，避免显示错误的极大值
        if (terrainHeight === undefined || Number.isNaN(terrainHeight) || terrainHeight === null) {
          if (point._retryCount === undefined) point._retryCount = 0;
          if (point._retryCount < 15) { // 增加重试次数到15次
            point._retryCount++;
            // 使用递增的重试延迟：前5次每500ms，之后每1500ms
            const retryDelay = point._retryCount <= 5 ? 500 : 1500;
            setTimeout(() => {
              // 确保重试时，该航点仍然存在、没有被替换、且属于当前航线
              if (pointEntities.value[index] === point && 
                  point._waylineId === currentWaylineId.value) {
                updateTerrainHeight(index);
              }
            }, retryDelay);
          } else {
            // 重试超时，使用参考起飞点高度作为兜底地面高度
            const defaultGround = (takeoffPoint.value && takeoffPoint.value.height !== undefined && !Number.isNaN(takeoffPoint.value.height)) ? Number(takeoffPoint.value.height) : 0;
            console.warn(`航点${index + 1} 地形数据加载超时，使用参考地面高度 ${defaultGround}m`);
            point.terrainHeight = defaultGround;
            point.agl = Math.max(0, height - defaultGround);
            const relH = takeoffPoint.value ? (height - takeoffPoint.value.height) : height;
            pointEntities.value[index].label.text = `航点${index + 1} H:${relH.toFixed(0)}m(相对)\n⚠️地形未加载`;
            pointEntities.value[index].label.fillColor = [255, 165, 0, 255]; // 橙色警告
            pointEntities.value[index].updateKey = Date.now();
          }
          return;
        }

        // 成功获取后重置重试计数
        point._retryCount = 0;

        const agl = height - terrainHeight;

        // 响应式更新 AGL 与地形高度字段
        pointEntities.value[index].agl = agl;
        pointEntities.value[index].terrainHeight = terrainHeight;

        // 更新 Label，杜绝 -0m 显示与浮点微量负值误报撞地
        const displayAgl = Math.abs(agl) < 0.05 ? 0 : Math.round(agl);
        const relH = takeoffPoint.value ? (height - takeoffPoint.value.height) : height;
        let text = `航点${index + 1} H:${relH.toFixed(0)}m(相对)`;
        if (agl < 30) {
          text += `\n⚠️距地:${displayAgl}m`;
          if (agl < -0.5) {
            text += ` (撞地!)`;
          }
          pointEntities.value[index].label.fillColor = [255, 0, 0, 255]; // 红色
        } else {
          pointEntities.value[index].label.fillColor = [255, 255, 255, 255]; // 白色
        }
        pointEntities.value[index].label.text = text;
        // 更新updateKey强制Vue重新渲染
        pointEntities.value[index].updateKey = Date.now();

      } catch (e) {
        console.warn("地形采样失败", e);
      }
    }


    // ----------------------------------------------------------------
    // 鼠标事件与拖拽逻辑
    // ----------------------------------------------------------------

    const setupWaypointDragHandlers = (viewer, Cesium) => {
      const canvas = viewer.scene.canvas;
      const handler = new Cesium.ScreenSpaceEventHandler(canvas);

      // Prevent browser default on Alt+Click / Shift+Click (download, new window, etc.)
      canvas.addEventListener('mousedown', (e) => {
        if (e.altKey || e.shiftKey) {
          e.preventDefault()
        }
      }, true)
      canvas.addEventListener('contextmenu', (e) => {
        e.preventDefault()
      }, true)

      // Adaptive vertical sensitivity based on camera altitude
      const getVerticalSensitivity = () => {
        try {
          const cameraHeight = viewer.camera.positionCartographic.height;
          return Math.max(0.2, Math.min(5.0, cameraHeight / 2000));
        } catch (e) {
          return 0.5;
        }
      }

      // ---- Shared handler logic (reused for plain + modifier events) ----

      const handleLeftDown = (click, forceVertical) => {
        if (!click || !click.position) return;
        if (fpvState.isActive) {
          fpvState.isDragging = true;
          fpvState.startMouseX = click.position.x;
          fpvState.startMouseY = click.position.y;
          return;
        }

        const picked = viewer.scene.pick(click.position);
        if (Cesium.defined(picked) && picked.id && typeof picked.id.id === 'string') {
          const match = picked.id.id.match(/^(?:waypoint|vline)-(\d+)$/);
          if (match) {
            const index = parseInt(match[1]);
            if (index >= 0 && index < pointEntities.value.length) {
              selectedWaypointIndex.value = index;
            }

            // 预览模式下严格禁止拖拽航点
            if (isPreviewMode.value) {
              return;
            }

            const p = pointEntities.value && pointEntities.value[index];
            if (!p || !p.position) return;

            waypointDragState.isDragging = true;
            waypointDragState.waypointIndex = index;
            // forceVertical = true when called from ALT/SHIFT modifier handler
            waypointDragState.mode = (forceVertical || waypointDragState.altKeyPressed) ? 'vertical' : 'horizontal';
            waypointDragState.startMouseY = click.position.y;
            waypointDragState.startHeight = p.position[2];

            dragCurrentHeight.value = p.position[2];
            dragHeightDelta.value = 0;
            dragTooltipPos.x = click.position.x;
            dragTooltipPos.y = click.position.y;

            console.log(`[WaypointDrag] START waypoint-${index}, mode=${waypointDragState.mode}, forceVertical=${forceVertical}, altKeyPressed=${waypointDragState.altKeyPressed}`)

            viewer.scene.screenSpaceCameraController.enableRotate = false;
            if (waypointDragState.mode === 'vertical') {
              viewer.scene.screenSpaceCameraController.enableTranslate = false;
              viewer.scene.screenSpaceCameraController.enableZoom = false;
            }
          }
        }
      }

      const handleMouseMove = (movement) => {
        if (!movement || !movement.endPosition) return;
        if (isPreviewMode.value && !fpvState.isActive) return;
        if (fpvState.isActive && fpvState.isDragging) {
          const dx = movement.endPosition.x - fpvState.startMouseX;
          const dy = movement.endPosition.y - fpvState.startMouseY;
          fpvState.startMouseX = movement.endPosition.x;
          fpvState.startMouseY = movement.endPosition.y;

          const camera = viewer.camera;
          const lookFactor = 0.002;
          camera.lookRight(dx * lookFactor);
          camera.lookUp(dy * lookFactor);

          // Constrain pitch
          if (camera.pitch > Cesium.Math.toRadians(35)) camera.setView({ orientation: { heading: camera.heading, pitch: Cesium.Math.toRadians(35), roll: 0 } });
          if (camera.pitch < Cesium.Math.toRadians(-90)) camera.setView({ orientation: { heading: camera.heading, pitch: Cesium.Math.toRadians(-90), roll: 0 } });

          syncFpvToWaypointAction();
          return;
        }

        if (!waypointDragState.isDragging) return;
        const index = waypointDragState.waypointIndex;
        const point = pointEntities.value && pointEntities.value[index];
        if (!point || !point.position) return;

        dragTooltipPos.x = movement.endPosition.x;
        dragTooltipPos.y = movement.endPosition.y;

        if (waypointDragState.mode === 'vertical') {
          const sensitivity = getVerticalSensitivity();
          const dy = waypointDragState.startMouseY - movement.endPosition.y;
          let h = waypointDragState.startHeight + dy * sensitivity;
          if (h < 0) h = 0;
          point.position = [point.position[0], point.position[1], h];

          dragCurrentHeight.value = h;
          dragHeightDelta.value = h - waypointDragState.startHeight;

          const delta = dragHeightDelta.value;
          const deltaStr = delta >= 0 ? `+${delta.toFixed(0)}` : delta.toFixed(0);
          const relH = takeoffPoint.value ? (h - takeoffPoint.value.height) : h;
          if (point.label) {
            point.label.text = `H:${relH.toFixed(0)}m (${deltaStr})`;
            point.label.fillColor = [0, 255, 255, 255];
          }
        } else {
          const ray = viewer.scene.camera.getPickRay(movement.endPosition);
          const cartesian = viewer.scene.globe.pick(ray, viewer.scene);
          if (cartesian) {
            const carto = Cesium.Cartographic.fromCartesian(cartesian);
            const lng = Cesium.Math.toDegrees(carto.longitude);
            const lat = Cesium.Math.toDegrees(carto.latitude);
            point.position = [lng, lat, point.position[2]];
          }
        }
      }

      const handleLeftUp = () => {
        if (fpvState.isActive) {
          fpvState.isDragging = false;
          return;
        }

        if (waypointDragState.isDragging) {
          const idx = waypointDragState.waypointIndex;
          viewer.scene.screenSpaceCameraController.enableRotate = true;
          viewer.scene.screenSpaceCameraController.enableTranslate = true;
          viewer.scene.screenSpaceCameraController.enableZoom = true;
          waypointDragState.isDragging = false;
          waypointDragState.waypointIndex = -1;

          const point = pointEntities.value[idx];
          if (point) {
            point.label.fillColor = [255, 255, 255, 255];
          }

          updateAuxiliaryGraphics();
          updateTerrainHeight(idx);
        }
      }

      // ---- Register for PLAIN events (no modifier) ----
      handler.setInputAction((click) => handleLeftDown(click, false), Cesium.ScreenSpaceEventType.LEFT_DOWN);
      handler.setInputAction(handleMouseMove, Cesium.ScreenSpaceEventType.MOUSE_MOVE);
      handler.setInputAction(handleLeftUp, Cesium.ScreenSpaceEventType.LEFT_UP);

      // ---- Register for ALT modifier events ----
      // ★ Cesium routes Alt+Click to a DIFFERENT handler channel!
      handler.setInputAction((click) => handleLeftDown(click, true), Cesium.ScreenSpaceEventType.LEFT_DOWN, Cesium.KeyboardEventModifier.ALT);
      handler.setInputAction(handleMouseMove, Cesium.ScreenSpaceEventType.MOUSE_MOVE, Cesium.KeyboardEventModifier.ALT);
      handler.setInputAction(handleLeftUp, Cesium.ScreenSpaceEventType.LEFT_UP, Cesium.KeyboardEventModifier.ALT);

      // ---- Register for SHIFT modifier events ----
      handler.setInputAction((click) => handleLeftDown(click, true), Cesium.ScreenSpaceEventType.LEFT_DOWN, Cesium.KeyboardEventModifier.SHIFT);
      handler.setInputAction(handleMouseMove, Cesium.ScreenSpaceEventType.MOUSE_MOVE, Cesium.KeyboardEventModifier.SHIFT);
      handler.setInputAction(handleLeftUp, Cesium.ScreenSpaceEventType.LEFT_UP, Cesium.KeyboardEventModifier.SHIFT);

      // ---- Register for CTRL modifier too (in case user presses wrong key) ----
      handler.setInputAction((click) => handleLeftDown(click, false), Cesium.ScreenSpaceEventType.LEFT_DOWN, Cesium.KeyboardEventModifier.CTRL);
      handler.setInputAction(handleMouseMove, Cesium.ScreenSpaceEventType.MOUSE_MOVE, Cesium.KeyboardEventModifier.CTRL);
      handler.setInputAction(handleLeftUp, Cesium.ScreenSpaceEventType.LEFT_UP, Cesium.KeyboardEventModifier.CTRL);

      // ---- Register RIGHT_CLICK & LEFT_CLICK events ----
      handler.setInputAction((e) => onRightClick(e), Cesium.ScreenSpaceEventType.RIGHT_CLICK);
      handler.setInputAction((e) => onLeftClick(e), Cesium.ScreenSpaceEventType.LEFT_CLICK);
    }


    // ----------------------------------------------------------------
    // business logic
    // ----------------------------------------------------------------




    // 新增航点 (在末尾)
    const addWaypoint = () => {
      if (!takeoffPoint.value) return message.warn('请先设置起飞点');

      const { longitude, latitude } = contextMenu.position;
      const h = takeoffPoint.value.height + waylineGlobalParams.global_height;

      createWaypointData(parseFloat(longitude), parseFloat(latitude), h);
      contextMenu.visible = false;
    }

    // 优化 3: 插入航点 (在最近的线段中间)
    const insertWaypoint = () => {
      const { longitude, latitude } = contextMenu.position;
      const h = takeoffPoint.value.height + waylineGlobalParams.global_height;
      const newPos = map.Cesium.Cartesian3.fromDegrees(parseFloat(longitude), parseFloat(latitude), h);

      let minDistance = Number.MAX_VALUE;
      let insertIdx = pointEntities.value.length; // 默认追加

      // 遍历寻找最近的线段
      for (let i = 0; i < pointEntities.value.length - 1; i++) {
        const p1 = map.Cesium.Cartesian3.fromDegrees(...pointEntities.value[i].position);
        const p2 = map.Cesium.Cartesian3.fromDegrees(...pointEntities.value[i + 1].position);

        // 简单的距离判别：如果点击点到线段两端距离之和 ≈ 线段长度
        const d1 = map.Cesium.Cartesian3.distance(p1, newPos);
        const d2 = map.Cesium.Cartesian3.distance(p2, newPos);
        const lineLen = map.Cesium.Cartesian3.distance(p1, p2);

        if (Math.abs((d1 + d2) - lineLen) < minDistance) {
          minDistance = Math.abs((d1 + d2) - lineLen);
          // 只有非常接近线段时才认为是插入
          if (minDistance < 50) {
            insertIdx = i + 1;
          }
        }
      }

      createWaypointData(parseFloat(longitude), parseFloat(latitude), h, insertIdx);
      contextMenu.visible = false;
    }

    // 统一创建航点数据的方法
    const createWaypointData = (lng, lat, height, index = null) => {
      const targetIndex = index !== null ? index : pointEntities.value.length;
      const wp = {
        updateKey: Date.now(),
        position: [lng, lat, height],
        agl: undefined, // 初始距地高度
        yaw: 0,
        aircraft_heading: 0,
        gimbalPitch: -90,
        gimbal_pitch: -90,
        gimbalYaw: 0,
        gimbal_yaw: 0,
        billboard: {
          image: mapBjIcon,
          width: 28, height: 34,
          verticalOrigin: 1, // BOTTOM = 1
          heightReference: 0  // NONE = 0
        },
        label: {
          text: `航点...`,
          font: '14px sans-serif',
          fillColor: [255, 255, 255, 255], // 白色
          showBackground: true,
          backgroundColor: [0, 0, 0, 153], // 黑色半透明
          pixelOffset: [0, -40],
          verticalOrigin: 1, // BOTTOM = 1
          heightReference: 0  // NONE = 0
        },
        actionGroupList: [
          {
            action_group_id: 0,
            action_group_start_index: targetIndex,
            action_group_end_index: targetIndex,
            action_trigger_type: 'reachPoint',
            action_trigger_param: 0,
            actions: [
              {
                action_index: 0,
                action_func: 'rotateYaw',
                aircraft_heading: 0,
                aircraft_path_mode: 'clockwise'
              },
              {
                action_index: 1,
                action_func: 'gimbalRotate',
                gimbal_pitch_rotate_angle: -90,
                gimbal_yaw_rotate_angle: 0,
                payload_position_index: 0,
                use_global_payload_lens_index: 1
              }
            ]
          }
        ]
      };

      if (index !== null) {
        pointEntities.value.splice(index, 0, wp);
      } else {
        pointEntities.value.push(wp);
      }

      // 重新对所有动作组进行索引
      reindexAllActionGroups();

      // 更新序号 Label
      pointEntities.value.forEach((p, i) => {
        const relH = takeoffPoint.value ? (p.position[2] - takeoffPoint.value.height) : p.position[2];
        p.label.text = `航点${i + 1} H:${relH.toFixed(0)}m(相对)`;
      });

      // 触发辅助更新与视锥体更新
      updateAuxiliaryGraphics();
      updateTerrainHeight(index !== null ? index : pointEntities.value.length - 1);
      syncFrustumEntities();
      message.success(index !== null ? '插入航点成功' : '新增航点成功');
    }

    const deleteWaypoint = (index) => {
      pointEntities.value.splice(index, 1);
      selectedWaypointIndex.value = -1;
      reindexAllActionGroups();
      // 重刷序号
      pointEntities.value.forEach((p, i) => {
        // 保留原有的高度信息
        const h = p.position[2];
        const relH = takeoffPoint.value ? (h - takeoffPoint.value.height) : h;
        p.label.text = `航点${i + 1} H:${relH.toFixed(0)}m(相对)`;
      });
      updateAuxiliaryGraphics();
      syncFrustumEntities();
    }

    // ----------------------------------------------------------------
    // 动作组管理逻辑
    // ----------------------------------------------------------------

    const actionTriggerTypeOptions = [
      { value: 'reachPoint', label: '到达航点' },
      { value: 'betweenAdjacentPoints', label: '航段触发' },
      { value: 'multipleTiming', label: '等时触发' },
      { value: 'multipleDistance', label: '等距触发' }
    ];

    // ---- 等间隔拍照（跨航点动作组） ----
    const intervalPhotoGroups = ref([])
    const intervalPhotoEditing = ref(false)
    const editingIntervalId = ref(null)
    const intervalPhotoForm = reactive({
      startIndex: 0,
      endIndex: 1,
      triggerType: 'multipleTiming',
      triggerParam: 5,
      use_global_image_format: 1,
      image_format: 'visable,ir',
      file_suffix: ''
    })

    const openAddIntervalPhoto = () => {
      if (intervalPhotoEditing.value && !editingIntervalId.value) {
        intervalPhotoEditing.value = false
        return
      }
      editingIntervalId.value = null
      intervalPhotoForm.startIndex = 0
      intervalPhotoForm.endIndex = Math.min(1, Math.max(1, pointEntities.value.length - 1))
      intervalPhotoForm.triggerType = 'multipleTiming'
      intervalPhotoForm.triggerParam = 5
      intervalPhotoForm.use_global_image_format = 1
      intervalPhotoForm.image_format = 'visable,ir'
      intervalPhotoForm.file_suffix = ''
      intervalPhotoEditing.value = true
    }

    const editIntervalPhoto = (group) => {
      if (!group) return
      editingIntervalId.value = group.id
      intervalPhotoForm.startIndex = group.startIndex
      intervalPhotoForm.endIndex = group.endIndex
      intervalPhotoForm.triggerType = group.triggerType
      intervalPhotoForm.triggerParam = group.triggerParam
      intervalPhotoForm.use_global_image_format = group.use_global_image_format !== undefined ? group.use_global_image_format : 1
      intervalPhotoForm.image_format = group.image_format || group.imageFormat || group.payload_lens_index || 'visable,ir'
      intervalPhotoForm.file_suffix = group.file_suffix || ''
      intervalPhotoEditing.value = true
    }

    const saveIntervalPhoto = () => {
      const { startIndex, endIndex, triggerType, triggerParam, use_global_image_format, image_format, file_suffix } = intervalPhotoForm
      if (startIndex >= endIndex) {
        message.warning('结束航点必须大于起始航点')
        return
      }
      if (triggerParam <= 0) {
        message.warning('间隔值必须大于0')
        return
      }
      const conflict = intervalPhotoGroups.value.some(g => {
        if (editingIntervalId.value && g.id === editingIntervalId.value) return false
        return !(endIndex <= g.startIndex || startIndex >= g.endIndex)
      })
      if (conflict) {
        message.warning('与已有的等间隔拍照航段存在重叠')
        return
      }

      if (editingIntervalId.value) {
        const existing = intervalPhotoGroups.value.find(g => g.id === editingIntervalId.value)
        if (existing) {
          existing.startIndex = startIndex
          existing.endIndex = endIndex
          existing.triggerType = triggerType
          existing.triggerParam = triggerParam
          existing.use_global_image_format = use_global_image_format !== undefined ? use_global_image_format : 1
          existing.image_format = image_format || 'visable,ir'
          existing.file_suffix = file_suffix || ''
          message.success('等间隔拍照航段修改成功')
        }
      } else {
        intervalPhotoGroups.value.push({
          id: Date.now(),
          startIndex,
          endIndex,
          triggerType,
          triggerParam,
          use_global_image_format: use_global_image_format !== undefined ? use_global_image_format : 1,
          image_format: image_format || 'visable,ir',
          file_suffix: file_suffix || ''
        })
        message.success('等间隔拍照航段添加成功')
      }

      intervalPhotoEditing.value = false
      editingIntervalId.value = null
    }

    const cancelIntervalPhotoEdit = () => {
      intervalPhotoEditing.value = false
      editingIntervalId.value = null
    }

    const removeIntervalPhoto = (id) => {
      if (editingIntervalId.value === id) {
        editingIntervalId.value = null
        intervalPhotoEditing.value = false
      }
      intervalPhotoGroups.value = intervalPhotoGroups.value.filter(g => g.id !== id)
    }

    const intervalPhotoSegments = computed(() => {
      if (!map.Cesium || !pointEntities.value.length) return []
      return intervalPhotoGroups.value.map(g => {
        if (g.startIndex >= pointEntities.value.length || g.endIndex >= pointEntities.value.length) return null
        const positions = []
        for (let i = g.startIndex; i <= g.endIndex; i++) {
          positions.push(pointEntities.value[i].position)
        }
        return { ...g, positions }
      }).filter(Boolean)
    })

    const actionActuatorFuncOptions = [
      { value: 'takePhoto', label: '单拍' },
      { value: 'startRecord', label: '开始录像' },
      { value: 'stopRecord', label: '结束录像' },
      { value: 'focus', label: '对焦' },
      { value: 'zoom', label: '变焦' },
      { value: 'gimbalRotate', label: '旋转云台' },
      { value: 'rotateYaw', label: '飞行器偏航' },
      { value: 'hover', label: '悬停等待' },
      { value: 'customDirName', label: '创建文件夹' },
      { value: 'gimbalEvenlyRotate', label: '均匀转云台' },
      { value: 'orientedShoot', label: '定向拍照' },
      { value: 'panoShot', label: '全景拍照' },
      // { value: 'recordPointCloud', label: '点云录制' }
    ];

    const addActionGroup = (point, index) => {
      if (!point.actionGroupList) point.actionGroupList = [];
      point.actionGroupList.push({
        action_group_id: 0,
        action_group_start_index: index,
        action_group_end_index: index,
        action_trigger_type: 'reachPoint',
        action_trigger_param: 0,
        actions: []
      });
      reindexAllActionGroups()
    }

    const deleteActionGroup = (point, gIndex) => {
      point.actionGroupList.splice(gIndex, 1);
      reindexAllActionGroups()
    }

    const reindexAllActionGroups = () => {
      let globalGroupId = 0
      for (const point of pointEntities.value) {
        if (!point.actionGroupList) continue
        for (const group of point.actionGroupList) {
          group.action_group_id = globalGroupId++
        }
      }
    }

    const addAction = (group) => {
      group.actions.push({
        action_index: group.actions.length,
        action_func: 'hover',
        hover_time: 5
      });
    }

    const deleteAction = (group, aIndex) => {
      group.actions.splice(aIndex, 1);
      reindexActions(group)
    }

    const reindexActions = (group) => {
      group.actions.forEach((action, i) => {
        action.action_index = i
      })
    }

    // ---- 地图上航点动作气泡 ----
    const actionPopup = reactive({
      visible: false,
      x: 0,
      y: 0,
      pointIndex: -1
    })
    const actionPopupPoint = computed(() => {
      if (actionPopup.pointIndex < 0 || actionPopup.pointIndex >= pointEntities.value.length) return null
      return pointEntities.value[actionPopup.pointIndex]
    })
    const actionPopupGroup = computed(() => {
      const pt = actionPopupPoint.value
      if (!pt) return null
      if (!pt.actionGroupList || pt.actionGroupList.length === 0) return null
      return pt.actionGroupList[0]
    })

    const updateActionPopupPosition = () => {
      if (!actionPopup.visible || actionPopup.pointIndex < 0) return
      const pt = pointEntities.value[actionPopup.pointIndex]
      if (!pt || !map.viewer || !map.Cesium || !map.Cesium.SceneTransforms) return
      const pos = map.Cesium.Cartesian3.fromDegrees(pt.position[0], pt.position[1], pt.position[2])
      const transformFunc = map.Cesium.SceneTransforms.wgs84ToWindowCoordinates || map.Cesium.SceneTransforms.worldToWindowCoordinates
      const screenPos = transformFunc ? transformFunc(map.viewer.scene, pos) : null
      if (screenPos) {
        actionPopup.x = screenPos.x
        actionPopup.y = screenPos.y
      }
    }

    const openActionPopup = (waypointIndex) => {
      selectedWaypointIndex.value = waypointIndex
      if (isPreviewMode.value) return
      const pt = pointEntities.value[waypointIndex]
      if (!pt) return
      if (!pt.actionGroupList || pt.actionGroupList.length === 0) {
        addActionGroup(pt, waypointIndex)
      }
      clearCardRefs()
      actionPopup.pointIndex = waypointIndex
      actionPopup.visible = true
      selectedActionIndex.value = -1
      updateActionPopupPosition()
    }

    const closeActionPopup = () => {
      clearCardRefs()
      actionPopup.visible = false
      actionPopup.pointIndex = -1
      selectedActionIndex.value = -1
    }

    const selectWaypointAction = (waypointIndex, action) => {
      openActionPopup(waypointIndex)
      selectedActionIndex.value = action.action_index
      nextTick(() => {
        const el = document.getElementById(`action-card-${waypointIndex}-${action.action_index}`)
        if (el) {
          el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
        }
      })
    }

    const swapActions = (fromIdx, toIdx) => {
      const group = actionPopupGroup.value
      if (!group) return
      const actions = group.actions
      if (fromIdx === toIdx || fromIdx < 0 || toIdx < 0 || fromIdx >= actions.length || toIdx >= actions.length) return
      
      const movedItem = actions.splice(fromIdx, 1)[0]
      actions.splice(toIdx, 0, movedItem)
      
      reindexActions(group)
      
      // 同步高亮选中项
      if (selectedActionIndex.value === fromIdx) {
        selectedActionIndex.value = toIdx
      } else if (selectedActionIndex.value > fromIdx && selectedActionIndex.value <= toIdx) {
        selectedActionIndex.value--
      } else if (selectedActionIndex.value < fromIdx && selectedActionIndex.value >= toIdx) {
        selectedActionIndex.value++
      }
    }

    const clearCardRefs = () => {
      cardRefs.forEach(r => r.value = null)
    }

    const setCardRef = (el, index) => {
      if (cardRefs[index]) {
        cardRefs[index].value = el
      }
    }

    cardRefs.forEach((refItem, index) => {
      useDrag(index, refItem)
      useDrop(refItem, {
        onDom: (draggedIndex) => {
          swapActions(draggedIndex, index)
        }
      })
    })

    const quickAddAction = (funcName) => {
      let group = actionPopupGroup.value
      if (!group) {
        const pt = actionPopupPoint.value
        if (!pt) return
        if (!pt.actionGroupList || pt.actionGroupList.length === 0) {
          addActionGroup(pt, actionPopup.pointIndex)
        }
        group = pt.actionGroupList[0]
        if (!group) return
      }
      const action = { action_index: group.actions.length, action_func: funcName }
      if (funcName === 'hover') {
        action.hover_time = 5
      } else if (funcName === 'zoom') {
        action.zoom = 2
      } else if (funcName === 'gimbalRotate') {
        action.gimbal_pitch_rotate_angle = -90
        action.gimbal_yaw_rotate_angle = 0
        action.payload_position_index = 0
        action.use_global_payload_lens_index = 1
      } else if (funcName === 'rotateYaw') {
        action.aircraft_heading = 0
        action.aircraft_path_mode = 'clockwise'
      } else if (['takePhoto', 'startRecord', 'panoShot', 'orientedShoot'].includes(funcName)) {
        action.use_global_image_format = 1
        if (['takePhoto', 'startRecord', 'orientedShoot'].includes(funcName)) {
          action.image_format = 'visable,ir'
        }
        if (funcName === 'startRecord') {
          const recCount = getExistingRecordCount()
          action.file_suffix = `#${recCount + 1}`
        } else if (['takePhoto', 'orientedShoot'].includes(funcName)) {
          action.file_suffix = ''
        }
        if (funcName === 'orientedShoot') {
          action.gimbal_pitch_rotate_angle = -90
          action.gimbal_yaw_rotate_angle = 0
          action.zoom = 2
        }
      } else if (funcName === 'stopRecord') {
        const info = getPrecedingStartRecordInfo(actionPopup.pointIndex)
        if (!info.hasStart) {
          message.info('提示：当前航线尚未添加开始录像动作')
        } else if (!isCoordSameAsStartRecord(actionPopup.pointIndex)) {
          message.info(`已添加停录动作。提示：停止录像航点最好与录像航点(航点${info.startIndex + 1})经纬度一致`)
        }
      } else if (funcName === 'customDirName') {
        const d = new Date()
        const timeStr = `${d.getFullYear()}${(d.getMonth() + 1).toString().padStart(2, '0')}${d.getDate().toString().padStart(2, '0')}${d.getHours().toString().padStart(2, '0')}${d.getMinutes().toString().padStart(2, '0')}`
        const prefix = waylineGlobalParams.custom_dir_prefix || 'zxsl'
        action.directory_name = `${prefix}-${timeStr}`
      }
      group.actions.push(action)
    }

    const removePopupAction = (aIndex) => {
      const group = actionPopupGroup.value
      if (!group) return
      group.actions.splice(aIndex, 1)
      reindexActions(group)
    }

    // ----------------------------------------------------------------
    // 飞行器偏航角与云台偏航角 120° 夹角约束计算
    // ----------------------------------------------------------------
    // 计算两个偏航角在 [-180, 180] 周期内的最小有向角差 (angle1 - angle2)
    const getAngleDiff = (angle1, angle2) => {
      let diff = (Number(angle1 || 0) - Number(angle2 || 0)) % 360
      if (diff > 180) diff -= 360
      if (diff < -180) diff += 360
      return diff
    }

    // 统一合并提示：防抖并使用固定 key 更新同一条提示，避免短时间内弹出多条重复消息
    let lastYawWarningTime = 0
    const notifyYawLimitWarning = (msg = '飞行器偏航角与云台偏航角夹角最大不超过120°，已自动调整') => {
      const now = Date.now()
      if (now - lastYawWarningTime < 1500) return
      lastYawWarningTime = now
      message.warning({
        content: msg,
        key: 'yaw_limit_warning',
        duration: 2.5
      })
    }

    // 限制云台偏航角，使其与飞行器偏航角的夹角在 [-120°, 120°] 范围内
    const clampGimbalYaw = (gimbalYaw, aircraftYaw) => {
      const gYaw = Number(gimbalYaw || 0)
      const aYaw = Number(aircraftYaw || 0)
      const diff = getAngleDiff(gYaw, aYaw)
      if (diff > 120) {
        let clamped = aYaw + 120
        if (clamped > 180) clamped -= 360
        if (clamped < -180) clamped += 360
        return Number(clamped.toFixed(1))
      } else if (diff < -120) {
        let clamped = aYaw - 120
        if (clamped > 180) clamped -= 360
        if (clamped < -180) clamped += 360
        return Number(clamped.toFixed(1))
      }
      return gYaw
    }

    // 限制飞行器偏航角，使其与云台偏航角的夹角在 [-120°, 120°] 范围内
    const clampAircraftYaw = (aircraftYaw, gimbalYaw) => {
      const aYaw = Number(aircraftYaw || 0)
      const gYaw = Number(gimbalYaw || 0)
      const diff = getAngleDiff(gYaw, aYaw)
      if (diff > 120) {
        let clamped = gYaw - 120
        if (clamped > 180) clamped -= 360
        if (clamped < -180) clamped += 360
        return Number(clamped.toFixed(1))
      } else if (diff < -120) {
        let clamped = gYaw + 120
        if (clamped > 180) clamped -= 360
        if (clamped < -180) clamped += 360
        return Number(clamped.toFixed(1))
      }
      return aYaw
    }

    // 动作参数变更实时响应 (用于同步视锥体与第一视角)
    const onActionParamChange = (action) => {
      const pt = actionPopupPoint.value || (selectedWaypointIndex.value >= 0 ? pointEntities.value[selectedWaypointIndex.value] : null)
      if (pt) {
        if (action.action_func === 'rotateYaw' && action.aircraft_heading !== undefined) {
          let headingVal = Number(action.aircraft_heading)
          const gYaw = getWaypointGimbalYaw(pt)
          const diff = Math.abs(getAngleDiff(gYaw, headingVal))
          if (diff > 120) {
            headingVal = clampAircraftYaw(headingVal, gYaw)
            action.aircraft_heading = headingVal
            notifyYawLimitWarning('飞行器偏航角与云台偏航角夹角最大不超过120°，已自动调整')
          }
          pt.yaw = headingVal
          pt.aircraft_heading = headingVal
        }
        if (action.action_func === 'gimbalRotate' || action.action_func === 'orientedShoot') {
          if (action.gimbal_pitch_rotate_angle !== undefined) {
            pt.gimbalPitch = Number(action.gimbal_pitch_rotate_angle)
            pt.gimbal_pitch = Number(action.gimbal_pitch_rotate_angle)
          }
          if (action.gimbal_yaw_rotate_angle !== undefined) {
            let gYawVal = Number(action.gimbal_yaw_rotate_angle)
            const aYaw = getWaypointYaw(pt)
            const diff = Math.abs(getAngleDiff(gYawVal, aYaw))
            if (diff > 120) {
              gYawVal = clampGimbalYaw(gYawVal, aYaw)
              action.gimbal_yaw_rotate_angle = gYawVal
              notifyYawLimitWarning('云台偏航角与飞行器偏航角夹角最大不超过120°，已自动调整')
            }
            pt.gimbalYaw = gYawVal
            pt.gimbal_yaw = gYawVal
          }
        }
        if ((action.action_func === 'zoom' || action.action_func === 'orientedShoot') && action.zoom !== undefined) {
          pt.zoom = Number(action.zoom)
          pt.zoomFactor = Number(action.zoom)
        }
        pt.updateKey = Date.now()
      }
      syncFrustumEntities()
    }

    const getWaypointYaw = (point) => {
      if (point.actionGroupList) {
        for (const group of point.actionGroupList) {
          if (!group.actions) continue
          for (const act of group.actions) {
            if (act.action_func === 'rotateYaw' && act.aircraft_heading != null) return Number(act.aircraft_heading)
          }
        }
      }
      return point.aircraft_heading !== undefined ? Number(point.aircraft_heading) : (point.yaw !== undefined ? Number(point.yaw) : 0)
    }

    const getWaypointGimbalYaw = (point) => {
      if (point.actionGroupList) {
        for (const group of point.actionGroupList) {
          if (!group.actions) continue
          for (const act of group.actions) {
            if ((act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') && act.gimbal_yaw_rotate_angle != null) {
              return Number(act.gimbal_yaw_rotate_angle)
            }
          }
        }
      }
      return point.gimbal_yaw !== undefined ? Number(point.gimbal_yaw) : (point.gimbalYaw !== undefined ? Number(point.gimbalYaw) : 0)
    }

    const getWaypointPitch = (point) => {
      if (point.actionGroupList) {
        for (const group of point.actionGroupList) {
          if (!group.actions) continue
          for (const act of group.actions) {
            if ((act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') && act.gimbal_pitch_rotate_angle != null) {
              return Number(act.gimbal_pitch_rotate_angle)
            }
          }
        }
      }
      return point.gimbal_pitch !== undefined ? Number(point.gimbal_pitch) : (point.gimbalPitch !== undefined ? Number(point.gimbalPitch) : -90)
    }

    const updateWaypointYaw = (index, val) => {
      const pt = pointEntities.value[index]
      if (!pt) return
      let numVal = Number(val)
      const gYaw = getWaypointGimbalYaw(pt)
      const diff = Math.abs(getAngleDiff(gYaw, numVal))
      if (diff > 120) {
        numVal = clampAircraftYaw(numVal, gYaw)
        notifyYawLimitWarning('飞行器偏航角与云台偏航角夹角最大不超过120°，已自动调整')
      }
      pt.yaw = numVal
      pt.aircraft_heading = numVal
      if (!pt.actionGroupList) pt.actionGroupList = []
      let found = false
      for (const group of pt.actionGroupList) {
        if (!group.actions) continue
        for (const act of group.actions) {
          if (act.action_func === 'rotateYaw') {
            act.aircraft_heading = numVal
            found = true
          }
        }
      }
      if (!found) {
        if (pt.actionGroupList.length === 0) {
          addActionGroup(pt, index)
        }
        pt.actionGroupList[0].actions.push({
          action_index: pt.actionGroupList[0].actions.length,
          action_func: 'rotateYaw',
          aircraft_heading: numVal,
          aircraft_path_mode: 'clockwise'
        })
      }
      pt.updateKey = Date.now()
      syncFrustumEntities()
    }

    const updateWaypointGimbalYaw = (index, val) => {
      const pt = pointEntities.value[index]
      if (!pt) return
      let numVal = Number(val)
      const aYaw = getWaypointYaw(pt)
      const diff = Math.abs(getAngleDiff(numVal, aYaw))
      if (diff > 120) {
        numVal = clampGimbalYaw(numVal, aYaw)
        notifyYawLimitWarning('云台偏航角与飞行器偏航角夹角最大不超过120°，已自动调整')
      }
      pt.gimbalYaw = numVal
      pt.gimbal_yaw = numVal
      if (!pt.actionGroupList) pt.actionGroupList = []
      let found = false
      for (const group of pt.actionGroupList) {
        if (!group.actions) continue
        for (const act of group.actions) {
          if (act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') {
            act.gimbal_yaw_rotate_angle = numVal
            found = true
          }
        }
      }
      if (!found) {
        if (pt.actionGroupList.length === 0) {
          addActionGroup(pt, index)
        }
        pt.actionGroupList[0].actions.push({
          action_index: pt.actionGroupList[0].actions.length,
          action_func: 'gimbalRotate',
          gimbal_pitch_rotate_angle: pt.gimbalPitch !== undefined ? pt.gimbalPitch : -90,
          gimbal_yaw_rotate_angle: numVal,
          payload_position_index: 0,
          use_global_payload_lens_index: 1
        })
      }
      pt.updateKey = Date.now()
      syncFrustumEntities()
    }

    const updateWaypointPitch = (index, val) => {
      const pt = pointEntities.value[index]
      if (!pt) return
      const numVal = Number(val)
      pt.gimbalPitch = numVal
      pt.gimbal_pitch = numVal
      if (!pt.actionGroupList) pt.actionGroupList = []
      let found = false
      for (const group of pt.actionGroupList) {
        if (!group.actions) continue
        for (const act of group.actions) {
          if (act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') {
            act.gimbal_pitch_rotate_angle = numVal
            found = true
          }
        }
      }
      if (!found) {
        if (pt.actionGroupList.length === 0) {
          addActionGroup(pt, index)
        }
        pt.actionGroupList[0].actions.push({
          action_index: pt.actionGroupList[0].actions.length,
          action_func: 'gimbalRotate',
          gimbal_pitch_rotate_angle: numVal,
          gimbal_yaw_rotate_angle: pt.gimbalYaw !== undefined ? pt.gimbalYaw : 0,
          payload_position_index: 0,
          use_global_payload_lens_index: 1
        })
      }
      pt.updateKey = Date.now()
      syncFrustumEntities()
    }

    // 从鹰眼图相机姿态实时更新航点动作参数 (偏航角作用于云台相对正北，俯仰角作用于云台)
    const updateWaypointActionFromCamera = (camera, ptIndex) => {
      const pt = pointEntities.value[ptIndex]
      if (!pt || !map.Cesium) return

      let camHeadingDeg = (map.Cesium.Math.toDegrees(camera.heading) % 360 + 360) % 360
      if (camHeadingDeg > 180) camHeadingDeg -= 360
      camHeadingDeg = Number(camHeadingDeg.toFixed(1))
      const camPitchDeg = Number(Math.max(-90, Math.min(35, map.Cesium.Math.toDegrees(camera.pitch))).toFixed(1))

      // 强制约束：云台偏航与飞行器偏航夹角不超过 120°
      const aYaw = getWaypointYaw(pt)
      const clampedCamHeading = clampGimbalYaw(camHeadingDeg, aYaw)

      // 1. 更新航点属性 (云台偏航 0° 正北)
      pt.gimbalYaw = clampedCamHeading
      pt.gimbal_yaw = clampedCamHeading
      pt.gimbalPitch = camPitchDeg
      pt.gimbal_pitch = camPitchDeg

      if (!pt.actionGroupList) pt.actionGroupList = []
      if (pt.actionGroupList.length === 0) {
        addActionGroup(pt, ptIndex)
      }

      // 2. 更新或追加 gimbalRotate 动作
      let gimbalAct = null
      for (const group of pt.actionGroupList) {
        if (!group.actions) continue
        for (const act of group.actions) {
          if (act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') gimbalAct = act
        }
      }

      if (gimbalAct) {
        gimbalAct.gimbal_yaw_rotate_angle = clampedCamHeading
        gimbalAct.gimbal_pitch_rotate_angle = camPitchDeg
      } else {
        pt.actionGroupList[0].actions.push({
          action_index: pt.actionGroupList[0].actions.length,
          action_func: 'gimbalRotate',
          gimbal_pitch_rotate_angle: camPitchDeg,
          gimbal_yaw_rotate_angle: clampedCamHeading,
          payload_position_index: 0,
          use_global_payload_lens_index: 1
        })
      }

      pt.updateKey = Date.now()
      syncFrustumEntities()
    }

    // 鹰眼图直接微调飞行器偏航
    const onEagleEyeHeadingChange = (val) => {
      let idx = selectedWaypointIndex.value
      if (idx < 0) idx = 0
      updateWaypointYaw(idx, val)
      syncEagleEyeCamera()
    }

    // 鹰眼图直接微调云台偏航 (0° 正北)
    const onEagleEyeGimbalYawChange = (val) => {
      let idx = selectedWaypointIndex.value
      if (idx < 0) idx = 0
      updateWaypointGimbalYaw(idx, val)
      syncEagleEyeCamera()
    }

    // 鹰眼图直接微调云台俯仰
    const onEagleEyeGimbalPitchChange = (val) => {
      let idx = selectedWaypointIndex.value
      if (idx < 0) idx = 0
      updateWaypointPitch(idx, val)
      syncEagleEyeCamera()
    }

    const actionIconMap = {
      takePhoto: '📷', startRecord: '🎬', stopRecord: '⏹', focus: '🎯',
      zoom: '🔍', gimbalRotate: '🔄', rotateYaw: '✈', hover: '⏸',
      customDirName: '📁', gimbalEvenlyRotate: '🔃', orientedShoot: '📸'
    }
    const actionLabelMap = {
      takePhoto: '拍照', startRecord: '录像', stopRecord: '停录', focus: '对焦',
      zoom: '变焦', gimbalRotate: '云台', rotateYaw: '偏航', hover: '悬停',
      customDirName: '建目录', gimbalEvenlyRotate: '匀转云台', orientedShoot: '定向拍照'
    }

    // ----------------------------------------------------------------
    // 航点视锥体可视化
    // ----------------------------------------------------------------
    const showFrustum = ref(true)

    const waypointFrustums = computed(() => {
      if (!showFrustum.value || !map.Cesium || !pointEntities.value.length) return []
      const Cesium = map.Cesium
      const payloadType = props.config?.payload_type
      const fovSpec = CAMERA_FOV_TABLE[payloadType] || DEFAULT_FOV

      let idx = selectedWaypointIndex.value
      if (idx < 0 || idx >= pointEntities.value.length) {
        if (pointEntities.value.length > 0) {
          idx = 0 // 如果没有选中航点但列表不为空，默认回退到第一个航点
        } else {
          return []
        }
      }

      const pt = pointEntities.value[idx]
      if (!pt) return []
      const lon = Number(pt.position[0])
      const lat = Number(pt.position[1])
      const alt = Number(pt.position[2])
      if (![lon, lat, alt].every(Number.isFinite) || alt <= 0) return []

      // --- 角度优先级解析（依据大疆云API角度规范） ---
      let pitch = -90 // 云台俯仰：默认垂直向下
      let aircraftYaw = 0 // 飞行器偏航：默认0° (正北)
      let customGimbalYaw = null // 云台偏航：0° 为正北，独立于机身
      let hasExplicitYaw = false
      let zoom = 1

      // 遍历动作列表提取显式参数（后面的动作会覆盖前面的）
      if (pt.actionGroupList) {
        for (const group of pt.actionGroupList) {
          if (!group.actions) continue
          for (const act of group.actions) {
            if (act.action_func === 'rotateYaw' && act.aircraft_heading != null) {
              aircraftYaw = Number(act.aircraft_heading)
              hasExplicitYaw = true
            }
            if (act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') {
              if (act.gimbal_pitch_rotate_angle != null) pitch = Number(act.gimbal_pitch_rotate_angle)
              if (act.gimbal_yaw_rotate_angle != null) customGimbalYaw = Number(act.gimbal_yaw_rotate_angle)
              if (act.zoom != null) zoom = Math.max(1, Number(act.zoom))
            }
            if (act.action_func === 'zoom' && act.zoom != null) {
              zoom = Math.max(1, Number(act.zoom))
            }
          }
        }
      }

      // 兜底从航点属性读取
      if (!hasExplicitYaw) {
        if (pt.aircraft_heading !== undefined) aircraftYaw = Number(pt.aircraft_heading)
        else if (pt.yaw !== undefined) aircraftYaw = Number(pt.yaw)
      }
      if (customGimbalYaw === null) {
        if (pt.gimbal_yaw !== undefined) customGimbalYaw = Number(pt.gimbal_yaw)
        else if (pt.gimbalYaw !== undefined) customGimbalYaw = Number(pt.gimbalYaw)
      }
      if (pitch === -90 && pt.gimbalPitch !== undefined) pitch = Number(pt.gimbalPitch)
      else if (pitch === -90 && pt.gimbal_pitch !== undefined) pitch = Number(pt.gimbal_pitch)

      // 3. 确定镜头绝对偏航角：优先使用云台绝对偏航角(0°正北，不受飞行器偏航影响)；若无设置则使用机身偏航角
      const finalHeading = customGimbalYaw !== null ? customGimbalYaw : aircraftYaw
      const totalYaw = ((finalHeading % 360) + 360) % 360
      const yaw = totalYaw > 180 ? totalYaw - 360 : totalYaw

      const hFovEff = fovSpec.hFov / zoom
      const vFovEff = fovSpec.vFov / zoom
      const halfH = Cesium.Math.toRadians(hFovEff / 2)
      const halfV = Cesium.Math.toRadians(vFovEff / 2)
      const pitchRad = Cesium.Math.toRadians(pitch)
      const yawRad = Cesium.Math.toRadians(yaw)

      // 动态获取地形高度，避免使用相对起飞点的高度导致视锥穿透地表
      let groundElev = pt.agl > 0 ? alt - pt.agl : 0
      if (map.viewer?.scene?.globe) {
        const carto = Cesium.Cartographic.fromDegrees(lon, lat)
        const h = map.viewer.scene.globe.getHeight(carto)
        if (h !== undefined) {
          groundElev = h
        }
      }
      const trueAgl = alt - groundElev
      if (trueAgl <= 0) return []

      // 使用向量计算相机的局部坐标系方向 (ENU: E=东, N=北, U=上)
      const fE = Math.sin(yawRad) * Math.cos(pitchRad)
      const fN = Math.cos(yawRad) * Math.cos(pitchRad)
      const fU = Math.sin(pitchRad)

      const rE = Math.cos(yawRad)
      const rN = -Math.sin(yawRad)
      const rU = 0

      const uE = -Math.sin(yawRad) * Math.sin(pitchRad)
      const uN = -Math.cos(yawRad) * Math.sin(pitchRad)
      const uU = Math.cos(pitchRad)

      const tanH = Math.tan(halfH)
      const tanV = Math.tan(halfV)

      // 四个角点(坐标系：x向右，y向上)
      const corners = [
        [-tanH, tanV],
        [tanH, tanV],
        [tanH, -tanV],
        [-tanH, -tanV]
      ]

      const mPerDegLat = 111320
      const rawMPerDegLon = 111320 * Math.cos(Cesium.Math.toRadians(lat))
      const mPerDegLon = Math.max(Math.abs(rawMPerDegLon), 1)
      const maxLength = Math.max(trueAgl * 1.5, 60)

      const projectRay = (dE, dN, dU) => {
        const length = Math.sqrt(dE * dE + dN * dN + dU * dU)
        if (!Number.isFinite(length) || length === 0) return null

        dE /= length
        dN /= length
        dU /= length

        const groundDistance = dU < -0.001 ? trueAgl / (-dU) : Number.POSITIVE_INFINITY
        const distance = Math.min(groundDistance, maxLength)
        return {
          point: [
            lon + (dE * distance) / mPerDegLon,
            lat + (dN * distance) / mPerDegLat,
            alt + (dU * distance)
          ],
          hitsGround: Number.isFinite(groundDistance) && groundDistance <= maxLength
        }
      }

      const projectedCorners = corners.map(([x, y]) => projectRay(
        fE + x * rE + y * uE,
        fN + x * rN + y * uN,
        fU + x * rU + y * uU
      ))
      if (projectedCorners.some(result => !result)) return []

      const centerProjection = projectRay(fE, fN, fU)
      if (!centerProjection) return []

      return [{
        id: 'frustum-' + idx,
        origin: [lon, lat, alt],
        groundPoints: projectedCorners.map(result => result.point),
        centerPoint: centerProjection.point,
        centerHitsGround: centerProjection.hitsGround,
        isSelected: true
      }]
    })

    // ----------------------------------------------------------------
    // FPV 模式控制逻辑
    // ----------------------------------------------------------------
    const fpvState = reactive({
      isActive: false,
      waypointIndex: -1,
      startMouseX: 0,
      startMouseY: 0,
      isDragging: false,
      originalPitch: 0,
      originalYaw: 0
    })

    const enterFpvMode = (index) => {
      const pt = pointEntities.value[index]
      if (!pt || !map.viewer || !map.Cesium) return

      // --- FPV 姿态提取（与视锥体保持一致的优先级） ---
      let pitch = -90
      let aircraftYaw = 0
      let customGimbalYaw = null
      let zoom = 1

      // 2. 遍历动作列表提取显式参数
      if (pt.actionGroupList) {
        for (const group of pt.actionGroupList) {
          if (!group.actions) continue
          for (const act of group.actions) {
            if (act.action_func === 'rotateYaw' && act.aircraft_heading != null) aircraftYaw = Number(act.aircraft_heading)
            if (act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') {
              if (act.gimbal_pitch_rotate_angle != null) pitch = Number(act.gimbal_pitch_rotate_angle)
              if (act.gimbal_yaw_rotate_angle != null) customGimbalYaw = Number(act.gimbal_yaw_rotate_angle)
              if (act.zoom != null) zoom = Math.max(1, Number(act.zoom))
            }
          }
        }
      }

      if (pt.aircraft_heading !== undefined) aircraftYaw = Number(pt.aircraft_heading)
      else if (pt.yaw !== undefined) aircraftYaw = Number(pt.yaw)
      if (customGimbalYaw === null) {
        if (pt.gimbal_yaw !== undefined) customGimbalYaw = Number(pt.gimbal_yaw)
        else if (pt.gimbalYaw !== undefined) customGimbalYaw = Number(pt.gimbalYaw)
      }
      if (pitch === -90 && pt.gimbalPitch !== undefined) pitch = Number(pt.gimbalPitch)
      else if (pitch === -90 && pt.gimbal_pitch !== undefined) pitch = Number(pt.gimbal_pitch)

      const finalHeading = customGimbalYaw !== null ? customGimbalYaw : aircraftYaw
      const totalFpvYaw = ((finalHeading % 360) + 360) % 360
      const yaw = totalFpvYaw > 180 ? totalFpvYaw - 360 : totalFpvYaw

      fpvState.originalPitch = pitch
      fpvState.originalYaw = yaw

      const [lng, lat, height] = pt.position
      map.viewer.camera.flyTo({
        destination: map.Cesium.Cartesian3.fromDegrees(lng, lat, height),
        orientation: {
          heading: map.Cesium.Math.toRadians(yaw),
          pitch: map.Cesium.Math.toRadians(pitch),
          roll: 0.0
        },
        duration: 1.5,
        complete: () => {
          fpvState.isActive = true
          fpvState.waypointIndex = index
          map.viewer.scene.screenSpaceCameraController.enableInputs = false
          selectedWaypointIndex.value = index // 同步左侧菜单选中
        }
      })
    }

    const syncFpvToWaypointAction = () => {
      if (!fpvState.isActive || fpvState.waypointIndex < 0) return
      updateWaypointActionFromCamera(map.viewer.camera, fpvState.waypointIndex)
    }

    const exitFpvMode = (save) => {
      if (!save) {
        // Rollback
        const pt = pointEntities.value[fpvState.waypointIndex]
        if (pt && pt.actionGroupList && pt.actionGroupList[0]) {
           const act = pt.actionGroupList[0].actions.find(a => ['orientedShoot', 'gimbalRotate'].includes(a.action_func))
           if (act) {
               act.gimbal_pitch_rotate_angle = fpvState.originalPitch
               act.gimbal_yaw_rotate_angle = fpvState.originalYaw
           }
        }
        message.info('已取消航点姿态修改')
      } else {
        message.success(`已保存航点姿态: 偏航 ${fpvState.originalYaw.toFixed(1)}°, 俯仰 ${fpvState.originalPitch.toFixed(1)}°`)
      }
      
      fpvState.isActive = false
      map.viewer.scene.screenSpaceCameraController.enableInputs = true
      
      const pt = pointEntities.value[fpvState.waypointIndex]
      if (pt) {
        const [lng, lat, height] = pt.position
        map.viewer.camera.flyTo({
          destination: map.Cesium.Cartesian3.fromDegrees(lng, lat, height + 300),
          orientation: {
            heading: 0,
            pitch: map.Cesium.Math.toRadians(-90),
            roll: 0.0
          },
          duration: 1
        })
      }
      fpvState.waypointIndex = -1
    }

    // ----------------------------------------------------------------
    // 初始化与事件绑定
    // ----------------------------------------------------------------

    const onViewerReady = ({ Cesium, viewer }) => {
      map.viewer = markRaw(viewer);
      map.Cesium = markRaw(Cesium);
      map.handler = new Cesium.ScreenSpaceEventHandler(viewer.canvas);

      viewer.scene.globe.enableLighting = true;
      // 开启地形深度检测，防止点透视
      viewer.scene.globe.depthTestAgainstTerrain = true;

      // 优化网络并发与数据加载解压调度，避免在无独显/弱 CPU 设备上造成主线程和内存总线卡死
      if (Cesium.RequestScheduler) {
        Cesium.RequestScheduler.requestsByServer = 4;
        Cesium.RequestScheduler.maximumRequests = 8;
        Cesium.RequestScheduler.maximumRequestsPerServer = 4;
      }
      if (viewer.scene && viewer.scene.globe) {
        viewer.scene.globe.loadingQueueThreshold = 2;
        viewer.scene.globe.tileCacheSize = 100;
      }

      // 初始化材质与动态航线
      initMaterials();
      initDynamicRoute(Cesium);

      // 同步渲染所有已有实体 (航点、起飞点、机场、辅助线)
      syncWaypointEntities();
      syncTakeoffPointEntity();
      syncDockEntities();
      syncAuxiliaryEntities();
      syncFrustumEntities();

      // 初始化拖拽处理
      setupWaypointDragHandlers(viewer, Cesium);

      const geocoderService = new TiandituGeocoderService({
        key: "4db514621ce57a692181a8bc4ddb9d72",
        specify: "156500000", // 优先限定搜重庆（瀨溪河流域）以提升检索准确度
        getLocalPoints: () => {
          const list = [];
          // 1. 收集正在编辑的航点
          if (Array.isArray(pointEntities.value)) {
            pointEntities.value.forEach((p, i) => {
              if (p && Array.isArray(p.position) && p.position.length >= 2) {
                list.push({
                  name: `航点 ${i + 1}`,
                  longitude: Number(p.position[0]),
                  latitude: Number(p.position[1]),
                  height: p.position[2] !== undefined ? Number(p.position[2]) : 150,
                  address: '当前编辑航线航点'
                });
              }
            });
          }
          // 2. 收集起飞点
          if (takeoffPoint.value && takeoffPoint.value.longitude && takeoffPoint.value.latitude) {
            list.push({
              name: '起飞点',
              longitude: Number(takeoffPoint.value.longitude),
              latitude: Number(takeoffPoint.value.latitude),
              height: takeoffPoint.value.height !== undefined ? Number(takeoffPoint.value.height) + 50 : 150,
              address: '当前编辑航线起飞参考点'
            });
          }
          return list;
        }
      })

      const originGeocode = geocoderService.geocode.bind(geocoderService);

      // 3. 替换 geocode 方法，使用自定义防抖以返回 Promise，修复 lodash debounce 返回 undefined 导致无法检索的问题
      let debounceTimeout = null;
      geocoderService.geocode = (input, options) => {
        if (debounceTimeout) {
          clearTimeout(debounceTimeout);
        }
        return new Promise((resolve, reject) => {
          debounceTimeout = setTimeout(() => {
            originGeocode(input, options)
              .then(resolve)
              .catch(reject);
          }, 500); // 500ms 检索更顺畅
        });
      };
      viewer.geocoder.viewModel._geocoderServices = [geocoderService];
      if (viewer.geocoder && viewer.geocoder.container) {
        const setChinesePlaceholder = () => {
          const inputEl = viewer.geocoder.container.querySelector('.cesium-geocoder-input') || viewer.geocoder.container.querySelector('input');
          if (inputEl) {
            inputEl.placeholder = '搜索地址或地名...';
          }
        };
        setChinesePlaceholder();
        setTimeout(setChinesePlaceholder, 200);
      }

      // 初始化材质
      initMaterials();

      // 标记地图已初始化完成
      isMapReady.value = true;

      console.log('地图初始化完成，isMapReady:', isMapReady.value);

      if (takeoffPoint.value) {
        map.viewer.camera.flyTo({
          destination: map.Cesium.Cartesian3.fromDegrees(takeoffPoint.value.longitude, takeoffPoint.value.latitude, takeoffPoint.value.height + 10000),
          orientation: {
            heading: map.Cesium.Math.toRadians(0),
            pitch: map.Cesium.Math.toRadians(-90),
            roll: 0
          }
        })
      }

      emit("viewer-ready", viewer);
    }

    // 监听数据变化，自动同步 Cesium 实体
    watch(() => pointEntities.value.length, () => {
      syncWaypointEntities();
    });

    watch(takeoffPoint, () => {
      syncTakeoffPointEntity();
    }, { deep: true });

    watch(dockMap, () => {
      syncDockEntities();
    }, { deep: true });

    watch([showFrustum, waypointFrustums], () => {
      syncFrustumEntities();
    }, { deep: true });

    const onRightClick = (e) => {
      if (isPreviewMode.value) return;
      if (!map.viewer || !map.Cesium) return;
      const ray = map.viewer.scene.camera.getPickRay(e.position);
      let cartesian = map.viewer.scene.globe.pick(ray, map.viewer.scene);
      if (!cartesian) {
        cartesian = map.viewer.scene.camera.pickEllipsoid(e.position, map.viewer.scene.globe.ellipsoid);
      }

      if (cartesian) {
        const carto = map.Cesium.Cartographic.fromCartesian(cartesian);
        contextMenu.x = e.position.x + 10;
        contextMenu.y = e.position.y + 10;
        contextMenu.position = {
          longitude: map.Cesium.Math.toDegrees(carto.longitude).toFixed(7),
          latitude: map.Cesium.Math.toDegrees(carto.latitude).toFixed(7),
          height: carto.height ? carto.height.toFixed(1) : '0.0'
        };
        contextMenu.visible = true;
      }
    }

    const setTakeoffPointFromMenu = () => {
      const { longitude, latitude, height } = contextMenu.position;
      takeoffPoint.value = {
        longitude: parseFloat(longitude),
        latitude: parseFloat(latitude),
        height: parseFloat(height || 0)
      };
      emit('update-takeoff-point', {
        longitude: parseFloat(longitude),
        latitude: parseFloat(latitude),
        height: parseFloat(height || 0)
      });
      contextMenu.visible = false;
      message.success('参考起飞点设置成功');
    }

    const onLeftClick = (e) => {
      contextMenu.visible = false;
      waypointMenu.visible = false;
      if (e && e.position && map.viewer && map.Cesium) {
        const picked = map.viewer.scene.pick(e.position);
        if (map.Cesium.defined(picked) && picked.id && typeof picked.id.id === 'string') {
          const match = picked.id.id.match(/^(?:waypoint|vline)-(\d+)$/);
          if (match) {
            const index = parseInt(match[1]);
            if (index >= 0 && index < pointEntities.value.length) {
              selectedWaypointIndex.value = index;
            }
          }
        }
      }
    }

    // ----------------------------------------------------------------
    // 统计与导出
    // ----------------------------------------------------------------

    const totalDistance = computed(() => {
      let dist = 0;
      if (pointEntities.value.length < 2 || !map.Cesium) return 0;
      for (let i = 0; i < pointEntities.value.length - 1; i++) {
        const p1 = map.Cesium.Cartesian3.fromDegrees(...pointEntities.value[i].position);
        const p2 = map.Cesium.Cartesian3.fromDegrees(...pointEntities.value[i + 1].position);
        dist += map.Cesium.Cartesian3.distance(p1, p2);
      }
      return dist;
    });

    const totalDuration = computed(() => (totalDistance.value / 10) + (pointEntities.value.length * 20));

    const generateWaylineJson = async () => {
      // 校验所有航点：飞行器偏航角与云台偏航角夹角最大不超过120°
      for (let i = 0; i < pointEntities.value.length; i++) {
        const pt = pointEntities.value[i]
        const aYaw = getWaypointYaw(pt)
        const gYaw = getWaypointGimbalYaw(pt)
        const diff = Math.abs(getAngleDiff(gYaw, aYaw))
        if (diff > 120) {
          message.error(`航点 ${i + 1} 的飞行器偏航角(${aYaw.toFixed(1)}°)与云台偏航角(${gYaw.toFixed(1)}°)夹角超过120°(当前为${diff.toFixed(1)}°)，请调整后再保存！`)
          selectedWaypointIndex.value = i
          return
        }
      }

      let globalGroupId = 0

      // 预构建等间隔拍照动作组（按 startIndex 分组，挂在起始航点上）
      const intervalGroupsByStart = {}
      for (const ig of intervalPhotoGroups.value) {
        const key = ig.startIndex
        if (!intervalGroupsByStart[key]) intervalGroupsByStart[key] = []
        const useGlobal = ig.use_global_image_format !== undefined ? ig.use_global_image_format : 1
        const photoAction = {
          action_index: 0,
          action_func: 'takePhoto',
          use_global_image_format: useGlobal,
          payload_position_index: ig.payload_position_index !== undefined ? ig.payload_position_index : 0
        }
        if (useGlobal === 0 && (ig.image_format || ig.payload_lens_index)) {
          photoAction.image_format = ig.image_format || ig.payload_lens_index
        }
        if (ig.file_suffix) {
          photoAction.file_suffix = ig.file_suffix
        }
        intervalGroupsByStart[key].push({
          action_group_id: 0,
          action_group_start_index: ig.startIndex,
          action_group_end_index: ig.endIndex,
          action_trigger_type: ig.triggerType,
          action_trigger_param: ig.triggerParam,
          actions: [photoAction]
        })
      }

      const route_point_list = pointEntities.value.map((point, index) => {
        let action_group_list = []

        // 航点自身的到达航点动作组（跳过没有动作的空动作组）
        if (point.actionGroupList && point.actionGroupList.length > 0) {
          for (const group of point.actionGroupList) {
            // 当 actions 为空数组时，跳过该动作组，不生成 action_group_list 条目
            if (!group.actions || group.actions.length === 0) continue
            const actions = group.actions.map((action, aIdx) => ({
              ...action,
              action_index: aIdx
            }))
            action_group_list.push({
              ...group,
              action_group_id: globalGroupId++,
              action_group_start_index: index,
              action_group_end_index: index,
              actions
            })
          }
        }

        // 挂在该航点上的等间隔拍照动作组
        if (intervalGroupsByStart[index]) {
          for (const ig of intervalGroupsByStart[index]) {
            action_group_list.push({
              ...ig,
              action_group_id: globalGroupId++,
            })
          }
        }

        return {
          route_point_index: index,
          longitude: point.position[0],
          latitude: point.position[1],
          height: Number(point.position[2]) - Number(takeoffPoint.value.height),
          action_group_list: action_group_list.length > 0 ? action_group_list : undefined
        }
      });

      const json = { ...props.config, ...waylineGlobalParams, route_point_list };

      // 获取 workspaceId 和航线名称
      const workspaceId = localStorage.getItem(ELocalStorageKey.WorkspaceId);
      if (!workspaceId) {
        message.error('未找到工作空间ID，请重新登录');
        return;
      }

      // 优先使用传入的 waylineName prop，其次使用配置中的名称，最后生成默认名称
      const waylineName = props.waylineName || props.config?.name || `航线_${new Date().toISOString().slice(0, 19).replace(/[:-]/g, '')}`;

      try {
        let res;
        if (props.isEditMode && props.editWaylineId) {
          // 编辑模式：调用修改接口
          res = await modifyWaylineEdit(workspaceId, props.editWaylineId, json, props.dock_sn);
          if (res.code === 0) {
            message.success('航线修改保存成功');
            // 清理编辑缓存
            if (props.editWaylineId) {
              sessionStorage.removeItem(`editWaylineData_${props.editWaylineId}`);
              sessionStorage.removeItem(`wayline_draft_${props.editWaylineId}`);
            }
            sessionStorage.removeItem('editWaylineData');
            localStorage.removeItem('editWaylineData');
            // 返回航线库页面
            router.back();
          } else {
            message.error('航线修改失败: ' + (res.message || '未知错误'));
          }
        } else {
          // 新建模式：调用新增接口
          res = await saveWaylineEdit(workspaceId, waylineName, json);
          if (res.code === 0) {
            message.success('航线保存成功');
            sessionStorage.removeItem('editWaylineData');
            localStorage.removeItem('editWaylineData');
          } else {
            message.error('航线保存失败: ' + (res.message || '未知错误'));
          }
        }
      } catch (error) {
        console.error('保存航线失败:', error);
        message.error('航线保存失败，请检查网络连接');
      }
    }

    console.log('测试', dockMap.value)

    // ----------------------------------------------------------------
    // 其他 UI 逻辑
    // ----------------------------------------------------------------
    const triColorGradientMaterial = shallowRef(null)

    const initMaterials = () => {
      if (!map.Cesium) return

      const canvas = document.createElement('canvas')
      canvas.width = 512
      canvas.height = 512
      const ctx = canvas.getContext('2d')

      const centerX = 256
      const centerY = 256
      const maxRadius = 256

      // 创建径向渐变
      const gradient = ctx.createRadialGradient(centerX, centerY, 0, centerX, centerY, maxRadius)

      // 0-30% 区域（0-3km）：绿色
      gradient.addColorStop(0, 'rgba(0, 255, 0, 0.2)')
      gradient.addColorStop(0.3, 'rgba(0, 255, 0, 0.2)')

      // 30-50% 区域（3-5km）：黄色
      gradient.addColorStop(0.3, 'rgba(255, 255, 0, 0.2)')
      gradient.addColorStop(0.5, 'rgba(255, 255, 0, 0.2)')

      // 50-100% 区域（5-10km）：红色
      gradient.addColorStop(0.5, 'rgba(255, 0, 0, 0.2)')
      gradient.addColorStop(1, 'rgba(255, 0, 0, 0.2)')

      ctx.fillStyle = gradient
      ctx.fillRect(0, 0, 512, 512)

      triColorGradientMaterial.value = new map.Cesium.ImageMaterialProperty({
        image: canvas,
        transparent: true
      })
    }

    // ----------------------------------------------------------------
    // 鹰眼图（无人机第一视角 PiP）
    // ----------------------------------------------------------------
    const showEagleEye = ref(true)
    const eagleEyeViewerRef = ref(null)

    // 计算选中航点的相机姿态（与视锥体使用完全一致的优先级逻辑）
    const eagleEyeCamera = computed(() => {
      let idx = selectedWaypointIndex.value
      if (idx < 0 || idx >= pointEntities.value.length) {
        if (pointEntities.value.length > 0) {
          idx = 0 // 如果没有选中航点但列表不为空，默认回退到第一个航点
        } else {
          return null
        }
      }

      const pt = pointEntities.value[idx]
      if (!pt || !pt.position) return null
      const [lon, lat, alt] = pt.position
      if (lon === undefined || lat === undefined || alt === undefined) return null

      // --- 角度优先级解析（与 waypointFrustums 一致）---
      let pitch = -90
      let aircraftYaw = 0
      let customGimbalYaw = null
      let hasExplicitYaw = false
      let zoom = 1

      if (pt.actionGroupList) {
        for (const group of pt.actionGroupList) {
          if (!group.actions) continue
          for (const act of group.actions) {
            if (act.action_func === 'rotateYaw' && act.aircraft_heading != null) {
              aircraftYaw = Number(act.aircraft_heading)
              hasExplicitYaw = true
            }
            if (act.action_func === 'gimbalRotate' || act.action_func === 'orientedShoot') {
              if (act.gimbal_pitch_rotate_angle != null) pitch = Number(act.gimbal_pitch_rotate_angle)
              if (act.gimbal_yaw_rotate_angle != null) customGimbalYaw = Number(act.gimbal_yaw_rotate_angle)
              if (act.zoom != null) zoom = Math.max(1, Number(act.zoom))
            }
            if (act.action_func === 'zoom' && act.zoom != null) {
              zoom = Math.max(1, Number(act.zoom))
            }
          }
        }
      }

      // 兜底
      if (!hasExplicitYaw) {
        if (pt.aircraft_heading !== undefined) aircraftYaw = Number(pt.aircraft_heading)
        else if (pt.yaw !== undefined) aircraftYaw = Number(pt.yaw)
      }
      if (customGimbalYaw === null) {
        if (pt.gimbal_yaw !== undefined) customGimbalYaw = Number(pt.gimbal_yaw)
        else if (pt.gimbalYaw !== undefined) customGimbalYaw = Number(pt.gimbalYaw)
      }
      if (pitch === -90 && pt.gimbalPitch !== undefined) pitch = Number(pt.gimbalPitch)
      else if (pitch === -90 && pt.gimbal_pitch !== undefined) pitch = Number(pt.gimbal_pitch)

      // 3. 确定镜头绝对偏航角：优先使用云台绝对偏航角(0°为正北，不受机头影响)；若无设置则使用机身偏航角
      const finalHeading = customGimbalYaw !== null ? customGimbalYaw : aircraftYaw
      const totalEagleYaw = ((finalHeading % 360) + 360) % 360
      const yaw = totalEagleYaw > 180 ? totalEagleYaw - 360 : totalEagleYaw

      return {
        lon,
        lat,
        alt,
        yaw,
        pitch,
        zoom,
        aircraftYaw,
        gimbalYaw: customGimbalYaw !== null ? customGimbalYaw : 0
      }
    })

    // 原生 Cesium 鹰眼图 Viewer 实例
    const eagleEyeViewer = shallowRef(null)
    
    let eagleEyeHandler = null
    const eagleEyeDragState = {
      isDragging: false,
      startX: 0,
      startY: 0
    }

    // 初始化鹰眼图 Cesium 地图
    const initEagleEyeViewer = () => {
            console.log('initEagleEyeViewer1')

      if (!eagleEyeContainerRef.value || eagleEyeViewer.value || !map.Cesium) return
      
      console.log('initEagleEyeViewer2')
      try {
        const tdtToken = CURRENT_CONFIG.tiandituToken || '4db514621ce57a692181a8bc4ddb9d72'
        const imgProvider = new map.Cesium.UrlTemplateImageryProvider({
          url: `https://t{s}.tianditu.gov.cn/DataServer?T=img_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
          subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
          maximumLevel: 18
        })
        const ciaProvider = new map.Cesium.UrlTemplateImageryProvider({
          url: `https://t{s}.tianditu.gov.cn/DataServer?T=cia_w&X={x}&Y={y}&L={z}&tk=${tdtToken}`,
          subdomains: ['0', '1', '2', '3', '4', '5', '6', '7'],
          maximumLevel: 18
        })

        const viewer = new map.Cesium.Viewer(eagleEyeContainerRef.value, {
          animation: false,
          timeline: false,
          fullscreenButton: false,
          infoBox: false,
          selectionIndicator: false,
          skyAtmosphere: false,
          skyBox: false,
          sceneModePicker: false,
          showCredit: false,
          geocoder: false,
          baseLayerPicker: false,
          baseLayer: new map.Cesium.ImageryLayer(imgProvider)
        })

        viewer.imageryLayers.addImageryProvider(ciaProvider)

        if (viewer._cesiumWidget?._creditContainer) {
          viewer._cesiumWidget._creditContainer.style.display = 'none'
        }
        viewer.scene.globe.enableLighting = false
        viewer.scene.screenSpaceCameraController.enableInputs = false
        viewer.scene.globe.depthTestAgainstTerrain = true

        // 加载 3D 真实地形 (参考 DjiCesium 方案)
        getGlobalTerrainProvider(map.Cesium).then(terrain => {
          if (terrain && viewer && !viewer.isDestroyed()) {
            viewer.terrainProvider = terrain
            syncEagleEyeCamera()
          }
        })

        const rawViewer = markRaw(viewer)
        eagleEyeViewer.value = rawViewer
        eagleEyeViewerRef.value = rawViewer

        // 绑定拖拽事件来调整选中航点的姿态
        eagleEyeHandler = new map.Cesium.ScreenSpaceEventHandler(viewer.canvas)
        eagleEyeHandler.setInputAction((click) => {
          eagleEyeDragState.isDragging = true
          eagleEyeDragState.startX = click.position.x
          eagleEyeDragState.startY = click.position.y
        }, map.Cesium.ScreenSpaceEventType.LEFT_DOWN)
        
        eagleEyeHandler.setInputAction((movement) => {
          if (!eagleEyeDragState.isDragging) return
          const dx = movement.endPosition.x - eagleEyeDragState.startX
          const dy = movement.endPosition.y - eagleEyeDragState.startY
          eagleEyeDragState.startX = movement.endPosition.x
          eagleEyeDragState.startY = movement.endPosition.y

          const camera = viewer.camera
          const lookFactor = 0.003
          // 水平拖拽改变 Heading，垂直拖拽改变 Pitch (向上拖抬头，向下拖低头)
          let newHeading = camera.heading + dx * lookFactor
          let newPitch = camera.pitch - dy * lookFactor

          // 限制俯仰在 -90°(俯视) ~ +35°(仰视) 之间
          const minPitch = map.Cesium.Math.toRadians(-90)
          const maxPitch = map.Cesium.Math.toRadians(35)
          newPitch = Math.max(minPitch, Math.min(maxPitch, newPitch))

          // 强制锁定 roll 为 0.0，确保地平线严格水平，彻底杜绝倾斜
          camera.setView({
            orientation: {
              heading: newHeading,
              pitch: newPitch,
              roll: 0.0
            }
          })

          let idx = selectedWaypointIndex.value
          if (idx < 0) idx = 0
          if (pointEntities.value.length > 0) {
            updateWaypointActionFromCamera(camera, idx)
          }
        }, map.Cesium.ScreenSpaceEventType.MOUSE_MOVE)

        eagleEyeHandler.setInputAction(() => {
          eagleEyeDragState.isDragging = false
          syncEagleEyeCamera()
        }, map.Cesium.ScreenSpaceEventType.LEFT_UP)

        onEagleEyeReady({ viewer: rawViewer })
        syncEagleEyeCamera()
      } catch (e) {
        console.error('初始化鹰眼图失败:', e)
      }
    }

    // 销毁鹰眼图 Viewer
    const destroyEagleEyeViewer = () => {
      if (eagleEyeViewer.value) {
        if (eagleEyeHandler) {
          eagleEyeHandler.destroy()
          eagleEyeHandler = null
        }
        onEagleEyeDestroyed()
        try {
          if (!eagleEyeViewer.value.isDestroyed()) {
            eagleEyeViewer.value.destroy()
          }
        } catch (e) {
          console.error('销毁鹰眼图 Viewer 异常:', e)
        }
        eagleEyeViewer.value = null
        eagleEyeViewerRef.value = null
      }
    }

    // 同步鹰眼图相机到选中航点视角
    const syncEagleEyeCamera = () => {
      if (eagleEyeDragState.isDragging) return
      const viewer = eagleEyeViewer.value || (eagleEyeViewerRef.value?.getCesiumObject ? eagleEyeViewerRef.value.getCesiumObject() : null)
      const cam = eagleEyeCamera.value
      if (!viewer || !cam || !map.Cesium || (viewer.isDestroyed && viewer.isDestroyed())) return

      // 确保鹰眼图加载并保持与主地图相同的 3D 地形
      getGlobalTerrainProvider(map.Cesium).then(terrain => {
        if (terrain && viewer && !viewer.isDestroyed() && viewer.terrainProvider !== terrain) {
          viewer.terrainProvider = terrain
        }
      })

      const payloadType = props.config?.payload_type
      const fovSpec = CAMERA_FOV_TABLE[payloadType] || DEFAULT_FOV
      const zoom = cam.zoom || 1
      const vFovEff = fovSpec.vFov / zoom

      // 同步相机 FOV 视场角与容器高宽比，保证第一视角与实际相机镜头规格及 3D 视锥体完全一致
      if (viewer.camera.frustum instanceof map.Cesium.PerspectiveFrustum) {
        viewer.camera.frustum.fov = map.Cesium.Math.toRadians(vFovEff)
        if (eagleEyeContainerRef.value) {
          const w = eagleEyeContainerRef.value.clientWidth || 320
          const h = eagleEyeContainerRef.value.clientHeight || 200
          viewer.camera.frustum.aspectRatio = w / h
        }
      }

      viewer.camera.setView({
        destination: map.Cesium.Cartesian3.fromDegrees(cam.lon, cam.lat, cam.alt),
        orientation: {
          heading: map.Cesium.Math.toRadians(cam.yaw),
          pitch: map.Cesium.Math.toRadians(cam.pitch),
          roll: 0.0
        }
      })
    }

    // 监听选中航点及其姿态参数变化，自动同步鹰眼图及初始化
    watch([showEagleEye, () => eagleEyeCamera.value, isMapReady], ([show, cam, ready]) => {
      console.log('watch', show, cam, ready)
      if (show && cam && ready) {
        nextTick(() => {
            console.log('nextTick', eagleEyeContainerRef.value, eagleEyeViewer.value)
          if (eagleEyeContainerRef.value && !eagleEyeViewer.value) {
            console.log('initEagleEyeViewer')
            initEagleEyeViewer()
          } else if (eagleEyeViewer.value && !eagleEyeDragState.isDragging) {
            console.log('syncEagleEyeCamera')
            syncEagleEyeCamera()
          }
        })
      } else if (!show) {
        console.log('destroyEagleEyeViewer')
        destroyEagleEyeViewer()
      }
    }, { immediate: true, deep: true })

    // 3D 建模图层控制状态与方法
    const activeTilesetConfigs = ref([])
    const viewerTilesetPrimitives = new Map() // Map of Viewer -> Map of url -> Cesium3DTileset primitive

    const applyTilesetToViewer = (viewer, url, checked) => {
      if (!viewer || !map.Cesium || (viewer.isDestroyed && viewer.isDestroyed())) return

      if (!viewerTilesetPrimitives.has(viewer)) {
        viewerTilesetPrimitives.set(viewer, new Map())
      }
      const primitivesMap = viewerTilesetPrimitives.get(viewer)

      if (checked) {
        if (primitivesMap.has(url)) return
        try {
          // 加载 3D 建模时关闭地形深度检测，防止模型被地形瓦片遮挡
          viewer.scene.globe.depthTestAgainstTerrain = false

          map.Cesium.Cesium3DTileset.fromUrl(url, {
            maximumScreenSpaceError: 0.5,
          }).then(tileset => {
            if (!primitivesMap || (viewer.isDestroyed && viewer.isDestroyed())) return
            // Check if the user hasn't unchecked it while it was loading
            if (primitivesMap.has(url)) return
            const addedTileset = viewer.scene.primitives.add(tileset)
            primitivesMap.set(url, markRaw(addedTileset))
          }).catch(e => {
            console.error(`加载3D建模 primitives 失败: ${url}`, e)
          })
        } catch (e) {
          console.error(`加载3D建模外层异常: ${url}`, e)
        }
      } else {
        const tileset = primitivesMap.get(url)
        if (tileset) {
          try {
            if (!viewer.isDestroyed?.()) {
              viewer.scene.primitives.remove(tileset)
            }
          } catch (e) {
            console.error(`卸载3D建模 primitives 失败: ${url}`, e)
          }
          primitivesMap.delete(url)
        }

        // 若该 Viewer 的所有 3D 建模已卸载，恢复地形深度检测
        if (primitivesMap.size === 0 && !viewer.isDestroyed?.()) {
          viewer.scene.globe.depthTestAgainstTerrain = true
        }
      }
    }

    const handleTilesetChange = ({ checked, name, url }) => {
      // 1. 更新启用的 tileset 列表状态
      if (checked) {
        if (!activeTilesetConfigs.value.some(cfg => cfg.url === url)) {
          activeTilesetConfigs.value.push({ url, name })
        }
      } else {
        activeTilesetConfigs.value = activeTilesetConfigs.value.filter(cfg => cfg.url !== url)
      }

      // 2. 应用到主地图 viewer
      if (map.viewer) {
        applyTilesetToViewer(map.viewer, url, checked)
      }

      // 3. 应用到第一视角（鹰眼）viewer
      const fpvViewer = eagleEyeViewer.value || (eagleEyeViewerRef.value?.getCesiumObject ? eagleEyeViewerRef.value.getCesiumObject() : eagleEyeViewerRef.value)
      if (fpvViewer && !fpvViewer.isDestroyed?.()) {
        applyTilesetToViewer(fpvViewer, url, checked)
      }
    }

    const onEagleEyeReady = ({ viewer: v }) => {
      // 关闭鹰眼图的所有 UI 控件以节省空间
      if (v && !v.isDestroyed?.()) {
        if (v._cesiumWidget?._creditContainer) {
          v._cesiumWidget._creditContainer.style.display = 'none'
        }
        v.scene.globe.enableLighting = false
        v.scene.screenSpaceCameraController.enableInputs = false

        // 加载当前已经勾选的 3D 建模到鹰眼（第一视角）viewer
        activeTilesetConfigs.value.forEach(cfg => {
          applyTilesetToViewer(v, cfg.url, true)
        })

        nextTick(() => syncEagleEyeCamera())
      }
    }

    const onEagleEyeDestroyed = () => {
      // 当鹰眼第一视角关闭时，卸载它所关联的所有 3D 建模 primitives，避免内存泄漏
      const fpvViewer = eagleEyeViewer.value || eagleEyeViewerRef.value
      if (fpvViewer && viewerTilesetPrimitives.has(fpvViewer)) {
        const primitivesMap = viewerTilesetPrimitives.get(fpvViewer)
        primitivesMap.clear()
        viewerTilesetPrimitives.delete(fpvViewer)
      }
    }

    const onDestroyed = () => {
      map.isDestroyed = true
      // 销毁所有 primitives，避免内存泄漏
      viewerTilesetPrimitives.forEach((primitivesMap, viewer) => {
        try {
          if (!viewer.isDestroyed()) {
            primitivesMap.forEach((tileset) => {
              try {
                viewer.scene.primitives.remove(tileset)
              } catch (e) {}
            })
          }
        } catch (e) {}
      })
      viewerTilesetPrimitives.clear()
    }

    const switchToTopDownView = () => {
      if (!map.viewer || !map.Cesium) return;
      const camera = map.viewer.camera;
      const ray = new map.Cesium.Ray(camera.position, camera.direction);
      let target = map.viewer.scene.globe.pick(ray, map.viewer.scene);
      if (!target) {
        target = camera.pickEllipsoid(new map.Cesium.Cartesian2(map.viewer.canvas.width / 2, map.viewer.canvas.height / 2));
      }
      if (!target) return;

      const distance = map.Cesium.Cartesian3.distance(camera.position, target);
      const carto = map.Cesium.Cartographic.fromCartesian(target);
      const dest = map.Cesium.Cartesian3.fromDegrees(
        map.Cesium.Math.toDegrees(carto.longitude),
        map.Cesium.Math.toDegrees(carto.latitude),
        carto.height + Math.max(100, distance) // 保持高度，最小为100m
      );

      map.viewer.camera.flyTo({
        destination: dest,
        orientation: {
          heading: camera.heading, // 保持偏航，防止突然转动
          pitch: map.Cesium.Math.toRadians(-90), // 俯视角 -90 度
          roll: 0.0
        },
        duration: 1.2
      });
    }

    const accessToken = ref('eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJqdGkiOiIzMTdmZTIxOS05MjVkLTQ3MGEtYWUzNy00YWQ4MDZiMjJhZGEiLCJpZCI6MjEwODIxLCJpYXQiOjE3MTM5Mjc0ODd9.tKCOP9jjOYKTT69b7wgnhlp20JeM_7ZlS9vAuTdsxBA')
    const animation = ref(false)
    const timeline = ref(false)
    const fullscreenButton = ref(false)
    const fullscreenElement = ref(undefined)
    const infoBox = ref(false)
    const showCredit = ref(false)
    const dronePosition = ref(null)
    const droneOrientation = ref(null)

    const onCesiumReady = (e) => console.log('Cesium Ready')
    const onMouseMove = () => { }
    const onLeftUp = () => { }
    const selectedEntityChanged = () => { }
    const onTouchEnd = () => { }

    const onWaypointClick = (i, e) => {
      selectedWaypointIndex.value = i
      openActionPopup(i)
    }

    const updateWaypointPosition = (i) => {
      const point = pointEntities.value[i]
      if (point) {
        const relH = takeoffPoint.value ? (point.position[2] - takeoffPoint.value.height) : point.position[2]
        point.label.text = `航点${i + 1} H:${relH.toFixed(0)}m(相对)`
        point.updateKey = Date.now()
      }
      updateAuxiliaryGraphics()
      updateTerrainHeight(i)
    }

    const updateWaypointRelativeHeight = (i, relativeHeight) => {
      if (!takeoffPoint.value) return
      const absoluteHeight = Number(relativeHeight || 0) + Number(takeoffPoint.value.height)
      const point = pointEntities.value[i]
      if (point) {
        point.position[2] = absoluteHeight
        point.label.text = `航点${i + 1} H:${absoluteHeight.toFixed(0)}m`
        point.updateKey = Date.now()
      }
      updateAuxiliaryGraphics()
      updateTerrainHeight(i)
    }

    const selectWaypoint = (i) => {
      selectedWaypointIndex.value = i
      openActionPopup(i)
    }

    const flyToWaypointIndex = (i) => {
      const point = pointEntities.value[i]
      if (point && map.viewer) {
        const [lng, lat, height] = point.position
        map.viewer.camera.flyTo({
          destination: map.Cesium.Cartesian3.fromDegrees(lng, lat, (height || 0) + 500),
          orientation: {
            heading: map.Cesium.Math.toRadians(0),
            pitch: map.Cesium.Math.toRadians(-90),
            roll: 0.0
          },
          duration: 1
        })
      }
    }

    const switchWaypointPrev = () => {
      const current = selectedWaypointIndex.value >= 0 ? selectedWaypointIndex.value : 0;
      if (current > 0) {
        selectedWaypointIndex.value = current - 1;
        flyToWaypointIndex(selectedWaypointIndex.value);
      }
    }

    const switchWaypointNext = () => {
      const current = selectedWaypointIndex.value >= 0 ? selectedWaypointIndex.value : 0;
      if (current < pointEntities.value.length - 1) {
        selectedWaypointIndex.value = current + 1;
        flyToWaypointIndex(selectedWaypointIndex.value);
      }
    }

    const onSwitchWaypoint = (idx) => {
      selectedWaypointIndex.value = idx;
      flyToWaypointIndex(idx);
    }

    const isEditMode = computed(() => props.isEditMode)
</script>
<style lang="scss" scoped>
pre {
  max-height: 200px;
  overflow: auto;
}

.flight-range-legend {
  position: absolute;
  right: 420px;
  bottom: 20px;
  z-index: 100;
  background: rgba(0, 0, 0, 0.75);
  border-radius: 8px;
  padding: 10px 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  transition: right 0.3s ease;

  &.drawer-open {
    right: 840px;
  }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .legend-color {
    display: inline-block;
    width: 60px;
    height: 12px;
    border-radius: 2px;
    border: 1px solid rgba(255, 255, 255, 0.3);
  }

  .legend-label {
    color: #fff;
    font-size: 13px;
    white-space: nowrap;
  }
}

// 鹰眼图画中画样式
.eagle-eye-pip {
  position: absolute;
  bottom: 20px;
  right: 20px;
  z-index: 500;
  width: 400px;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.6), 0 0 0 1px rgba(255, 255, 255, 0.08);
  background: #111;
  transition: right 0.3s ease;

  &.drawer-open {
    right: 440px;
  }

  .eagle-eye-hint {
    font-size: 10px;
    color: #888;
    background: rgba(255, 255, 255, 0.06);
    padding: 1px 6px;
    border-radius: 3px;
    user-select: none;
  }

  .eagle-eye-controls {
    padding: 8px 10px 10px;
    background: #161616;
    border-top: 1px solid rgba(255, 255, 255, 0.08);

    .ctrl-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 6px;
      margin-bottom: 6px;

      .ctrl-cell {
        display: flex;
        flex-direction: column;
        gap: 3px;
        background: #202020;
        padding: 4px 6px;
        border-radius: 4px;
        border: 1px solid rgba(255, 255, 255, 0.05);

        .ctrl-cell-label {
          font-size: 10px;
          color: #aaa;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }

        .ctrl-cell-input-wrap {
          display: flex;
          align-items: center;
          gap: 2px;

          .ctrl-num-input {
            width: 100%;
            background: #111;
            border-color: #333;
            color: #00f0ff;
            font-size: 11px;
            height: 22px;

            :deep(.ant-input-number-input) {
              height: 20px;
              padding: 0 4px;
              color: #00f0ff !important;
              font-weight: bold;
              font-family: 'Consolas', monospace;
              text-align: right;
            }
            :deep(.ant-input-number-handler-wrap) {
              display: none;
            }
          }

          .ctrl-cell-unit {
            font-size: 11px;
            color: #666;
          }
        }
      }
    }

    .ctrl-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 2px;

      .ctrl-footer-info {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 11px;

        .calc-label {
          color: #888;
        }
        .calc-val {
          color: #00f0ff;
          font-weight: bold;
          font-family: 'Consolas', monospace;
        }
        .calc-formula {
          color: #555;
          font-size: 10px;
          font-family: 'Consolas', monospace;
        }
      }

      .ctrl-reset-btn {
        background: #2a2a2a;
        border: 1px solid #444;
        color: #ddd;
        font-size: 10px;
        padding: 2px 8px;
        border-radius: 3px;
        cursor: pointer;
        transition: all 0.2s;

        &:hover {
          background: #1890ff;
          border-color: #1890ff;
          color: #fff;
        }
      }
    }
  }

  .eagle-eye-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 12px;
    background: rgba(0, 0, 0, 0.85);
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .eagle-eye-title {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #4fc3f7;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.5px;

      .eagle-eye-icon {
        width: 14px;
        height: 14px;
        stroke: #4fc3f7;
      }
    }

    .eagle-eye-wp-nav {
      display: flex;
      align-items: center;
      gap: 3px;
      margin-left: 8px;
      background: rgba(255, 255, 255, 0.08);
      border: 1px solid rgba(255, 255, 255, 0.15);
      border-radius: 4px;
      padding: 1px 4px;

      .nav-btn {
        background: transparent;
        border: none;
        color: #00d4ff;
        cursor: pointer;
        font-size: 10px;
        padding: 1px 4px;
        border-radius: 2px;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.2s;

        &:hover:not(:disabled) {
          background: rgba(0, 212, 255, 0.2);
          color: #fff;
        }

        &:disabled {
          color: #555;
          cursor: not-allowed;
          opacity: 0.5;
        }
      }

      .wp-selector {
        width: 80px;

        :deep(.ant-select-selector) {
          background: transparent !important;
          border: none !important;
          color: #fff !important;
          font-size: 11px !important;
          padding: 0 4px !important;
          height: 22px !important;
        }

        :deep(.ant-select-selection-item) {
          line-height: 22px !important;
          color: #00d4ff !important;
          font-weight: bold;
          text-align: center;
        }

        :deep(.ant-select-arrow) {
          color: #888;
          right: 4px;
          font-size: 9px;
        }
      }
    }

    .eagle-eye-close {
      background: none;
      border: none;
      color: #888;
      cursor: pointer;
      font-size: 14px;
      line-height: 1;
      padding: 2px 4px;
      border-radius: 4px;
      transition: all 0.2s;

      &:hover {
        color: #ff5252;
        background: rgba(255, 82, 82, 0.15);
      }
    }
  }

  .eagle-eye-body {
    position: relative;
    width: 100%;
    height: 240px;

    // 确保 vc-viewer 充满容器
    :deep(.cesium-viewer),
    :deep(.cesium-widget),
    :deep(.cesium-widget canvas) {
      width: 100% !important;
      height: 100% !important;
    }
    // 隐藏 cesium 默认 UI
    :deep(.cesium-viewer-toolbar),
    :deep(.cesium-viewer-bottom),
    :deep(.cesium-viewer-animationContainer),
    :deep(.cesium-viewer-timelineContainer),
    :deep(.cesium-viewer-fullscreenContainer) {
      display: none !important;
    }
  }

  .eagle-eye-crosshair {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
    z-index: 10;

    .crosshair-h,
    .crosshair-v {
      position: absolute;
      background: rgba(255, 255, 255, 0.25);
    }
    .crosshair-h {
      top: 50%;
      left: 30%;
      width: 40%;
      height: 1px;
    }
    .crosshair-v {
      left: 50%;
      top: 30%;
      width: 1px;
      height: 40%;
    }
  }

  .eagle-eye-overlay {
    position: absolute;
    bottom: 6px;
    left: 8px;
    display: flex;
    gap: 10px;
    z-index: 10;
    pointer-events: none;

    span {
      font-size: 10px;
      font-weight: 700;
      font-family: 'Consolas', 'Courier New', monospace;
      color: #4fc3f7;
      background: rgba(0, 0, 0, 0.6);
      padding: 2px 6px;
      border-radius: 3px;
      letter-spacing: 0.5px;
    }
  }

  .eagle-eye-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    background: #151515;
    color: #777;
    font-size: 13px;
    padding: 20px;
    text-align: center;
    border-radius: 0 0 10px 10px;
  }
}

.icon-del {
  font-size: 20px;
  color: #D6E5FB;
  cursor: pointer;
  transition: color 0.3s;
  margin-left: 10px;
  margin-top: -4px;

  &:hover {
    color: #5babcb;
  }
}

.color-box {
  display: flex;
  align-items: center;

  span {
    display: inline-block;
    width: 20px;
    height: 20px;
    margin-right: 6px;
    border-radius: 2px;
    cursor: pointer;
    border: 1px solid transparent;
    color: #fff;
    text-align: center;
    line-height: 18px;
    font-size: 16px;
    font-weight: bold;

    &:hover {
      border-color: #fff;
    }

    &:nth-of-type(1) {
      background-color: #2D8CF0;
    }

    &:nth-of-type(2) {
      background-color: #19BE6B;
    }

    &:nth-of-type(3) {
      background-color: #FFBB00;
    }

    &:nth-of-type(4) {
      background-color: #E23C39;
    }

    &:nth-of-type(5) {
      background-color: #B620E0;
    }
  }
}

.img-box {
  display: flex;
  align-items: center;

  >div {
    width: 24px;
    height: 24px;
    margin-right: 6px;
    border-radius: 2px;
    cursor: pointer;
    border: 2px solid transparent;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      border-color: #408ece;
    }

    img {
      width: 18px;
      height: 18px;



    }
  }

  .action {
    background-color: #fff;
  }

}

:deep(.map-legend) {
  position: absolute;
  left: 320px;
  bottom: 10px;
  display: flex;
  align-items: flex-end;

  .map-legend-item {
    background-color: #fff;
    border-radius: 2px;
    box-shadow: 0 0 4px rgba(0, 0, 0, .6);
    padding: 6px;
    margin-right: 4px;
  }

  .item {
    color: #000;
    font-size: .55rem;
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 1.4rem;

    img {
      width: 1rem;
      height: 1rem;
      margin-right: 4px;
    }

    .name {
      display: flex;
      align-items: center;
      margin-right: .375rem;

      span {
        display: inline-block;
        width: 1rem;
        height: 1rem;
        margin-right: 4px
      }
    }
  }
}

.context-menu {
  position: absolute;
  background-color: black;
  border: 1px solid #ccc;
  color: white;
  border-radius: 8px;
  padding: 10px 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);
  font-size: 13px;
  line-height: 1.5;
  z-index: 999;
}

.context-menu button {
  margin-top: 6px;
  background: #1677ff;
  color: white;
  border: none;
  padding: 4px 8px;
  border-radius: 4px;
  cursor: pointer;
}

.context-menu button:hover {
  background: #4096ff;
}

/* 隐藏数字输入框的上下箭头 (可选) */
::v-deep(input::-webkit-outer-spin-button),
::v-deep(input::-webkit-inner-spin-button) {
  -webkit-appearance: none;
}

.waypoint-panel {
  position: absolute;
  top: 0;
  left: 0;
  width: 400px;
  height: 100%;
  background-color: #1d1d1d;
  /* 深色背景 */
  border-right: 1px solid #333;
  z-index: 50;
  /* 确保在地图之上 */
  display: flex;
  flex-direction: column;
  color: #fff;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;

  .panel-header {
    height: 48px;
    padding: 0 16px;
    border-bottom: 1px solid #333;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background-color: #252525;

    .title {
      font-size: 14px;
      font-weight: 600;
    }

    .actions {
      cursor: pointer;
      color: #999;

      &:hover {
        color: #fff;
      }
    }
  }

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    padding: 16px 8px;
    border-bottom: 1px solid #333;
    background-color: #1d1d1d;

    .stat-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;

      .icon {
        color: #888;
        margin-bottom: 4px;
      }

      .value {
        font-size: 16px;
        font-weight: bold;
        color: #fff;
        line-height: 1.2;
      }

      .label {
        font-size: 20px;
        color: #666;
        margin-top: 2px;
      }
    }
  }

  .waypoint-list {
    flex: 1;
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: #444;
      border-radius: 2px;
    }

    .waypoint-item {
      border-bottom: 1px solid #2a2a2a;
      cursor: pointer;
      transition: background-color 0.2s;

      &:hover {
        background-color: #2a2a2a;
      }

      &.active {
        background-color: #262626;
        border-left: 3px solid #1890ff;
        /* 选中高亮条 */

        .wp-header {
          background-color: #2a2a2a;
        }
      }

      .wp-header {
        display: flex;
        align-items: center;
        padding: 12px 16px;
        height: 48px;

        .wp-index {
          flex: 1;
          display: flex;
          align-items: center;
          color: #fff;
          min-width: 0;
        }

        .wp-actions {
          flex: 1;
          display: flex;
          gap: 8px;

          .action-tag {
            font-size: 10px;
            background: #333;
            padding: 2px 6px;
            border-radius: 2px;
            color: #aaa;
          }
        }

        .wp-ops {
          display: flex;
          gap: 12px;
          opacity: 0;
          /* 默认隐藏操作按钮 */
          transition: opacity 0.2s;

          .icon-btn {
            color: #888;
            font-size: 14px;

            &:hover {
              color: #fff;
            }

            &.delete:hover {
              color: #ff4d4f;
            }
          }
        }
      }

      /* 鼠标悬停显示操作按钮 */
      &:hover .wp-ops {
        opacity: 1;
      }

      .wp-details {
        background-color: #141414;
        padding: 12px 16px;
        border-top: 1px solid #333;

        .input-group {
          display: flex;
          align-items: center;
          margin-bottom: 8px;

          .label {
            width: 60px;
            color: #888;
            font-size: 12px;
            flex-shrink: 0;
          }

          .dark-input {
            flex: 1;
            width: 100%;
            background-color: #242424 !important;
            border: 1px solid #444 !important;
            border-radius: 4px;
            color: white;

            ::v-deep(.ant-input-number-input) {
              color: white !important;
              background-color: #242424 !important;
              font-family: 'Consolas', 'Courier New', monospace;
              font-size: 12px;
            }

            ::v-deep(.ant-input-number-handler-wrap) {
              background: #2e2e2e !important;
              border-left: 1px solid #444 !important;
              opacity: 1 !important;
              border-radius: 0 4px 4px 0 !important;
              width: 22px !important;
            }

            ::v-deep(.ant-input-number-handler-up-inner),
            ::v-deep(.ant-input-number-handler-down-inner) {
              color: #00f0ff !important;
              font-size: 11px !important;
              font-weight: bold !important;
            }

            ::v-deep(.ant-input-number-handler:hover) {
              background: #3c3c3c !important;
            }

            ::v-deep(.ant-input-number-handler:hover .ant-input-number-handler-up-inner),
            ::v-deep(.ant-input-number-handler:hover .ant-input-number-handler-down-inner) {
              color: #ffffff !important;
            }

            ::v-deep(.ant-input-number-group-addon) {
              background-color: #1e1e1e !important;
              color: #00f0ff !important;
              border-color: #444 !important;
              font-size: 12px;
              font-weight: 500;
              padding: 0 8px;
            }
          }
        }

        .action-edit {
          margin-top: 12px;
          padding-top: 8px;
          border-top: 1px dashed #333;

          .section-title {
            font-size: 12px;
            color: #666;
            margin-bottom: 6px;
          }

          .action-row {
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 12px;
            color: #ccc;
          }
        }
      }
    }
  }
}

.waypoint-panel {
  position: absolute;
  top: 0;
  left: 0;
  width: 320px;
  height: 100%;
  background-color: #1d1d1d;
  z-index: 50;
  display: flex;
  flex-direction: column;
  color: #fff;

  .panel-header {
    height: 48px;
    padding: 0 16px;
    background: #252525;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-bottom: 1px solid #333;
  }

  .stats-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    padding: 12px;
    border-bottom: 1px solid #333;
    text-align: center;

    .value {
      font-size: 16px;
      font-weight: bold;
      color: #1890ff;
    }

    .label {
      font-size: 12px;
      color: #888;
    }
  }

  .waypoint-list {
    flex: 1;
    overflow-y: auto;

    .waypoint-item {
      border-bottom: 1px solid #2a2a2a;
      cursor: pointer;

      &.active {
        background: #262626;
        border-left: 3px solid #1890ff;
      }

      .wp-header {
        display: flex;
        padding: 10px 16px;
        justify-content: space-between;
        align-items: center;

        .icon-btn {
          margin-left: 8px;
          color: #666;

          &:hover {
            color: #fff;
          }

          &.delete:hover {
            color: #f5222d;
          }
        }
      }

      .wp-details {
        background: #141414;
        padding: 10px 16px;

        .input-group {
          display: flex;
          align-items: center;
          margin-bottom: 6px;

          .label {
            width: 40px;
            color: #888;
            font-size: 12px;
          }

          .dark-input {
            flex: 1;
            background: #333;
            border: 1px solid #444;
            color: #fff;
          }
        }

        .warning-text {
          font-size: 12px;
          margin-top: 4px;
          color: #aaa;
        }
      }
    }
  }
}



@keyframes pulseGlow {

  0%,
  100% {
    box-shadow: 0 0 10px rgba(0, 200, 255, 0.2);
  }

  50% {
    box-shadow: 0 0 25px rgba(0, 200, 255, 0.4);
  }
}

.drag-height-tooltip {
  position: absolute;
  z-index: 300;
  pointer-events: none;

  .tooltip-content {
    background: rgba(0, 0, 0, 0.85);
    border: 1px solid rgba(0, 255, 255, 0.6);
    border-radius: 8px;
    padding: 6px 12px;
    white-space: nowrap;
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
  }

  .tooltip-value {
    color: #00e5ff;
    font-size: 18px;
    font-weight: bold;
    line-height: 1.2;
  }

  .tooltip-delta {
    font-size: 12px;
    font-weight: 500;

    &.positive {
      color: #4ade80;
    }

    &.negative {
      color: #f87171;
    }
  }
}

.drag-mode-badge {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 200;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  pointer-events: none;
  letter-spacing: 1px;
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.4);

  &.mode-vertical {
    background: rgba(0, 200, 255, 0.2);
    border: 1px solid rgba(0, 200, 255, 0.5);
    color: #00e5ff;
  }

  &.mode-horizontal {
    background: rgba(255, 200, 0, 0.2);
    border: 1px solid rgba(255, 200, 0, 0.5);
    color: #fbbf24;
  }
}

.shortcut-hints {
  padding: 8px 16px;
  border-bottom: 1px solid #2a2a2a;
  background: #1a1a1a;

  .drag-mode-toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
    cursor: pointer;

    .toggle-label {
      font-size: 12px;
      color: #aaa;
      font-weight: 500;
    }

    .toggle-btn {
      display: flex;
      background: #2a2a2a;
      border-radius: 4px;
      overflow: hidden;
      border: 1px solid #444;

      .toggle-option {
        padding: 3px 10px;
        font-size: 11px;
        color: #666;
        transition: all 0.2s;
        white-space: nowrap;

        &.active {
          background: #00b8d4;
          color: #fff;
          font-weight: 600;
        }
      }
    }
  }

  .hint-item {
    font-size: 11px;
    color: #666;
    line-height: 1.8;

    kbd {
      display: inline-block;
      padding: 1px 5px;
      font-size: 10px;
      font-family: 'SFMono-Regular', Consolas, monospace;
      color: #bbb;
      background: #2a2a2a;
      border: 1px solid #444;
      border-radius: 3px;
      box-shadow: inset 0 -1px 0 #333;
    }
  }
}

/* ---- 左面板：动作摘要栏（始终可见） ---- */
.action-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  padding: 5px 16px 6px;
  background: #1a1a1a;
  border-top: 1px solid #2a2a2a;

  .action-add-chip {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    font-size: 11px;
    color: #1890ff;
    background: rgba(24, 144, 255, 0.08);
    border: 1px dashed rgba(24, 144, 255, 0.4);
    border-radius: 4px;
    cursor: pointer;
    transition: all 0.15s;

    &:hover {
      background: rgba(24, 144, 255, 0.18);
      border-color: #1890ff;
    }
  }

  .action-tag-mini {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    font-size: 11px;
    background: #2a2a2a;
    border: 1px solid #3a3a3a;
    padding: 2px 6px;
    border-radius: 4px;
    color: #ccc;
    white-space: nowrap;

    .tag-remove {
      font-size: 9px;
      color: #666;
      cursor: pointer;
      margin-left: 2px;
      padding: 1px;
      border-radius: 2px;

      &:hover {
        color: #ff4d4f;
        background: rgba(255, 77, 79, 0.15);
      }
    }
  }
}

/* ---- 右侧抽屉：动作编辑面板 ---- */
:deep(.action-drawer-panel) {
  .ant-drawer-content {
    background-color: rgba(24, 24, 24, 0.95) !important;
    backdrop-filter: blur(12px);
    border-left: 1px solid #333;
    color: #fff;
  }
  
  .ant-drawer-body {
    padding: 0 !important;
    overflow-y: auto;
    
    &::-webkit-scrollbar {
      width: 4px;
    }
    &::-webkit-scrollbar-thumb {
      background: #444;
      border-radius: 2px;
    }
  }
}

.action-popup-inner {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 16px;
  box-sizing: border-box;

  .action-popup-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    padding-bottom: 8px;
    border-bottom: 1px solid #333;

    .popup-title {
      font-size: 14px;
      font-weight: 600;
      color: #fff;
    }

    .popup-close {
      color: #888;
      cursor: pointer;
      font-size: 14px;
      padding: 4px;
      border-radius: 4px;
      transition: all 0.2s;

      &:hover {
        color: #fff;
        background: #333;
      }
    }
  }

  /* 组合快捷操作横幅 */
  .combo-action-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 10px;
    margin-bottom: 10px;
    border-radius: 8px;
    background: linear-gradient(135deg, rgba(24, 144, 255, 0.18) 0%, rgba(114, 46, 209, 0.22) 100%);
    border: 1px solid rgba(24, 144, 255, 0.4);
    cursor: pointer;
    transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.25);

    &:hover {
      background: linear-gradient(135deg, rgba(24, 144, 255, 0.3) 0%, rgba(114, 46, 209, 0.35) 100%);
      border-color: #1890ff;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(24, 144, 255, 0.25);
    }

    &:active {
      transform: translateY(0);
    }

    .combo-banner-left {
      display: flex;
      align-items: center;
      gap: 8px;

      .combo-banner-icon {
        width: 26px;
        height: 26px;
        border-radius: 6px;
        background: rgba(24, 144, 255, 0.25);
        color: #40a9ff;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 13px;
        font-weight: bold;
        flex-shrink: 0;
      }

      .combo-banner-text {
        display: flex;
        flex-direction: column;

        .combo-banner-title {
          font-size: 12px;
          font-weight: 600;
          color: #e6f7ff;
          line-height: 1.3;
        }

        .combo-banner-desc {
          font-size: 10px;
          color: #8c8c8c;
          margin-top: 1px;
        }
      }
    }

    .combo-banner-badge {
      font-size: 11px;
      color: #40a9ff;
      font-weight: 500;
      white-space: nowrap;
      margin-left: 6px;
    }

    &.combo-banner-photo-compare {
      background: linear-gradient(135deg, rgba(16, 185, 129, 0.18) 0%, rgba(6, 182, 212, 0.22) 100%);
      border-color: rgba(16, 185, 129, 0.4);

      &:hover {
        background: linear-gradient(135deg, rgba(16, 185, 129, 0.28) 0%, rgba(6, 182, 212, 0.32) 100%);
        border-color: #10b981;
        box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
      }

      .combo-banner-left {
        .combo-banner-icon {
          background: rgba(16, 185, 129, 0.25);
          color: #34d399;
        }
      }

      .combo-banner-badge {
        color: #34d399;
      }
    }
  }

  /* 快捷添加按钮行 */
  .quick-actions {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 6px;
    margin-bottom: 14px;
    padding-bottom: 12px;
    border-bottom: 1px solid #333;

    .quick-action-chip {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      padding: 6px 4px;
      font-size: 11px;
      border-radius: 6px;
      cursor: pointer;
      background: #2a2a2a;
      border: 1px solid #3a3a3a;
      color: #ccc;
      transition: all 0.15s;
      white-space: nowrap;

      .chip-icon {
        font-size: 12px;
      }

      &:hover {
        background: #1890ff;
        border-color: #1890ff;
        color: #fff;
      }

      &:active {
        transform: scale(0.95);
      }
    }
  }

  /* 已添加动作的卡片（垂直排列） */
  .action-cards-col {
    display: flex;
    flex-direction: column;
    gap: 8px;
    overflow-y: auto;
    flex: 1;
    padding-right: 4px;

    &::-webkit-scrollbar {
      width: 4px;
    }
    &::-webkit-scrollbar-thumb {
      background: #444;
      border-radius: 2px;
    }

    .action-card {
      background: #1f1f1f;
      border: 1px solid #333;
      border-radius: 8px;
      padding: 10px 12px;
      transition: all 0.2s;
      cursor: grab;

      &:active {
        cursor: grabbing;
      }

      &:hover {
        border-color: #1890ff;
        background: #262626;
      }

      &.active {
        border-color: #1890ff;
        box-shadow: 0 0 8px rgba(24, 144, 255, 0.45);
        background: #262626;
      }

      .card-top {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 8px;

        .card-index-badge {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 18px;
          height: 18px;
          border-radius: 50%;
          background: rgba(24, 144, 255, 0.15);
          color: #1890ff;
          font-size: 10px;
          font-weight: 700;
          border: 1px solid rgba(24, 144, 255, 0.3);
          flex-shrink: 0;
        }

        .card-icon {
          font-size: 14px;
        }

        .card-name {
          font-size: 12px;
          font-weight: 600;
          color: #fff;
        }

        .card-remove {
          font-size: 11px;
          color: #888;
          cursor: pointer;
          padding: 2px;
          border-radius: 4px;
          transition: all 0.2s;

          &:hover {
            color: #ff4d4f;
            background: rgba(255, 77, 79, 0.1);
          }
        }
      }

      .card-params {
        border-top: 1px dashed #333;
        padding-top: 8px;
        margin-top: 6px;

        .card-input-field {
          width: 80px;
          background: #141414 !important;
          border-color: #3a3a3a !important;
          color: #fff !important;
          height: 24px;
          padding: 0 4px;
          font-size: 12px;

          :deep(.ant-input-number-input) {
            color: #fff !important;
            background: #141414 !important;
          }

          :deep(.ant-input-number-handler-wrap) {
            display: none;
          }
        }

        .card-input-text {
          width: 100%;
          background: #141414 !important;
          border-color: #3a3a3a !important;
          color: #fff !important;
          font-size: 12px;
          border-radius: 4px;
          padding: 2px 6px;
        }

        .card-unit {
          font-size: 11px;
          color: #aaa;
        }

        :deep(.ant-switch) {
          background-color: #3a3a3a;
          &.ant-switch-checked {
            background-color: #1890ff;
          }
        }

        :deep(.ant-select-selector) {
          background-color: #141414 !important;
          border-color: #3a3a3a !important;
          color: #fff !important;
          height: 24px !important;
          font-size: 11px !important;
          padding: 0 6px !important;
          
          .ant-select-selection-item {
            line-height: 22px !important;
            color: #fff !important;
          }
        }
      }
    }
  }

  .action-cards-empty {
    text-align: center;
    color: #666;
    font-size: 12px;
    padding: 20px 0;
  }
}

/* ---- 等间隔拍照区域 ---- */
.interval-photo-section {
  border-bottom: 1px solid #2a2a2a;
  padding-bottom: 8px;

  .interval-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 16px 4px;

    .interval-title {
      font-size: 12px;
      color: #aaa;
      font-weight: 500;
    }

    .interval-add-btn {
      color: #1890ff;
      cursor: pointer;
      font-size: 13px;
      padding: 2px;
      border-radius: 4px;

      &:hover {
        background: rgba(24, 144, 255, 0.15);
      }
    }
  }

  .interval-form {
    padding: 8px 16px;
    background: #141414;
    border-top: 1px solid #2a2a2a;
    box-sizing: border-box;
    width: 100%;
    overflow: hidden;

    .interval-form-title {
      font-size: 11px;
      font-weight: 600;
      color: #4fc3f7;
      margin-bottom: 8px;
      padding-bottom: 4px;
      border-bottom: 1px dashed rgba(255, 255, 255, 0.1);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .interval-form-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 6px;
      min-width: 0;

      .interval-label {
        width: 52px;
        font-size: 11px;
        color: #888;
        flex-shrink: 0;
        white-space: nowrap;
      }

      .interval-input {
        width: 60px;
        background: #2a2a2a;
        border-color: #444;
        color: #fff;
        flex-shrink: 0;

        :deep(.ant-input-number-input) {
          color: #fff !important;
          background: #2a2a2a;
        }
      }

      .interval-select {
        flex: 1;
        min-width: 0;
        width: 0;

        :deep(.ant-select-selector) {
          background: #2a2a2a !important;
          border-color: #444 !important;
          color: #fff !important;
          height: 24px !important;
          display: flex;
          align-items: center;
          padding: 0 6px !important;

          .ant-select-selection-item {
            color: #fff !important;
            line-height: 22px !important;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
          }
        }
      }

      .interval-sep {
        color: #666;
        font-size: 12px;
        flex-shrink: 0;
      }

      .interval-unit {
        font-size: 11px;
        color: #888;
        flex-shrink: 0;
      }

      :deep(.ant-switch) {
        background-color: #3a3a3a;
        &.ant-switch-checked {
          background-color: #1890ff;
        }
      }
    }

    .llm-form-card {
      display: flex;
      flex-direction: column;
      gap: 6px;
      background: #1c1c1c;
      padding: 8px 10px;
      border-radius: 6px;
      border: 1px solid #333;
      box-sizing: border-box;
      width: 100%;
      min-width: 0;
      overflow: hidden;

      .llm-form-row {
        display: flex;
        align-items: center;
        gap: 6px;
        min-width: 0;
        width: 100%;

        .llm-label {
          width: 52px;
          font-size: 11px;
          color: #888;
          flex-shrink: 0;
          white-space: nowrap;
        }

        .llm-select {
          flex: 1;
          min-width: 0;
          width: 0;

          :deep(.ant-select-selector) {
            background: #2a2a2a !important;
            border-color: #444 !important;
            color: #fff !important;
            height: 24px !important;
            display: flex;
            align-items: center;
            padding: 0 6px !important;

            .ant-select-selection-item {
              color: #fff !important;
              line-height: 22px !important;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            }
          }
        }
      }
    }

    .interval-input-text {
      width: 100%;
      min-width: 0;
      background: #2a2a2a !important;
      border: 1px solid #444 !important;
      color: #fff !important;
      font-size: 11px !important;
      height: 24px !important;
      border-radius: 4px;
      padding: 2px 6px;
      box-sizing: border-box;

      &::placeholder {
        color: #666;
      }
    }

    .rule-preview-box {
      font-family: Consolas, 'Courier New', monospace;
      font-size: 10px;
      background: #0a0a0a;
      padding: 4px 6px;
      border-radius: 4px;
      border: 1px solid #222;
      color: #00f0ff;
      word-break: break-all;
      user-select: all;
      max-height: 50px;
      overflow-y: auto;
      line-height: 1.3;
    }

    :deep(.ant-radio-group) {
      .ant-radio-button-wrapper {
        font-size: 11px;
        height: 22px;
        line-height: 20px;
        padding: 0 8px;
        background: #2a2a2a;
        border-color: #444;
        color: #aaa;

        &.ant-radio-button-wrapper-checked {
          background: #1890ff;
          color: #fff;
          border-color: #1890ff;
        }
      }
    }

    .interval-form-actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
      margin-top: 6px;

      .interval-btn {
        font-size: 11px;
        padding: 3px 12px;
        border-radius: 4px;
        border: none;
        cursor: pointer;

        &.cancel {
          background: #333;
          color: #aaa;

          &:hover {
            background: #444;
          }
        }

        &.confirm {
          background: #1890ff;
          color: #fff;

          &:hover {
            background: #40a9ff;
          }
        }
      }
    }
  }

  .interval-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 6px 16px;
    cursor: pointer;
    border-left: 2px solid transparent;
    transition: all 0.15s;

    &:hover {
      background: #262626;
    }

    &.active {
      background: rgba(24, 144, 255, 0.15);
      border-left-color: #1890ff;
    }

    .interval-item-info {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #ccc;
      min-width: 0;
      flex: 1;

      .interval-badge {
        font-size: 14px;
        flex-shrink: 0;
      }

      .interval-param {
        color: #1890ff;
        font-weight: 500;
      }
    }

    .interval-item-ops {
      display: flex;
      align-items: center;
      gap: 6px;
      margin-left: 6px;
      flex-shrink: 0;

      .interval-op-btn {
        font-size: 11px;
        color: #888;
        cursor: pointer;
        padding: 3px;
        border-radius: 3px;
        transition: all 0.15s;

        &.edit:hover {
          color: #1890ff;
          background: rgba(24, 144, 255, 0.15);
        }

        &.remove:hover {
          color: #ff4d4f;
          background: rgba(255, 77, 79, 0.15);
        }
      }
    }
  }
}
</style>

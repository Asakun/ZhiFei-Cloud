
export async function makeDoubleLayerIcon(
    outerImgUrl:string,   // 外框图片
    centerImgUrl:string,  // 中心图标
    size = 128     // 输出画布大小（2 的幂）
  ) {
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = size
    const ctx = canvas.getContext('2d')
  
    // 1. 外框（底座）
    const outer = await loadImage(outerImgUrl)
    const sOuter = Math.min(size, outer.width, outer.height)
    const xOuter = (size - sOuter) / 2
    const yOuter = (size - sOuter) / 2
    ctx.drawImage(outer, xOuter, yOuter, sOuter, sOuter)
  
    // 2. 中心图标（缩小一点留边）
    const center = await loadImage(centerImgUrl)
    const sCenter = Math.min(size * 0.4, center.width, center.height)
    const xCenter = (size - sCenter) / 2
    const yCenter = (size - sCenter) / 2 - 20
    ctx.drawImage(center, xCenter, yCenter, sCenter, sCenter)
  
    return canvas.toDataURL('image/png')
}
  
function loadImage(src) {
    return new Promise((resolve, reject) => {
      const img = new Image()
      img.crossOrigin = 'anonymous'
      img.onload = () => resolve(img)
      img.onerror = reject
      img.src = src
    })
}

/**
 * 任意 3 位或 6 位十六进制色值 → rgba
 * @param {string} hex   支持 "#fff" 或 "#ffffff"（大小写均可）
 * @param {number} alpha 0~1 之间的透明度，默认 1
 * @returns {string}     "rgba(255,255,255,0.75)"
 */
export function hexToRgba(hex:string, alpha = 1) {
    // 去掉开头 #
    let str = hex.replace('#', '');
    // 3 位缩写 → 6 位
    if (str.length === 3) {
      str = str.split('').map(s => s + s).join('');
    }
    if (str.length !== 6) throw new Error('Invalid hex color');
  
    const num = Number.parseInt(str, 16);
    const r = (num >>> 16) & 0xff;
    const g = (num >>> 8)  & 0xff;
    const b = num          & 0xff;
  
    return `rgba(${r},${g},${b},${alpha})`;
}
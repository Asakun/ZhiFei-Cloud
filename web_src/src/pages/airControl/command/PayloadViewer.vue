<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import { parsePayload } from './payload-data'
import { CURRENT_CONFIG } from '@/api/http/config'

// ==================== 类型定义 ====================
interface DataPoint {
  ts: number
  lat: number
  lon: number
  h: number
  t: number
  v: Record<string, number>
}
interface Channel {
  key: string
  kind: 'num' | 'cat'
  unit: string
  min: number; max: number; p10: number; p90: number; mean: number; sd: number; digits: number
}
interface Mission {
  id: string; name: string; short: string; platform: string; job: string
  geom: 'track' | 'profile'
  count: number; raw_count: number; skipped: number
  t0: number; t1: number; duration_min: number; track_m?: number
  bbox?: { lat0: number; lat1: number; lon0: number; lon1: number }
  h: { min: number; max: number }
  channels: Channel[]; pts: DataPoint[]; primary: string
  status?: Record<string, number>
}

// ==================== 路由参数 ====================
const route = useRoute()
const transferKey = route.query.transferKey as string
const jobId      = route.query.jobId as string
const jobName    = route.query.jobName as string
const payloadName = route.query.payloadName as string

// ==================== 状态 ====================
const theme = ref<'dark'|'light'>('dark')
const missions = ref<Mission[]>([])
const currentMission = computed(() => missions.value[0])
const curKey = ref('')
const selIdx = ref(0)
let selActive = false  // 切换通道时临时关闭高亮，点击选中后重新开启
const showFlags = reactive({ field: true, track: true, pts: true, basemap: true })

// Canvas
const canvasRef = ref<HTMLCanvasElement|null>(null)
const mapWrapRef = ref<HTMLElement|null>(null)
const tipRef = ref<HTMLElement|null>(null)
let ctx: CanvasRenderingContext2D|null = null
let dpr = 1, W = 0, H = 0
let PROJ: any = null
let SCR: [number,number][] = []
const fieldCache: Record<string, HTMLCanvasElement> = {}

// 站点定位小地图（垂直剖面视图专用）
const locMapRef = ref<HTMLCanvasElement|null>(null)
let locCtx: CanvasRenderingContext2D|null = null

// ==================== 主题 ====================
const THEME = {
  dark:{
    ramp:[[0,[13,29,52]],[.18,[27,79,138]],[.38,[30,136,199]],[.56,[34,211,238]],[.72,[52,211,153]],[.86,[251,191,36]],[1,[253,230,138]]],
    cssRamp:'linear-gradient(90deg,#0D1D34,#1B4F8A,#1E88C7,#22D3EE,#34D399,#FBBF24,#FDE68A)',
    bgTop:'#0C1526', bgBot:'#080E1A',
    grid:'rgba(148,163,184,.10)', gridSoft:'rgba(148,163,184,.07)',
    border:'rgba(46,62,92,.85)', borderSoft:'rgba(148,163,184,.13)',
    axis:'rgba(148,163,184,.6)', axis2:'#64748B', axisSoft:'rgba(148,163,184,.45)',
    scale:'rgba(248,250,252,.5)', scaleText:'rgba(248,250,252,.6)',
    accent:'#22D3EE', accentLine:'rgba(34,211,238,.95)',
    trackLine:'rgba(248,250,252,.3)', dotStroke:'rgba(11,18,32,.85)', dotFill:'#F8FAFC',
    selRing:'rgba(248,250,252,.85)', selGuide:'rgba(248,250,252,.3)',
    endFill:'rgba(11,18,32,.9)', endStroke:'rgba(34,211,238,.6)',
    labelBg:'rgba(8,14,26,.72)', tagText:'rgba(148,163,184,.62)',
    flag5:'#FBBF24', flag2:'#C084FC',
    profTop:'#0C1526', profMid:'#0A1220',
    scatter:'rgba(148,163,184,.55)', selRing2:'rgba(34,211,238,.9)', axisFaint:'rgba(148,163,184,.6)'
  },
  light:{
    ramp:[[0,[203,222,241]],[.16,[157,196,232]],[.34,[100,166,221]],[.5,[40,140,192]],[.66,[22,146,118]],[.82,[205,148,8]],[1,[198,54,54]]],
    cssRamp:'linear-gradient(90deg,#CBDEF1,#9DC4E8,#64A6DD,#288CC0,#169276,#CD9408,#C63636)',
    bgTop:'#F8FBFE', bgBot:'#EDF3FA',
    grid:'rgba(15,23,42,.10)', gridSoft:'rgba(15,23,42,.055)',
    border:'rgba(15,23,42,.13)', borderSoft:'rgba(15,23,42,.13)',
    axis:'#64748B', axis2:'#64748B', axisSoft:'#8494A8',
    scale:'rgba(15,23,42,.5)', scaleText:'#475569',
    accent:'#0891B2', accentLine:'rgba(8,145,178,.95)',
    trackLine:'rgba(15,23,42,.28)', dotStroke:'#FFFFFF', dotFill:'#FFFFFF',
    selRing:'rgba(15,23,42,.65)', selGuide:'rgba(15,23,42,.3)',
    endFill:'#FFFFFF', endStroke:'#0891B2',
    labelBg:'rgba(255,255,255,.85)', tagText:'#7C8CA1',
    flag5:'#D97706', flag2:'#7C3AED',
    profTop:'#F8FBFE', profMid:'#EEF4FA',
    scatter:'rgba(71,85,105,.45)', selRing2:'rgba(8,145,178,.9)', axisFaint:'#7C8CA1'
  }
} as const
const TH = computed(() => THEME[theme.value])

// ==================== 工具 ====================
const esc = (s:any)=>String(s).replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch] as string))
const pad2 = (n:number)=>String(n).padStart(2,'0')
const tOf = (ts:number)=>{ const d=new Date(ts); return `${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}` }
const dOf = (ts:number)=>{ const d=new Date(ts); return `${d.getMonth()+1}月${d.getDate()}日` }
const fmt = (v:number,c:Channel|undefined)=>{ if(!c||v==null||isNaN(v))return String(v); return c.digits!=null?v.toFixed(c.digits):String(v) }
const fch = (v:number,c:Channel|undefined)=>fmt(v,c)+(c?.unit?' '+c.unit:'')
const chOf = (key:string)=>currentMission.value?.channels.find(c=>c.key===key)
const bounds = (c:Channel)=>c.kind==='cat'?[c.min,c.max]:[c.p10,c.p90]
const normClamped=(c:Channel,v:number)=>{ const [lo,hi]=bounds(c); return Math.max(0,Math.min(1,hi>lo?(v-lo)/(hi-lo):.5)) }
const niceTicks=(lo:number,hi:number,n:number)=>{
  const span=(hi-lo)||1,raw=span/Math.max(1,n),mag=Math.pow(10,Math.floor(Math.log10(raw))),norm=raw/mag
  const step=(norm<=1?1:norm<=2?2:norm<=2.5?2.5:norm<=5?5:10)*mag
  const out:number[]=[]
  for(let v=Math.ceil(lo/step)*step;v<=hi+1e-9;v+=step)out.push(+v.toFixed(6))
  return out
}
const ramp=(t:number)=>{
  const stops=TH.value.ramp; t=Math.max(0,Math.min(1,t))
  for(let i=1;i<stops.length;i++){
    if(t<=stops[i][0]){
      const a=stops[i-1],b=stops[i],u=(t-a[0])/(b[0]-a[0]||1)
      return [a[1][0]+(b[1][0]-a[1][0])*u,a[1][1]+(b[1][1]-a[1][1])*u,a[1][2]+(b[1][2]-a[1][2])*u]
    }
  }
  const last=stops[stops.length-1][1]
  return [last[0],last[1],last[2]]
}

// ==================== 数据解析 ====================
// 三种浮窗下行格式（键值串 / JSON / 带单位分节的纯文本）统一由 ./payload-data 解析

// ==================== 数据加载 ====================
const loadData=()=>{
  if(!transferKey){message.error('缺少传输密钥');return}
  try{
    const dataStr=window.sessionStorage.getItem(transferKey)
    if(!dataStr){message.error('未找到负载数据');return}
    const rawData=JSON.parse(dataStr)
    // 不删除 sessionStorage：刷新页面（F5）时需要重新读取同一份数据；
    // sessionStorage 会随标签页关闭自动清理。
    const mission=convertToMission(rawData)
    missions.value=[mission]
    curKey.value=mission.primary
    selIdx.value=mission.pts.length-1
  }catch(e){
    console.error('加载失败',e)
    message.error('数据格式错误')
  }
}
const convertToMission=(rawData:any):Mission=>{
  const unitMap:Record<string,string>={}
  const pts:DataPoint[]=rawData
    .filter((item:any)=>{
      if(!item.latitude||!item.longitude)return false
      return Object.keys(parsePayload(item.value).values).length>0
    })
    .map((item:any)=>{
      const parsed=parsePayload(item.value)
      const values={...parsed.values}
      Object.entries(parsed.units).forEach(([k,u])=>{ if(!unitMap[k])unitMap[k]=u })
      let ts=Date.now()
      if(item.create_time)ts=typeof item.create_time==='number'?item.create_time:new Date(item.create_time).getTime()
      // 从 t 字段提取状态（字符串中的 t:1 / t:2 / t:5 / t:4）
      const tVal=values['t']??1
      delete values['t'] // t 是状态码，不作为数据通道
      // j/w 是载荷内置经纬度，可保留或删除
      return { ts, lat:item.latitude||0, lon:item.longitude||0, h:item.height||0, t:tVal, v:values }
    })
  const validPts=pts.filter(p=>p.lat!==0&&p.lon!==0&&Object.keys(p.v).length>0)
  if(validPts.length===0)throw new Error('没有有效采样点')

  const channelKeys=new Set<string>()
  validPts.forEach(p=>Object.keys(p.v).forEach(k=>channelKeys.add(k)))

  const channels:Channel[]=Array.from(channelKeys).map(key=>{
    const vals=validPts.map(p=>p.v[key]).filter(v=>v!=null&&!isNaN(v)).sort((a,b)=>a-b)
    if(!vals.length)return null
    const min=vals[0],max=vals[vals.length-1]
    const p10=vals[Math.floor(vals.length*.1)]??min
    const p90=vals[Math.floor(vals.length*.9)]??max
    const mean=vals.reduce((a,b)=>a+b,0)/vals.length
    const sd=Math.sqrt(vals.reduce((a,v)=>a+Math.pow(v-mean,2),0)/vals.length)
    const range=max-min
    let digits=2; if(range>100)digits=1; if(range>1000)digits=0; if(range<1)digits=3
    return {key,kind:'num' as const,unit:unitMap[key]||'',min,max,p10,p90,mean,sd,digits}
  }).filter(Boolean) as Channel[]
  if(!channels.length)throw new Error('没有通道数据')

  const lats=validPts.map(p=>p.lat),lons=validPts.map(p=>p.lon)
  let trackLength=0
  for(let i=1;i<validPts.length;i++){
    const p1=validPts[i-1],p2=validPts[i]
    const dx=(p2.lon-p1.lon)*111320*Math.cos(((p1.lat+p2.lat)/2)*Math.PI/180)
    const dy=(p2.lat-p1.lat)*110540
    trackLength+=Math.sqrt(dx*dx+dy*dy)
  }
  const t0=validPts[0].ts,t1=validPts[validPts.length-1].ts
  // 水平跨度接近 0（单点悬停/垂直剖面采集）→ 剖面视图，否则空间反演视图
  const latMid=(Math.min(...lats)+Math.max(...lats))/2
  const latSpan=(Math.max(...lats)-Math.min(...lats))*110540
  const lonSpan=(Math.max(...lons)-Math.min(...lons))*111320*Math.cos(latMid*Math.PI/180)
  const geom:'track'|'profile'=Math.max(latSpan,lonSpan)<2?'profile':'track'
  // 统计状态码
  const status:Record<string,number>={'1':0,'2':0,'4':0,'5':0}
  validPts.forEach(p=>{ const k=String(p.t); if(k in status)status[k]++ })

  return {
    id:'payload',name:jobName||'负载数据',short:'浮窗下行数据',
    platform:payloadName||rawData[0]?.psdk_name||'载荷设备',
    job:(jobId||'').slice(0,8)||'unknown',geom,
    count:validPts.length,raw_count:rawData.length,skipped:rawData.length-validPts.length,
    t0,t1,duration_min:(t1-t0)/60000,track_m:trackLength,
    bbox:{lat0:Math.min(...lats),lat1:Math.max(...lats),lon0:Math.min(...lons),lon1:Math.max(...lons)},
    h:{min:Math.min(...validPts.map(p=>p.h)),max:Math.max(...validPts.map(p=>p.h))},
    channels,pts:validPts,primary:channels[0]?.key||'',status
  }
}

// ==================== 投影 ====================
const computeProjection=()=>{
  const m=currentMission.value; if(!m?.bbox)return
  const lat0=(m.bbox.lat0+m.bbox.lat1)/2
  const MX=111320*Math.cos(lat0*Math.PI/180),MY=110540
  const pts=m.pts.map(p=>[(p.lon-m.bbox!.lon0)*MX,(p.lat-m.bbox!.lat0)*MY])
  // 悬停/定高剖面任务所有点经纬度相同，退化为极小的包围盒；给出最小跨度避免除零并保持可读
  const rawX=Math.max(...pts.map(p=>p[0]))
  const rawY=Math.max(...pts.map(p=>p[1]))
  const spanX=Math.max(20,rawX)
  const spanY=Math.max(20,rawY)
  const offX=(spanX-rawX)/2,offY=(spanY-rawY)/2
  if(offX||offY)pts.forEach(p=>{p[0]+=offX;p[1]+=offY})
  const nn:number[]=[]
  for(let i=0;i<pts.length;i++){
    let best=Infinity
    for(let j=0;j<pts.length;j++)if(i!==j){const d=(pts[i][0]-pts[j][0])**2+(pts[i][1]-pts[j][1])**2;if(d<best)best=d}
    nn.push(Math.sqrt(best))
  }
  nn.sort((a,b)=>a-b)
  const R=Math.max(90,Math.min(520,nn[Math.floor(nn.length/2)]*5))
  PROJ={pts,spanX,spanY,R,X0:0,Y0:0,sc:1}
}
const resizeCanvas=()=>{
  if(!canvasRef.value||!mapWrapRef.value||!currentMission.value)return
  const m=currentMission.value
  if(m.geom==='track'&&!PROJ)return
  dpr=Math.min(2,window.devicePixelRatio||1)
  W=mapWrapRef.value.clientWidth
  H=m.geom==='track'
    ? Math.max(430,Math.min(620,W*(PROJ.spanY/PROJ.spanX)*1.08))
    : Math.max(440,Math.min(600,W*1.28))
  canvasRef.value.width=W*dpr; canvasRef.value.height=H*dpr
  canvasRef.value.style.height=H+'px'
  if(m.geom==='track'){
    const padX=.06*W,padY=.07*H
    PROJ.sc=Math.min((W-padX*2)/PROJ.spanX,(H-padY*2)/PROJ.spanY)
    PROJ.X0=(W-PROJ.spanX*PROJ.sc)/2; PROJ.Y0=(H-PROJ.spanY*PROJ.sc)/2
  }
  for(const k in fieldCache)delete fieldCache[k]
  drawMap()
}
const sx=(m:number)=>PROJ.X0+m*PROJ.sc
const sy=(m:number)=>H-(PROJ.Y0+m*PROJ.sc)

// ==================== 天地图瓦片底图（方案A：纯 Canvas 渲染） ====================
const TDT_TOKEN = CURRENT_CONFIG.tiandituToken || '4db514621ce57a692181a8bc4ddb9d72'
const TDT_SUBS = ['0','1','2','3','4','5','6','7']
const tileCache = new Map<string, HTMLImageElement | 'loading' | 'error'>()

// WGS84 → Web Mercator 瓦片坐标（XYZ 瓦片体系，与天地图 img_w/cia_w 一致）
const lngToTileX=(lon:number,z:number)=>Math.floor((lon+180)/360*(1<<z))
const latToTileY=(lat:number,z:number)=>{
  const r=lat*Math.PI/180
  return Math.floor((1-Math.log(Math.tan(r)+1/Math.cos(r))/Math.PI)/2*(1<<z))
}
const tileXToLon=(x:number,z:number)=>x/(1<<z)*360-180
const tileYToLat=(y:number,z:number)=>{
  const n=Math.PI-2*Math.PI*y/(1<<z)
  return 180/Math.PI*Math.atan(0.5*(Math.exp(n)-Math.exp(-n)))
}

// 根据当前 PROJ 缩放比自动选取合适的瓦片层级
const basemapZoom=()=>{
  if(!PROJ||!currentMission.value?.bbox)return 15
  const midLat=(currentMission.value.bbox.lat0+currentMission.value.bbox.lat1)/2
  const pxPerDeg=111320*Math.cos(midLat*Math.PI/180)*PROJ.sc
  const z=Math.round(Math.log2(pxPerDeg*360/((1<<15)*256))+15)
  return Math.max(10,Math.min(18,z))
}

// 在 Canvas 上绘制天地图瓦片（先影像后注记，异步加载，加载后自动重绘）
const drawBasemap=()=>{
  if(!ctx||!PROJ||!currentMission.value)return
  const bbox=currentMission.value.bbox!
  const midLat=(bbox.lat0+bbox.lat1)/2
  const MX=111320*Math.cos(midLat*Math.PI/180),MY=110540
  // 经纬度 → Canvas 像素（复用 PROJ 坐标体系）
  const toX=(lon:number)=>sx((lon-bbox.lon0)*MX)
  const toY=(lat:number)=>sy((lat-bbox.lat0)*MY)

  const z=basemapZoom()
  const tx0=lngToTileX(bbox.lon0-.01,z),tx1=lngToTileX(bbox.lon1+.01,z)
  const ty0=latToTileY(bbox.lat1+.01,z),ty1=latToTileY(bbox.lat0-.01,z)

  const loadTile=(key:string,url:string,onDone:()=>void)=>{
    if(tileCache.has(key))return
    tileCache.set(key,'loading')
    const img=new Image(); img.crossOrigin='anonymous'
    img.onload=()=>{ tileCache.set(key,img); onDone() }
    img.onerror=()=>tileCache.set(key,'error')
    img.src=url
  }

  for(let tx=tx0;tx<=tx1;tx++){
    for(let ty=ty0;ty<=ty1;ty++){
      const nwLon=tileXToLon(tx,z),   nwLat=tileYToLat(ty,z)
      const seLon=tileXToLon(tx+1,z), seLat=tileYToLat(ty+1,z)
      const px=toX(nwLon), py=toY(nwLat)
      const pw=toX(seLon)-px, ph=toY(seLat)-py
      const sub=TDT_SUBS[(tx+ty)%8]

      // 影像底图
      const ikey=`img/${z}/${tx}/${ty}`
      const icached=tileCache.get(ikey)
      if(icached&&icached!=='loading'&&icached!=='error'){
        ctx!.drawImage(icached as HTMLImageElement,px,py,pw,ph)
      } else {
        loadTile(ikey,
          `https://t${sub}.tianditu.gov.cn/DataServer?T=img_w&X=${tx}&Y=${ty}&L=${z}&tk=${TDT_TOKEN}`,
          ()=>drawMap())
      }

      // 注记叠加层
      const lkey=`cia/${z}/${tx}/${ty}`
      const lcached=tileCache.get(lkey)
      if(lcached&&lcached!=='loading'&&lcached!=='error'){
        ctx!.drawImage(lcached as HTMLImageElement,px,py,pw,ph)
      } else {
        const sub2=TDT_SUBS[(tx+ty+1)%8]
        loadTile(lkey,
          `https://t${sub2}.tianditu.gov.cn/DataServer?T=cia_w&X=${tx}&Y=${ty}&L=${z}&tk=${TDT_TOKEN}`,
          ()=>drawMap())
      }
    }
  }
}

// 垂直剖面模式下：右侧列小型站点定位地图
const drawLocMap=()=>{
  if(!locMapRef.value||!currentMission.value)return
  if(!locCtx)locCtx=locMapRef.value.getContext('2d')
  if(!locCtx)return
  const m=currentMission.value
  // 所有采样点平均位置作为站点中心
  const stLat=m.pts.reduce((a,p)=>a+p.lat,0)/m.pts.length
  const stLon=m.pts.reduce((a,p)=>a+p.lon,0)/m.pts.length
  const LW=locMapRef.value.clientWidth||240
  const LH=220
  const ld=Math.min(2,window.devicePixelRatio||1)
  locMapRef.value.width=LW*ld; locMapRef.value.height=LH*ld
  locMapRef.value.style.height=LH+'px'
  locCtx.setTransform(ld,0,0,ld,0,0)
  // 以站点为中心，纬度方向固定 ±0.006°（≈663m）
  // 经度方向根据 canvas 宽高比 + 墨卡托比例系数自动适配，消除拉伸
  const padLat=0.006
  const MX=111320*Math.cos(stLat*Math.PI/180), MY=110540
  const padLon=padLat*(MY/MX)*(LW/LH)
  const lb={lat0:stLat-padLat,lat1:stLat+padLat,lon0:stLon-padLon,lon1:stLon+padLon}
  // 坐标转像素
  const tX=(lon:number)=>(lon-lb.lon0)/(lb.lon1-lb.lon0)*LW
  const tY=(lat:number)=>(1-(lat-lb.lat0)/(lb.lat1-lb.lat0))*LH
  // 自动选瓦片层级
  const pxPerDeg=LW/(lb.lon1-lb.lon0)
  const z=Math.max(13,Math.min(17,Math.round(Math.log2(pxPerDeg*360/((1<<15)*256))+15)))
  // 背景
  locCtx.fillStyle=TH.value.bgBot; locCtx.fillRect(0,0,LW,LH)
  // 绘制影像底图 + 注记
  const tx0=lngToTileX(lb.lon0-.001,z),tx1=lngToTileX(lb.lon1+.001,z)
  const ty0=latToTileY(lb.lat1+.001,z),ty1=latToTileY(lb.lat0-.001,z)
  for(let tx=tx0;tx<=tx1;tx++){
    for(let ty=ty0;ty<=ty1;ty++){
      const nwLon=tileXToLon(tx,z),nwLat=tileYToLat(ty,z)
      const seLon=tileXToLon(tx+1,z),seLat=tileYToLat(ty+1,z)
      const px=tX(nwLon),py=tY(nwLat),pw=tX(seLon)-px,ph=tY(seLat)-py
      const sub=TDT_SUBS[(tx+ty)%8]
      ;[['img',`img_w`,sub],['cia',`cia_w`,TDT_SUBS[(tx+ty+1)%8]]].forEach(([pfx,type,s])=>{
        const key=`${pfx}/${z}/${tx}/${ty}`
        const cached=tileCache.get(key)
        if(cached&&cached!=='loading'&&cached!=='error'){
          locCtx!.drawImage(cached as HTMLImageElement,px,py,pw,ph)
        } else if(!cached){
          tileCache.set(key,'loading')
          const img=new Image(); img.crossOrigin='anonymous'
          img.onload=()=>{ tileCache.set(key,img); drawLocMap() }
          img.onerror=()=>tileCache.set(key,'error')
          img.src=`https://t${s}.tianditu.gov.cn/DataServer?T=${type}&X=${tx}&Y=${ty}&L=${z}&tk=${TDT_TOKEN}`
        }
      })
    }
  }
  // 十字准星
  const mx=tX(stLon),my=tY(stLat)
  locCtx.save()
  locCtx.strokeStyle='rgba(34,211,238,.45)'; locCtx.lineWidth=0.8; locCtx.setLineDash([4,3])
  locCtx.beginPath(); locCtx.moveTo(mx,0); locCtx.lineTo(mx,LH); locCtx.stroke()
  locCtx.beginPath(); locCtx.moveTo(0,my); locCtx.lineTo(LW,my); locCtx.stroke()
  locCtx.setLineDash([])
  // 同心圆站点标记
  ;[20,13,7].forEach((r,i)=>{
    locCtx!.beginPath(); locCtx!.arc(mx,my,r,0,6.2832)
    locCtx!.strokeStyle=`rgba(34,211,238,${[.25,.35,.55][i]})`;
    locCtx!.lineWidth=[1,.9,1.4][i]; locCtx!.stroke()
  })
  locCtx.beginPath(); locCtx.arc(mx,my,4,0,6.2832)
  locCtx.fillStyle=TH.value.accent; locCtx.fill()
  locCtx.strokeStyle='rgba(0,0,0,.7)'; locCtx.lineWidth=1; locCtx.stroke()
  // 坐标标签
  const label=`${stLon.toFixed(4)}°E  ${stLat.toFixed(4)}°N`
  locCtx.font='9.5px ui-monospace,monospace'
  const tw=locCtx.measureText(label).width
  const lx=Math.max(2,Math.min(LW-tw-6,mx-tw/2-3))
  const ly=my+8>LH-18?my-12:my+20
  locCtx.fillStyle='rgba(0,0,0,.55)'; locCtx.fillRect(lx-2,ly-11,tw+8,15)
  locCtx.fillStyle=TH.value.accent; locCtx.fillText(label,lx+2,ly)
  locCtx.restore()
}

// ==================== Canvas 绘制 ====================
const drawField=(c:Channel)=>{
  const off=document.createElement('canvas'),octx=off.getContext('2d')!
  const step=4,gw=Math.ceil(W/step),gh=Math.ceil(H/step)
  off.width=gw; off.height=gh
  const img=octx.createImageData(gw,gh),d=img.data
  const [lo,hi]=bounds(c),R=PROJ.R,R2=R*R
  // 预取点位坐标与通道值，避免内层循环反复访问嵌套对象（切换通道卡顿的主因）
  const pts=currentMission.value!.pts,n=pts.length
  const PX=new Float32Array(n),PY=new Float32Array(n),FV=new Float32Array(n),HV=new Uint8Array(n)
  for(let i=0;i<n;i++){
    PX[i]=PROJ.pts[i][0]; PY[i]=PROJ.pts[i][1]
    const v=pts[i].v[c.key]
    if(v==null||isNaN(v)){HV[i]=0;FV[i]=0}else{HV[i]=1;FV[i]=v}
  }
  const invHi=1/((hi-lo)||1)
  for(let gy=0;gy<gh;gy++){
    const ysrc=(H-gy*step-PROJ.Y0)/PROJ.sc
    for(let gx=0;gx<gw;gx++){
      const xsrc=(gx*step-PROJ.X0)/PROJ.sc
      let num=0,den=0,near=Infinity
      for(let i=0;i<n;i++){
        const dx=PX[i]-xsrc,dy=PY[i]-ysrc,d2=dx*dx+dy*dy
        if(d2<near)near=d2
        if(d2>R2||!HV[i])continue
        const wgt=1/(d2+40); num+=wgt*FV[i]; den+=wgt
      }
      const idx=(gy*gw+gx)*4
      if(!den){d[idx+3]=0;continue}
      const t=(num/den-lo)*invHi,col=ramp(t)
      const dist=Math.sqrt(near),a=dist>R*.75?Math.max(0,1-(dist-R*.75)/(R*.25)):1
      d[idx]=col[0];d[idx+1]=col[1];d[idx+2]=col[2];d[idx+3]=236*a
    }
  }
  octx.putImageData(img,0,0)
  return off
}
const drawMap=()=>{
  if(!ctx||!currentMission.value||!PROJ)return
  const c=chOf(curKey.value); if(!c)return
  if(currentMission.value.geom==='profile'){ drawProfile(); return }
  ctx.setTransform(dpr,0,0,dpr,0,0); ctx.clearRect(0,0,W,H)
  const g=ctx.createLinearGradient(0,0,0,H)
  g.addColorStop(0,TH.value.bgTop); g.addColorStop(1,TH.value.bgBot)
  ctx.fillStyle=g; ctx.fillRect(0,0,W,H)
  // 天地图底图（放在所有数据层之下）
  if(showFlags.basemap){ drawBasemap() }
  // 网格
  ctx.save(); ctx.strokeStyle=TH.value.gridSoft; ctx.lineWidth=1
  for(let gx=0;gx<=PROJ.spanX;gx+=500){ctx.beginPath();ctx.moveTo(sx(gx),0);ctx.lineTo(sx(gx),H);ctx.stroke()}
  for(let gy=0;gy<=PROJ.spanY;gy+=500){ctx.beginPath();ctx.moveTo(0,sy(gy));ctx.lineTo(W,sy(gy));ctx.stroke()}
  ctx.strokeStyle=TH.value.border
  ctx.strokeRect(sx(0)-.5,sy(PROJ.spanY)-.5,PROJ.spanX*PROJ.sc+1,PROJ.spanY*PROJ.sc+1)
  // 比例尺（按当前视域自适应，避免小范围任务出现超长比例尺）
  const niceSteps=[1,2,5,10,20,50,100,200,500,1000,2000,5000,10000]
  const target=PROJ.spanX*.32
  const niceM=niceSteps.reduce((a,b)=>Math.abs(b-target)<Math.abs(a-target)?b:a,niceSteps[0])
  const bw=niceM*PROJ.sc,bx=W/2-bw/2,by=H-22
  ctx.strokeStyle=TH.value.scale; ctx.lineWidth=1.3
  ctx.beginPath();ctx.moveTo(bx,by-5);ctx.lineTo(bx,by);ctx.lineTo(bx+bw,by);ctx.lineTo(bx+bw,by-5);ctx.stroke()
  ctx.fillStyle=TH.value.scaleText; ctx.font='9.5px ui-monospace,monospace'
  const sbTxt=niceM>=1000?`${niceM/1000} km`:`${niceM} m`
  ctx.fillText(sbTxt,bx+bw/2-ctx.measureText(sbTxt).width/2,by-7)
  ctx.restore()
  // 插值面
  if(showFlags.field){
    const ck=currentMission.value.id+':'+curKey.value+':'+theme.value
    if(!fieldCache[ck]){ const canvas=drawField(c); if(canvas)fieldCache[ck]=canvas }
    if(fieldCache[ck]){ ctx.save();ctx.imageSmoothingEnabled=true;ctx.drawImage(fieldCache[ck],0,0,W,H);ctx.restore() }
  }
  SCR=PROJ.pts.map((m:number[])=>[sx(m[0]),sy(m[1])] as [number,number])
  // 航迹
  if(showFlags.track){
    const pen=ctx
    pen.save();pen.strokeStyle=TH.value.trackLine;pen.lineWidth=1.2;pen.setLineDash([5,4])
    pen.beginPath();SCR.forEach((p,i)=>i?pen.lineTo(p[0],p[1]):pen.moveTo(p[0],p[1]));pen.stroke();pen.restore()
  }
  // 采样点
  if(showFlags.pts){
    currentMission.value.pts.forEach((p,i)=>{
      if(selActive&&i===selIdx.value)return  // 选中点最后单独画，保证在最顶层
      const [x,y]=SCR[i]
      const raw=p.v[c.key],t=(raw==null||isNaN(raw))?0:normClamped(c,raw),col=ramp(t)
      ctx!.beginPath();ctx!.arc(x,y,2.6,0,6.2832)
      ctx!.fillStyle=`rgb(${col[0]|0},${col[1]|0},${col[2]|0})`;ctx!.fill()
      ctx!.lineWidth=1;ctx!.strokeStyle=TH.value.dotStroke;ctx!.stroke()
      if(p.t!==1){
        ctx!.beginPath();ctx!.arc(x,y,5.5,0,6.2832)
        ctx!.strokeStyle=p.t===5?TH.value.flag5:TH.value.flag2;ctx!.lineWidth=1.4;ctx!.stroke()
      }
    })
  }
  // 起终点
  ;[[0,'起'],[currentMission.value.pts.length-1,'终']].forEach(([i,lab])=>{
    const [x,y]=SCR[i as number]
    ctx!.fillStyle=TH.value.endFill;ctx!.strokeStyle=TH.value.endStroke
    ctx!.beginPath();ctx!.arc(x,y,8,0,6.2832);ctx!.fill();ctx!.stroke()
    ctx!.fillStyle='#A9EEF9';ctx!.font='9px "Microsoft YaHei",sans-serif'
    ctx!.textAlign='center';ctx!.textBaseline='middle'
    ctx!.fillText(lab as string,x,y+.5);ctx!.textAlign='start';ctx!.textBaseline='alphabetic'
  })
  // 坐标标注
  const tag=(txt:string,x:number,y:number,align='left')=>{
    ctx!.font='9.5px ui-monospace,monospace'
    const w=ctx!.measureText(txt).width,bx2=align==='right'?x-w-4:x-3
    ctx!.fillStyle=TH.value.labelBg;ctx!.fillRect(bx2,y-10,w+7,14)
    ctx!.fillStyle=TH.value.tagText;ctx!.fillText(txt,bx2+3.5,y)
  }
  const bb=currentMission.value.bbox!
  tag(bb.lat1.toFixed(3)+'°N',sx(0)+1,sy(PROJ.spanY)-3)
  tag(bb.lat0.toFixed(3)+'°N',sx(0)+1,sy(0)-3)
  tag(bb.lon0.toFixed(3)+'°E',sx(0)+1,H-6)
  tag(bb.lon1.toFixed(3)+'°E',sx(PROJ.spanX)-1,H-6,'right')
  // 选中点高亮（最后绘制，始终在最顶层）
  if(selActive&&selIdx.value>=0&&selIdx.value<SCR.length){
    const [hx,hy]=SCR[selIdx.value]
    const sp=currentMission.value.pts[selIdx.value]
    const sraw=sp.v[c.key],st=(sraw==null||isNaN(sraw))?0:normClamped(c,sraw),scol=ramp(st)
    ctx.save()
    // 外层大光晕
    ctx.beginPath();ctx.arc(hx,hy,26,0,6.2832)
    ctx.strokeStyle='rgba(34,211,238,0.12)';ctx.lineWidth=7;ctx.stroke()
    // 中层亮环
    ctx.beginPath();ctx.arc(hx,hy,17,0,6.2832)
    ctx.strokeStyle='rgba(34,211,238,0.5)';ctx.lineWidth=1.5;ctx.stroke()
    // 内层白环
    ctx.beginPath();ctx.arc(hx,hy,10,0,6.2832)
    ctx.strokeStyle=TH.value.selRing;ctx.lineWidth=1.5;ctx.stroke()
    // 四向准星短线
    ctx.strokeStyle=TH.value.accent;ctx.lineWidth=1.2
    ;[[-1,0],[1,0],[0,-1],[0,1]].forEach(([dx,dy])=>{
      ctx.beginPath();ctx.moveTo(hx+dx*20,hy+dy*20);ctx.lineTo(hx+dx*32,hy+dy*32);ctx.stroke()
    })
    // 选中点本体（置顶重绘）
    ctx.beginPath();ctx.arc(hx,hy,6,0,6.2832)
    ctx.fillStyle=`rgb(${scol[0]|0},${scol[1]|0},${scol[2]|0})`;ctx.fill()
    ctx.lineWidth=1.5;ctx.strokeStyle=TH.value.dotStroke;ctx.stroke()
    ctx.restore()
  }
}

// ==================== 垂直剖面绘制（单站点 / 悬停采集） ====================
const drawProfile=()=>{
  if(!ctx||!currentMission.value)return
  const m=currentMission.value,c=chOf(curKey.value); if(!c)return
  const P=m.pts
  const pad={l:52,r:16,t:26,b:56}
  const usable=W-pad.l-pad.r,gap=46
  const leftW=Math.max(150,usable*.55-gap/2)
  const splitX=pad.l+leftW,rightL=splitX+gap
  const rightW=Math.max(90,W-pad.r-rightL)
  const yT=pad.t,yB=H-pad.b,ih=yB-yT
  const altMin=m.h.min-2,altMax=m.h.max+2
  const Y=(h:number)=>yB-(h-altMin)/((altMax-altMin)||1)*ih
  const Xt=(i:number)=>pad.l+(P.length<2?0:i/(P.length-1))*leftW
  const vmin=c.min,vmax=c.max,vpad=(vmax-vmin)*.06||1
  const plo=vmin-vpad,phi=vmax+vpad
  const [lo,hi]=bounds(c)
  const Xv=(v:number)=>rightL+Math.max(0,Math.min(1,(v-plo)/((phi-plo)||1)))*rightW

  ctx.setTransform(dpr,0,0,dpr,0,0); ctx.clearRect(0,0,W,H)
  const bg=ctx.createLinearGradient(0,0,0,H)
  bg.addColorStop(0,TH.value.profTop); bg.addColorStop(.55,TH.value.profMid); bg.addColorStop(1,TH.value.bgBot)
  ctx.fillStyle=bg; ctx.fillRect(0,0,W,H)

  // 高度网格
  ctx.font='9.5px ui-monospace,monospace'
  niceTicks(altMin,altMax,4).forEach(alt=>{
    const y=Y(alt)
    ctx!.strokeStyle=TH.value.grid
    ctx!.beginPath();ctx!.moveTo(pad.l,y);ctx!.lineTo(W-pad.r,y);ctx!.stroke()
    ctx!.fillStyle=TH.value.axis; ctx!.textAlign='right'
    ctx!.fillText(alt.toFixed(0),pad.l-8,y+3); ctx!.textAlign='start'
  })
  ctx.fillStyle=TH.value.axisSoft
  ctx.save();ctx.translate(11,yT+ih/2);ctx.rotate(-Math.PI/2)
  ctx.textAlign='center';ctx.fillText('高度 (m)',0,0);ctx.restore()

  // 左侧时间网格
  for(let k=0;k<=3;k++){
    const x=pad.l+leftW*k/3
    ctx.strokeStyle=TH.value.gridSoft
    ctx.beginPath();ctx.moveTo(x,yT);ctx.lineTo(x,yB);ctx.stroke()
  }
  // 分栏线
  ctx.strokeStyle=TH.value.border
  ctx.beginPath();ctx.moveTo(splitX+gap/2,yT);ctx.lineTo(splitX+gap/2,yB);ctx.stroke()

  // 轨迹着色（时间 × 高度）
  ctx.lineCap='round'
  if(showFlags.track){
    ctx.strokeStyle=TH.value.gridSoft; ctx.lineWidth=6
    ctx.beginPath()
    P.forEach((p,i)=>i?ctx!.lineTo(Xt(i),Y(p.h)):ctx!.moveTo(Xt(i),Y(p.h)))
    ctx.stroke()
    for(let i=0;i<P.length-1;i++){
      const va=P[i].v[c.key]
      const a=(va==null||isNaN(va))?.5:(hi!==lo?(va-lo)/(hi-lo):.5)
      const col=ramp(a)
      ctx.strokeStyle=`rgba(${col[0]|0},${col[1]|0},${col[2]|0},.92)`; ctx.lineWidth=3.6
      ctx.beginPath();ctx.moveTo(Xt(i),Y(P[i].h));ctx.lineTo(Xt(i+1),Y(P[i+1].h));ctx.stroke()
    }
  }

  // 采样点
  SCR=P.map((p,i)=>[Xt(i),Y(p.h)])
  if(showFlags.pts){
    P.forEach((p,i)=>{
      const raw=p.v[c.key],t=(raw==null||isNaN(raw))?0:normClamped(c,raw),col=ramp(t)
      ctx!.beginPath();ctx!.arc(SCR[i][0],SCR[i][1],i===selIdx.value?4.4:2.4,0,6.2832)
      ctx!.fillStyle=`rgb(${col[0]|0},${col[1]|0},${col[2]|0})`;ctx!.fill()
      ctx!.lineWidth=1;ctx!.strokeStyle=TH.value.dotStroke;ctx!.stroke()
    })
  }

  // 剖面曲线（浓度 × 高度）
  if(showFlags.field){
    P.forEach(p=>{
      const v=p.v[c.key]; if(v==null||isNaN(v))return
      ctx!.beginPath();ctx!.arc(Xv(v),Y(p.h),1.9,0,6.2832)
      ctx!.fillStyle=TH.value.scatter;ctx!.fill()
    })
    const LV=44,prof:{h:number;v:number}[]=[]
    for(let k=0;k<=LV;k++){
      const h=m.h.min+(m.h.max-m.h.min)*k/LV
      let sw=0,sv=0
      P.forEach(p=>{
        const v=p.v[c.key]; if(v==null||isNaN(v))return
        const dd=(p.h-h)/3.4,wgt=Math.exp(-.5*dd*dd); sw+=wgt; sv+=wgt*v
      })
      if(sw>0)prof.push({h,v:sv/sw})
    }
    if(prof.length){
      ctx.beginPath()
      prof.forEach((q,i)=>i?ctx!.lineTo(Xv(q.v),Y(q.h)):ctx!.moveTo(Xv(q.v),Y(q.h)))
      ctx.strokeStyle=TH.value.accentLine;ctx.lineWidth=2;ctx.stroke()
      ;[prof[0],prof[prof.length-1]].forEach(q=>{
        ctx!.beginPath();ctx!.arc(Xv(q.v),Y(q.h),2.6,0,6.2832)
        ctx!.fillStyle=TH.value.accent;ctx!.fill()
      })
    }
    // 数值轴
    ctx.font='9px ui-monospace,monospace';ctx.textAlign='center'
    niceTicks(plo,phi,3).forEach(v=>{
      const x=Xv(v)
      ctx!.strokeStyle=TH.value.gridSoft
      ctx!.beginPath();ctx!.moveTo(x,yT);ctx!.lineTo(x,yB);ctx!.stroke()
      ctx!.fillStyle=TH.value.axis
      ctx!.fillText(fmt(v,c),Math.min(W-pad.r-2,Math.max(rightL+4,x)),H-32)
    })
    ctx.textAlign='start'
  }

  // 选中高亮
  if(SCR[selIdx.value]){
    const p=P[selIdx.value]
    ctx.save();ctx.strokeStyle=TH.value.selGuide;ctx.setLineDash([3,4]);ctx.lineWidth=1
    ctx.beginPath();ctx.moveTo(pad.l,Y(p.h));ctx.lineTo(W-pad.r,Y(p.h));ctx.stroke();ctx.restore()
    ctx.beginPath();ctx.arc(SCR[selIdx.value][0],SCR[selIdx.value][1],9,0,6.2832)
    ctx.strokeStyle=TH.value.selRing;ctx.lineWidth=1.3;ctx.stroke()
    const sv=p.v[c.key]
    if(sv!=null&&!isNaN(sv)){
      ctx.beginPath();ctx.arc(Xv(sv),Y(p.h),5.5,0,6.2832)
      ctx.strokeStyle=TH.value.selRing2;ctx.lineWidth=1.2;ctx.stroke()
    }
  }

  // 阶段标注
  const hThr=m.h.min+.85*(m.h.max-m.h.min)
  let aEnd=P.findIndex(p=>p.h>=hThr); if(aEnd<0)aEnd=0
  let dStart=P.length-1
  for(let i=P.length-1;i>=0;i--){ if(P[i].h>=hThr){dStart=i;break} }
  const phases:Array<[number,string]>=[[Math.max(1,aEnd-2),'爬升'],[Math.round((aEnd+dStart)/2),'悬停'],[Math.min(P.length-1,dStart+2),'下降']]
  ctx.font='9.5px "Microsoft YaHei",sans-serif';ctx.fillStyle=TH.value.axisSoft
  phases.forEach(([i,label])=>{
    const x=Xt(i)
    if(x>pad.l+8&&x<splitX-16)ctx!.fillText(label,x,yT-8)
  })

  // 时间轴
  ctx.fillStyle=TH.value.axis;ctx.font='8.5px ui-monospace,monospace'
  for(let k=0;k<=2;k++){
    const i=Math.round((P.length-1)*k/2),x=Xt(i)
    if(k===0){ctx.textAlign='left';ctx.fillText(tOf(P[i].ts),x,H-32)}
    else if(k===2){ctx.textAlign='right';ctx.fillText(tOf(P[i].ts),Math.min(x,splitX-2),H-32)}
    else {ctx.textAlign='center';ctx.fillText(tOf(P[i].ts),x,H-32)}
  }
  ctx.textAlign='start'
  ctx.fillStyle=TH.value.axisFaint;ctx.font='9px "Microsoft YaHei",sans-serif'
  ctx.fillText('时间 →',pad.l,H-12)
  ctx.fillText('浓度 ('+(c.unit||'')+') →',rightL,H-12)
}

// ==================== 时序曲线 SVG ====================
const seriesSvg = computed(()=>{
  const m=currentMission.value; if(!m)return ''
  const c=chOf(curKey.value); if(!c)return ''
  const P=m.pts,W2=1200,H2=280,pad={l:60,r:20,t:20,b:50}
  const iw=W2-pad.l-pad.r,ih=H2-pad.t-pad.b
  // 缺测点用前一个有效值补齐，保证折线连续
  let prev:number|null=null
  const vals=P.map(p=>{
    const raw=p.v[c.key]
    if(raw==null||isNaN(raw)){ if(prev==null)prev=0; return prev }
    prev=raw; return raw
  })
  let lo=Math.min(...vals),hi=Math.max(...vals)
  const padY=(hi-lo)*.12||1; lo-=padY; hi+=padY
  if(Math.min(...vals)>=0&&lo<0)lo=0
  const X=(i:number)=>pad.l+(P.length<2?0:i/(P.length-1))*iw
  const Y=(v:number)=>pad.t+ih*(1-(v-lo)/(hi-lo||1))
  let s=`<svg viewBox="0 0 ${W2} ${H2}" style="width:100%;height:auto;overflow:visible">`
  s+=`<defs><linearGradient id="ag" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${TH.value.accent}" stop-opacity=".26"/><stop offset="1" stop-color="${TH.value.accent}" stop-opacity="0"/></linearGradient></defs>`
  // 网格线和Y轴刻度
  for(let k=0;k<=4;k++){
    const y=pad.t+ih*k/4,v=hi-(hi-lo)*k/4
    s+=`<line x1="${pad.l}" y1="${y}" x2="${W2-pad.r}" y2="${y}" stroke="${TH.value.grid}"/>`
    s+=`<text x="${pad.l-10}" y="${y+4}" text-anchor="end" fill="${TH.value.axis2}" font-size="11" font-family="ui-monospace,monospace">${fmt(v,c)}</text>`
  }
  const line=vals.map((v,i)=>`${X(i).toFixed(1)},${Y(v).toFixed(1)}`).join('L')
  s+=`<path d="M${line}L${X(P.length-1)},${pad.t+ih}L${X(0)},${pad.t+ih}Z" fill="url(#ag)"/>`
  s+=`<path d="M${line}" fill="none" stroke="${TH.value.accentLine}" stroke-width="2.2" stroke-linejoin="round"/>`
  P.forEach((p,i)=>{
    if(p.t===1)return
    const col=p.t===5?TH.value.flag5:TH.value.flag2,x=X(i),y=Y(vals[i])
    s+=`<path d="M${x},${y-5.5}L${x+5.5},${y}L${x},${y+5.5}L${x-5.5},${y}Z" fill="${col}"/>`
  })
  if(selIdx.value>=0&&selIdx.value<P.length){
    s+=`<line x1="${X(selIdx.value)}" y1="${pad.t}" x2="${X(selIdx.value)}" y2="${pad.t+ih}" stroke="${TH.value.selGuide}" stroke-width="1.5" stroke-dasharray="4 4"/>`
    s+=`<circle cx="${X(selIdx.value)}" cy="${Y(vals[selIdx.value]??lo)}" r="5" fill="${TH.value.dotFill}" stroke="${TH.value.dotStroke}" stroke-width="2"/>`
  }
  // X轴时间刻度
  const step=Math.max(1,Math.ceil(P.length/10))
  P.forEach((p,i)=>{ if(i%step)return; s+=`<text x="${X(i)}" y="${H2-20}" text-anchor="middle" fill="${TH.value.axis2}" font-size="10" font-family="ui-monospace,monospace">${tOf(p.ts)}</text>` })
  // 坐标轴标签
  s+=`<text x="${pad.l}" y="${H2-4}" text-anchor="start" fill="${TH.value.axisSoft}" font-size="11" font-family="ui-monospace,monospace">时间 →</text>`
  s+=`<text x="${W2-pad.r}" y="${H2-4}" text-anchor="end" fill="${TH.value.axisSoft}" font-size="11" font-family="ui-monospace,monospace">${c.key} (${c.unit||''}) →</text>`
  s+=`</svg>`
  return s
})
const serTitle = computed(()=>`通道 ${curKey.value.toUpperCase()} 沿${isProfile.value?'时间':'航迹'}变化`)
const serPill  = computed(()=>currentMission.value?`${currentMission.value.pts.length} 点`:'')
const serLegend = computed(()=>{
  const m=currentMission.value; if(!m)return ''
  let s=`<span><i style="display:inline-block;width:16px;height:3px;background:${TH.value.accent};vertical-align:middle;margin-right:6px"></i>通道值</span>`
  if(hasFlags.value){
    s+=`<span><i style="display:inline-block;width:9px;height:9px;background:${TH.value.flag5};vertical-align:middle;margin-right:6px;transform:rotate(45deg)"></i>t=5 异常</span>`
    s+=`<span><i style="display:inline-block;width:9px;height:9px;background:${TH.value.flag2};vertical-align:middle;margin-right:6px;transform:rotate(45deg)"></i>t=2 关注</span>`
  }
  return s
})

// ==================== 详情面板 ====================
const detTime = computed(()=>{ const p=currentMission.value?.pts[selIdx.value]; return p?tOf(p.ts):'—' })
const detLoc  = computed(()=>{
  const p=currentMission.value?.pts[selIdx.value]; if(!p)return '—'
  if(currentMission.value?.geom==='profile')
    return `${dOf(p.ts)} · 高度 ${p.h.toFixed(1)} m<br>${p.lon.toFixed(4)}°E ${p.lat.toFixed(4)}°N`
  return `${dOf(p.ts)} · ${p.lat.toFixed(5)}°N<br>${p.lon.toFixed(5)}°E · ${p.h.toFixed(1)} m · t=${p.t}`
})
const detDotColor = computed(()=>{
  const p=currentMission.value?.pts[selIdx.value]; if(!p)return 'var(--cyan)'
  if(!currentMission.value?.status)return 'var(--cyan)'
  return p.t===5?'var(--amber)':p.t===2?'var(--purple)':'var(--ok)'
})
const detGrid = computed(()=>{
  const p=currentMission.value?.pts[selIdx.value]; if(!p)return ''
  return currentMission.value!.channels.map(c=>{
    const v=p.v[c.key],t=(v==null||isNaN(v))?0:normClamped(c,v)
    return `<div class="kv"><span class="k">${esc(c.key)}</span><span class="v">${v!=null&&!isNaN(v)?fmt(v,c):'—'}</span>
      <span class="m"><i style="width:${(t*100).toFixed(0)}%"></i></span></div>`
  }).join('')
})
// 图例 / KPI 派生值（避免模板里出现非空断言）
const legendInfo = computed(()=>{
  const c=chOf(curKey.value); if(!c)return null
  const [lo,hi]=bounds(c)
  return { key:c.key, unit:c.unit, lo:fmt(lo,c), hi:fmt(hi,c) }
})
const trackKm = computed(()=>((currentMission.value?.track_m||0)/1000))
const avgKmh = computed(()=>{
  const m=currentMission.value
  if(!m||!m.duration_min)return 0
  return trackKm.value/(m.duration_min/60)
})

// ==================== 视图模式（空间反演 / 垂直剖面） ====================
const isProfile = computed(()=>currentMission.value?.geom==='profile')
const sampleSec = computed(()=>{
  const m=currentMission.value
  if(!m||m.count<2)return 5
  return Math.round((m.t1-m.t0)/Math.max(1,m.count-1)/1000*10)/10
})
const heroTitle = computed(()=>isProfile.value?'大气监测 <em>垂直剖面</em>':'反演成果 <em>空间分布图</em>')
const heroText = computed(()=>{
  const m=currentMission.value; if(!m)return ''
  return isProfile.value
    ? `浮窗下行数据即大气监测结果：载荷在单点上空垂直爬升后悬停再下降，共 ${m.count} 帧。页面以「时间 × 高度」着色轨迹与「浓度 × 高度」剖面曲线呈现，可逐通道切换、逐帧查看。`
    : `浮窗下行数据即反演结果：每个采样点携带经纬度、相对高度与 ${m.channels.length} 个参数通道。页面按地理位置做反距离权重插值成面，叠加航迹与采样点，可逐通道切换、逐点查看。`
})
const heroChips = computed(()=>{
  const m=currentMission.value; if(!m)return []
  if(isProfile.value)return [
    `${m.count} 条有效记录`,
    `采样间隔 ${sampleSec.value} s`,
    `${m.channels.length} 个气体通道`,
    `高度 ${m.h.min.toFixed(0)}–${m.h.max.toFixed(0)} m`,
    '单站点垂直剖面'
  ]
  return [
    `${m.raw_count} 条原始记录 / ${m.count} 个有效采样点`,
    `${m.channels.length} 个参数通道`,
    `航高 ${m.h.min.toFixed(0)}–${m.h.max.toFixed(0)} m`,
    `${m.duration_min.toFixed(1)} min`
  ]
})
const kpiItems = computed(()=>{
  const m=currentMission.value; if(!m)return []
  if(isProfile.value){
    const pm=chOf('PM2.5')||m.channels[0]
    const pm10=chOf('PM10')||m.channels[1]||pm
    return [
      {lab:'有效记录',tag:'SAMPLES',val:String(m.count),unit:'帧',cls:'',foot:`采样间隔 ${sampleSec.value} s · 单站点`},
      {lab:'垂直跨度',tag:'COLUMN',val:(m.h.max-m.h.min).toFixed(0),unit:'m',cls:'g',foot:`${m.h.min.toFixed(0)} – ${m.h.max.toFixed(0)} m · ${m.duration_min.toFixed(1)} min`},
      {lab:`${pm.key} 均值`,tag:pm.unit||'MEAN',val:fmt(pm.mean,pm),unit:pm.unit||'',cls:'a',foot:`${pm10.key} ${fmt(pm10.mean,pm10)} · 峰值 ${fmt(pm.max,pm)}`},
      {lab:'气体通道',tag:'GASES',val:String(m.channels.length),unit:'个',cls:'p',foot:m.channels.map(c=>c.key).join(' / ')}
    ]
  }
  return [
    {lab:'有效采样点',tag:'POINTS',val:String(m.count),unit:'个',cls:'',foot:`原始 ${m.raw_count} 条 · 跳过 ${m.skipped} 条`},
    {lab:'航迹长度',tag:'TRACK',val:trackKm.value.toFixed(2),unit:'km',cls:'g',foot:`${avgKmh.value.toFixed(1)} km/h · ${m.duration_min.toFixed(1)} min`},
    {lab:'异常 / 关注点',tag:'FLAGS',val:String((m.status?.['5']||0)+(m.status?.['2']||0)),unit:'个',cls:'a',foot:`t=5 异常 ${m.status?.['5']||0} · t=2 关注 ${m.status?.['2']||0}`},
    {lab:'参数通道',tag:'CHANNELS',val:String(m.channels.length),unit:'个',cls:'p',foot:`t=4 异常 ${m.status?.['4']||0} · 常规 ${m.status?.['1']||0} 点`}
  ]
})

// ==================== 点位排序 ====================
const hasFlags = computed(()=>!!currentMission.value?.pts.some(p=>p.t!==1))
const rankTitle = computed(()=>hasFlags.value?'异常与关注点位':'高值点位 TOP 12')
const rankPill = computed(()=>{
  const m=currentMission.value; if(!m)return '—'
  if(!hasFlags.value)return `按 ${curKey.value} 排序`
  const rows=m.pts.filter(p=>p.t!==1)
  return `${rows.length} 条 · 按 ${curKey.value} 排序`
})
const rankRows = computed(()=>{
  const m=currentMission.value; if(!m)return []
  const c=chOf(curKey.value); if(!c)return []
  const key=curKey.value
  const val=(p:DataPoint)=>{ const v=p.v[key]; return v==null||isNaN(v)?-Infinity:v }
  let rows=hasFlags.value
    ?m.pts.map((p,i)=>({p,i})).filter(o=>o.p.t!==1).sort((a,b)=>val(b.p)-val(a.p))
    :m.pts.map((p,i)=>({p,i})).sort((a,b)=>val(b.p)-val(a.p)).slice(0,12)
  const rs=rows.map(r=>val(r.p)).filter(v=>v!==-Infinity)
  const rlo=rs.length?Math.min(...rs):0,rhi=rs.length?Math.max(...rs):1,rspan=(rhi-rlo)||1
  return rows.map(({p,i})=>{
    const raw=val(p),show=raw===-Infinity?0:raw
    const t=raw===-Infinity?.04:Math.max(.04,Math.min(1,(raw-rlo)/rspan))
    return {i,time:tOf(p.ts),status:p.t,bar:t,val:raw===-Infinity?'—':fmt(show,c)}
  })
})

// ==================== 地图信息栏 ====================
const mapFootItems = computed(()=>{
  const m=currentMission.value; const c=chOf(curKey.value); if(!m||!c)return []
  const hasSel=selIdx.value>=0&&selIdx.value<m.pts.length
  const p=hasSel?m.pts[selIdx.value]:undefined
  return [
    {label:'当前通道', val:curKey.value},
    {label:'均值 ± 标准差', val:`${fmt(c.mean,c)} ± ${fmt(c.sd,c)}`},
    {label:'最小值 – 最大值', val:`${fmt(c.min,c)} – ${fmt(c.max,c)}`},
    m.geom==='profile'
      ? {label:'选中帧 / 高度', val:hasSel?`#${selIdx.value+1} · ${(p!.h).toFixed(1)} m`:'—'}
      : {label:'插值半径 / 选中点', val:`${PROJ?Math.round(PROJ.R):'-'} m · ${hasSel?`#${selIdx.value+1}`:'—'}`}
  ]
})

// ==================== 通道统计表 ====================
const statRows = computed(()=>{
  const m=currentMission.value; if(!m)return []
  return m.channels.map(c=>{
    const cv=c.mean?(c.sd/Math.abs(c.mean)*100):0
    const P=m.pts,lo0=bounds(c)[0],hi0=bounds(c)[1],sp=(hi0-lo0)||1
    const sparkPts=P.map((p,i)=>{
      const raw=p.v[c.key]
      const v=(raw==null||isNaN(raw))?lo0:Math.max(lo0,Math.min(hi0,raw))
      return `${(i/((P.length-1)||1)*92).toFixed(1)},${(20-2-(v-lo0)/sp*16).toFixed(1)}`
    }).join(' ')
    return {c,cv,sparkPts}
  })
})

// ==================== 采样记录 ====================
const rawCols = computed(()=>{
  const m=currentMission.value; if(!m)return []
  return m.channels.slice(0, m.geom==='track'?6:5)
})
const footNote = computed(()=>{
  const m=currentMission.value; if(!m)return ''
  if(m.geom==='profile'){
    return `剖面曲线由 ${m.count} 帧按高度做高斯核平滑（σ≈3.4 m）后得到，用于表达气体浓度随高度的连续变化，不代表实测瞬时值。`
  }
  return `插值面由 ${m.count} 个采样点按反距离权重（IDW）生成，仅用于表达参数的空间连续性，不代表该航段以外的实测值；通道语义请按载荷浮窗协议解释。t 为记录状态码：t=1 常规采样 ${m.status?.['1']||0} 点、t=2 关注 ${m.status?.['2']||0} 点、t=4 异常 ${m.status?.['4']||0} 点、t=5 异常 ${m.status?.['5']||0} 点。`
})
const rawRows = computed(()=>{
  const m=currentMission.value; if(!m)return []
  return m.pts.map((p,i)=>({p,i}))
})

// ==================== 交互 ====================
const nearest=(px:number,py:number,maxR:number)=>{
  let best=maxR*maxR,bi=-1
  for(let i=0;i<SCR.length;i++){const dx=SCR[i][0]-px,dy=SCR[i][1]-py,d=dx*dx+dy*dy;if(d<best){best=d;bi=i}}
  return bi
}
const onMousemove=(e:MouseEvent)=>{
  if(!canvasRef.value||!tipRef.value||!currentMission.value)return
  const r=canvasRef.value.getBoundingClientRect(),px=e.clientX-r.left,py=e.clientY-r.top
  const i=nearest(px,py,18); if(i<0){tipRef.value.classList.remove('on');return}
  const p=currentMission.value.pts[i],c=chOf(curKey.value)
  const m=currentMission.value
  const headRight=m.geom==='track'?`t=${p.t}`:`H ${p.h.toFixed(1)} m`
  const extra=m.geom==='track'
    ? `<div class="r"><span>经度</span><b>${p.lon.toFixed(5)}</b></div>
       <div class="r"><span>纬度</span><b>${p.lat.toFixed(5)}</b></div>`
    : m.channels.filter(cc=>cc.key!==curKey.value).slice(0,2).map(cc=>{
        const v=p.v[cc.key]
        return `<div class="r"><span>${esc(cc.key)}</span><b>${v!=null&&!isNaN(v)?esc(fch(v,cc)):'—'}</b></div>`
      }).join('')
  tipRef.value.innerHTML=`<div class="hd"><span>#${i+1} ${tOf(p.ts)}</span><span>${headRight}</span></div>
    <div class="r"><span>${esc(curKey.value)}</span><b>${esc(fch(p.v[curKey.value],c))}</b></div>${extra}`
  tipRef.value.classList.add('on')
  const tw=200,th=tipRef.value.offsetHeight||110
  let tx=px+16,ty=py+12
  if(tx+tw>W)tx=px-tw-14; if(ty+th>H)ty=py-th-12
  tipRef.value.style.left=tx+'px'; tipRef.value.style.top=ty+'px'
}
const onMouseleave=()=>tipRef.value?.classList.remove('on')
const onCanvasClick=(e:MouseEvent)=>{
  if(!canvasRef.value)return
  const r=canvasRef.value.getBoundingClientRect(),i=nearest(e.clientX-r.left,e.clientY-r.top,24)
  if(i>=0)select(i)
}
const onSeriesClick=(e:MouseEvent)=>{
  const svg=(e.currentTarget as HTMLElement).querySelector('svg'); if(!svg||!currentMission.value)return
  const b=svg.getBoundingClientRect(),u=(e.clientX-b.left)/b.width
  const uu=(u-46/640)/(1-58/640)
  select(Math.round(uu*(currentMission.value.pts.length-1)))
}
const onRankClick=(i:number)=>select(i)
const onRawRowClick=(i:number)=>select(i)
const select=(i:number)=>{
  if(!currentMission.value)return
  selActive=true
  selIdx.value=Math.max(0,Math.min(currentMission.value.pts.length-1,i))
  drawMap()
  // 滚动原始记录行到视野
  nextTick(()=>{
    const box=document.querySelector('#rawScroll') as HTMLElement|null
    const row=document.querySelector(`#rawTbl [data-i="${selIdx.value}"]`) as HTMLElement|null
    if(row&&box){
      const rr=row.getBoundingClientRect(),br=box.getBoundingClientRect()
      box.scrollTop+=rr.top-br.top-box.clientHeight/2+row.clientHeight/2
    }
  })
}
const toggleTheme=()=>{
  theme.value=theme.value==='dark'?'light':'dark'
  drawMap()
  if(currentMission.value?.geom==='profile')drawLocMap()
}
const toggleShow=(key:'field'|'track'|'pts'|'basemap')=>{showFlags[key]=!showFlags[key];drawMap()}
const switchChannel=(key:string)=>{selActive=false;curKey.value=key;drawMap()}

// ==================== 生命周期 ====================
onMounted(()=>{
  loadData()
  nextTick(()=>{
    if(canvasRef.value)ctx=canvasRef.value.getContext('2d')
    if(currentMission.value){
      computeProjection(); resizeCanvas()
      if(currentMission.value.geom==='profile') nextTick(drawLocMap)
    }
    window.addEventListener('resize',resizeCanvas)
  })
})
watch(()=>currentMission.value,(v)=>{
  if(v)nextTick(()=>{
    if(canvasRef.value)ctx=canvasRef.value.getContext('2d')
    computeProjection(); resizeCanvas()
    if(v.geom==='profile'){ locCtx=null; nextTick(drawLocMap) }
  })
})
onBeforeUnmount(()=>window.removeEventListener('resize',resizeCanvas))
</script>

<template>
  <div class="pv" :data-theme="theme">
    <div class="wrap">

      <!-- ===== 顶部 ===== -->
      <div class="top">
        <svg class="mark" viewBox="0 0 40 40" fill="none">
          <defs><linearGradient id="mg" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#338DFF"/><stop offset="1" stop-color="#22D3EE"/>
          </linearGradient></defs>
          <path d="M20 3l14.7 8.5v17L20 37 5.3 28.5v-17L20 3z" stroke="url(#mg)" stroke-width="1.4" opacity=".9"/>
          <path d="M20 11l7.8 4.5v9L20 29l-7.8-4.5v-9L20 11z" stroke="#22D3EE" stroke-width="1.1" opacity=".5"/>
          <circle cx="20" cy="20" r="3.2" fill="url(#mg)"/>
        </svg>
        <div class="brand"><b>负载数据展示</b></div>
        <div class="spacer"></div>
        <button class="chip tbtn" @click="toggleTheme">{{ theme==='dark'?'🌙 深色':'☀️ 浅色' }}</button>
        <div class="chip"><i></i>1 个任务</div>
        <div class="chip mono" v-if="currentMission">载荷 {{ currentMission.platform }}</div>
        <div class="chip mono" v-if="currentMission">job {{ currentMission.job }}</div>
      </div>

      <!-- ===== Hero ===== -->
      <div class="hero" v-if="currentMission">
        <h1 v-html="heroTitle"></h1>
        <p>{{ heroText }}</p>
        <div class="meta">
          <div class="chip mono" style="border-color:var(--line2);color:var(--ice)">载荷设备 <b style="color:var(--ink)">{{ currentMission.platform }}</b></div>
          <div class="chip mono" v-for="c in heroChips" :key="c">{{ c }}</div>
        </div>
      </div>

      <!-- ===== KPI ===== -->
      <div class="kpis" v-if="currentMission">
        <div class="kpi" v-for="k in kpiItems" :key="k.lab" :class="k.cls">
          <div class="lab">{{ k.lab }}<span>{{ k.tag }}</span></div>
          <div class="val" :class="k.cls">{{ k.val }}<small>{{ k.unit }}</small></div>
          <div class="foot">{{ k.foot }}</div>
        </div>
      </div>

      <!-- ===== 01 分布 ===== -->
      <div class="sec" v-if="currentMission">
        <div class="sec-h">
          <span class="no">01</span><h2>{{ isProfile ? '大气参数垂直剖面' : '反演参数空间分布' }}</h2>
          <span class="rt">{{ isProfile ? '时间 × 高度着色轨迹 · 单击点位联动' : 'IDW 插值成面 · 单击点位或曲线联动' }}</span>
        </div>
        <div class="card">
          <!-- 通道栏 -->
          <div class="chbar">
            <button v-for="ch in currentMission.channels" :key="ch.key"
              class="ch" :class="{on:ch.key===curKey}" @click="switchChannel(ch.key)">
              {{ ch.key.toUpperCase() }}
            </button>
            <span class="sep"></span>
            <button class="tg" :class="{on:showFlags.field}" @click="toggleShow('field')">{{ isProfile ? '浓度着色' : '插值面' }}</button>
            <button class="tg" :class="{on:showFlags.track}" @click="toggleShow('track')">{{ isProfile ? '剖面曲线' : '航迹' }}</button>
            <button class="tg" :class="{on:showFlags.pts}" @click="toggleShow('pts')">采样点</button>
            <button v-if="!isProfile" class="tg" :class="{on:showFlags.basemap}" @click="toggleShow('basemap')">🗺 天地图</button>
          </div>

          <!-- 地图 + 右侧列 -->
          <div class="ovbody" :class="{profile:isProfile}">
            <!-- 左侧地图列 -->
            <div class="mapcol">
              <div class="mapwrap" ref="mapWrapRef">
                <canvas ref="canvasRef" id="map"
                  @mousemove="onMousemove" @mouseleave="onMouseleave" @click="onCanvasClick"/>
                <div ref="tipRef" class="tip"></div>
              </div>
              <!-- 图例 -->
              <div class="legend" v-if="legendInfo">
                <span class="nm">{{ legendInfo.key.toUpperCase() }}<em v-if="legendInfo.unit">{{ legendInfo.unit }}</em></span>
                <span class="lgp">P10</span>
                <span>{{ legendInfo.lo }}</span>
                <div class="ramp" :style="{background:TH.cssRamp}"></div>
                <span>{{ legendInfo.hi }}</span>
                <span class="lgp">P90</span>
              </div>
              <!-- 信息栏 -->
              <div class="mapfoot">
                <div class="stat" v-for="item in mapFootItems" :key="item.label">
                  <span>{{ item.label }}</span><b>{{ item.val }}</b>
                </div>
              </div>
            </div>

            <!-- 右侧列 -->
            <div class="rcol">
              <!-- 站点定位地图（仅剖面视图显示） -->
              <div class="pane" v-if="isProfile">
                <div class="card-h">
                  <h3>📍 站点位置</h3>
                  <span class="pill">{{ currentMission.pts[0]?.lon.toFixed(4) }}°E · {{ currentMission.pts[0]?.lat.toFixed(4) }}°N</span>
                </div>
                <canvas ref="locMapRef" class="loc-map"></canvas>
              </div>

              <!-- 详情面板 -->
              <div class="pane">
                <div class="phead">
                  <span class="dot" :style="{background:detDotColor,boxShadow:`0 0 9px ${detDotColor}`}"></span>
                  <span class="time">{{ detTime }}</span>
                  <span class="loc" v-html="detLoc"></span>
                </div>
                <div style="padding:9px 15px 13px">
                  <div class="det" v-html="detGrid"></div>
                </div>
              </div>

              <!-- 点位排序 -->
              <div class="pane">
                <div class="card-h"><h3>{{ rankTitle }}</h3><span class="pill">{{ rankPill }}</span></div>
                <div class="alist">
                  <div v-for="row in rankRows" :key="row.i"
                    class="arow" :class="{on:row.i===selIdx}"
                    @click="onRankClick(row.i)">
                    <span>{{ row.time }}</span>
                    <span class="bdg" :class="row.status===5?'am':row.status===2?'pu':'ok'">{{ row.status }}</span>
                    <span class="bar"><i :style="{width:(row.bar*100).toFixed(0)+'%'}"></i></span>
                    <span class="v">{{ row.val }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 时序曲线 - 移到地图下方独立区域 -->
          <div class="series-chart">
            <div class="card-h">
              <h3>{{ serTitle }}</h3><span class="pill">{{ serPill }}</span>
            </div>
            <div class="chart-container" @click="onSeriesClick">
              <div v-html="seriesSvg"></div>
            </div>
            <div class="ser-lgd" v-html="serLegend"></div>
          </div>
        </div>
      </div>

      <!-- ===== 02 通道统计 ===== -->
      <div class="sec" v-if="currentMission">
        <div class="sec-h">
          <span class="no">02</span><h2>通道统计</h2>
          <span class="rt">{{ currentMission.count }} 点 · 全航段</span>
        </div>
        <div class="card">
          <div class="scroll">
            <table>
              <thead><tr>
                <th>通道</th><th class="mono">单位</th>
                <th class="mono">最小值</th><th class="mono">P10</th>
                <th class="mono">均值</th><th class="mono">P90</th>
                <th class="mono">最大值</th><th class="mono">标准差</th>
                <th class="mono">CV%</th><th>沿航迹走势</th>
              </tr></thead>
              <tbody>
                <tr v-for="row in statRows" :key="row.c.key">
                  <td><b style="font-family:var(--mono)">{{ row.c.key }}</b></td>
                  <td class="mono" style="color:var(--dim)">{{ row.c.unit||'—' }}</td>
                  <td class="mono">{{ fmt(row.c.min,row.c) }}</td>
                  <td class="mono">{{ fmt(row.c.p10,row.c) }}</td>
                  <td class="mono" style="color:var(--ice)">{{ fmt(row.c.mean,row.c) }}</td>
                  <td class="mono">{{ fmt(row.c.p90,row.c) }}</td>
                  <td class="mono">{{ fmt(row.c.max,row.c) }}</td>
                  <td class="mono" style="color:var(--dim)">{{ fmt(row.c.sd,row.c) }}</td>
                  <td class="mono" :style="{color:row.cv>15?'var(--amber)':'var(--sub)'}">{{ row.cv.toFixed(1) }}</td>
                  <td>
                    <svg width="92" height="22" style="vertical-align:middle">
                      <polyline :points="row.sparkPts" fill="none" stroke="var(--cyan)" stroke-width="1.2" opacity=".85"/>
                    </svg>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
        <div class="note" v-if="currentMission">
          <b>说明</b>
          <span>{{ footNote }}</span>
        </div>
      </div>

      <!-- ===== 03 采样记录 ===== -->
      <div class="sec" v-if="currentMission">
        <div class="sec-h">
          <span class="no">03</span><h2>采样记录</h2>
          <span class="rt">共 {{ currentMission.count }} 条 · 单击行定位</span>
        </div>
        <div class="card">
          <div class="scroll" id="rawScroll">
            <table id="rawTbl">
              <thead><tr>
                <th>#</th><th>时间</th><th class="mono">t</th>
                <th v-if="!isProfile" class="mono">经度</th><th v-if="!isProfile" class="mono">纬度</th>
                <th class="mono">高度 m</th>
                <th v-for="c in rawCols" :key="c.key" class="mono">{{ c.key }}<template v-if="c.unit"> ({{ c.unit }})</template></th>
              </tr></thead>
              <tbody>
                <tr v-for="row in rawRows" :key="row.i"
                  :data-i="row.i"
                  :class="{selected:row.i===selIdx}"
                  style="cursor:pointer"
                  @click="onRawRowClick(row.i)">
                  <td class="mono" style="color:var(--dim)">{{ row.i+1 }}</td>
                  <td class="mono">{{ tOf(row.p.ts) }}</td>
                  <td><span class="bdg" :class="row.p.t===1?'ok':row.p.t===5||row.p.t===4?'am':'pu'">{{ row.p.t }}</span></td>
                  <td v-if="!isProfile" class="mono">{{ row.p.lon.toFixed(5) }}</td>
                  <td v-if="!isProfile" class="mono">{{ row.p.lat.toFixed(5) }}</td>
                  <td class="mono">{{ row.p.h.toFixed(1) }}</td>
                  <td v-for="c in rawCols" :key="c.key" class="mono">{{ row.p.v[c.key]!=null?fmt(row.p.v[c.key],c):'—' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- ===== 底部 ===== -->
      <div class="foot" v-if="currentMission">
        <span>反演成果图 · 单任务展示 · 无人机智飞控制平台</span>
        <span class="mono">{{ currentMission.platform }} · {{ dOf(currentMission.t0) }} {{ tOf(currentMission.t0) }}–{{ tOf(currentMission.t1) }} · {{ currentMission.count }} 点</span>
      </div>

    </div>
  </div>
</template>

<style scoped lang="scss">
.pv {
  --bg: #0B1220; --card: #101A2E; --card2: #1A2744;
  --ink: #F8FAFC; --sub: #CBD5E1; --dim: #64748B; --ice: #E0F2FE;
  --line: rgba(148,163,184,0.12); --line2: rgba(34,211,238,0.18);
  --cyan: #22D3EE; --blue: #3B82F6; --amber: #F59E0B; --purple: #A855F7;
  --ok: #34D399; --warn: #FBBF24; --err: #F87171;
  --mono: 'JetBrains Mono','SF Mono',Monaco,Consolas,monospace;
  --sans: -apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif;
  --shd: 0 8px 24px rgba(0,0,0,0.4);
  /* 全局样式给 html/body 设了 overflow:hidden，本页自带滚动容器 */
  height: 100vh; overflow-y: auto; overflow-x: hidden;
  background: var(--bg);
  color: var(--ink); font-family: var(--sans); line-height: 1.6;
  transition: background .3s, color .3s;
}
.pv[data-theme="light"] {
  --bg: #F4F7FB; --card: #FFFFFF; --card2: #F1F5F9;
  --ink: #0F172A; --sub: #475569; --dim: #94A3B8; --ice: #0C4A6E;
  --line: rgba(51,65,85,0.12); --line2: rgba(8,145,178,0.22);
  --cyan: #0891B2; --blue: #2563EB; --amber: #D97706; --purple: #9333EA;
  --shd: 0 4px 16px rgba(15,23,42,0.1);
}
.wrap { max-width: 1680px; margin: 0 auto; padding: 20px; }

/* ===== 顶部 ===== */
.top {
  display: flex; align-items: center; gap: 12px; padding: 10px 16px;
  background: var(--card); border-radius: 12px; margin-bottom: 24px;
  border: 1px solid var(--line);
}
.mark { width: 32px; height: 32px; flex-shrink: 0; }
.brand { display: flex; flex-direction: column; gap: 1px;
  b { font-size: 15px; font-weight: 600; color: var(--ink); }
  span { font-size: 10px; letter-spacing: 0.8px; color: var(--dim);
    text-transform: uppercase; font-weight: 500; }
}
.spacer { flex: 1; }
.chip {
  padding: 5px 11px; background: var(--card2); border-radius: 7px;
  font-size: 13px; color: var(--sub); border: 1px solid var(--line);
  display: flex; align-items: center; gap: 6px; white-space: nowrap;
  i { width: 6px; height: 6px; border-radius: 50%; background: var(--cyan);
    box-shadow: 0 0 8px var(--cyan); }
  &.mono { font-family: var(--mono); font-size: 12px; }
  &.tbtn { cursor: pointer; transition: all .2s;
    &:hover { background: var(--card); border-color: var(--cyan); color: var(--cyan); }
  }
}

/* ===== Hero ===== */
.hero {
  padding: 32px 24px; background: var(--card); border-radius: 16px;
  border: 1px solid var(--line2); margin-bottom: 24px; position: relative;
  overflow: hidden;
  &::before {
    content: ''; position: absolute; top: -50%; right: -10%; width: 400px; height: 400px;
    background: radial-gradient(circle, rgba(34,211,238,0.08) 0%, transparent 70%);
    pointer-events: none;
  }
  .badge {
    display: inline-block; padding: 4px 10px; background: rgba(34,211,238,0.12);
    color: var(--cyan); font-size: 11px; font-weight: 600; letter-spacing: 0.6px;
    text-transform: uppercase; border-radius: 5px; margin-bottom: 12px;
    border: 1px solid rgba(34,211,238,0.25);
  }
  h1 { font-size: 32px; font-weight: 700; margin: 0 0 12px; color: var(--ink);
    em { font-style: normal; color: var(--cyan); font-weight: 600; }
  }
  p { font-size: 14px; color: var(--sub); line-height: 1.7; max-width: 900px; margin-bottom: 18px; }
  .meta { display: flex; flex-wrap: wrap; gap: 8px; }
}

/* ===== KPI ===== */
.kpis { display: grid; grid-template-columns: repeat(4,1fr); gap: 10px; margin-bottom: 20px; }
.kpi {
  background: var(--card); border: 1px solid var(--line); border-radius: 10px;
  padding: 12px 16px; position: relative; overflow: hidden;
  &::before {
    content: ''; position: absolute; bottom: 0; right: 0; width: 80px; height: 80px;
    background: radial-gradient(circle at bottom right, rgba(34,211,238,0.08), transparent 60%);
    pointer-events: none;
  }
  &.g::before { background: radial-gradient(circle at bottom right, rgba(52,211,153,0.08), transparent 60%); }
  &.a::before { background: radial-gradient(circle at bottom right, rgba(251,191,36,0.08), transparent 60%); }
  &.p::before { background: radial-gradient(circle at bottom right, rgba(168,85,247,0.08), transparent 60%); }
  .lab { display: flex; flex-direction: column; gap: 1px; margin-bottom: 6px;
    font-size: 12px; font-weight: 600; color: var(--sub);
    span { font-size: 9px; letter-spacing: 0.7px; color: var(--dim);
      text-transform: uppercase; font-weight: 500; }
  }
  .val { font-size: 24px; font-weight: 700; font-family: var(--mono);
    color: var(--cyan); margin-bottom: 4px;
    small { font-size: 13px; font-weight: 500; color: var(--dim); margin-left: 3px; }
    &.g { color: var(--ok); }
    &.a { color: var(--amber); }
    &.p { color: var(--purple); }
  }
  .foot { font-size: 11px; color: var(--dim); }
}

/* ===== Section ===== */
.sec { margin-bottom: 40px;
  .sec-h {
    display: flex; align-items: center; gap: 12px; margin-bottom: 16px;
    .no {
      display: inline-flex; align-items: center; justify-content: center;
      width: 28px; height: 28px; background: var(--cyan); color: var(--bg);
      font-size: 13px; font-weight: 700; border-radius: 7px; font-family: var(--mono);
    }
    h2 { font-size: 20px; font-weight: 700; margin: 0; color: var(--ink); }
    .rt { margin-left: auto; font-size: 12px; color: var(--dim); font-family: var(--mono); }
  }
}
.card {
  background: var(--card); border: 1px solid var(--line); border-radius: 14px;
  overflow: hidden; box-shadow: var(--shd);
  .card-h {
    display: flex; align-items: center; gap: 8px; padding: 10px 14px;
    border-bottom: 1px solid var(--line);
    h3 { font-size: 14px; font-weight: 600; margin: 0; color: var(--ink); }
    .pill {
      padding: 2px 8px; background: var(--card2); border-radius: 5px;
      font-size: 11px; color: var(--dim); font-family: var(--mono); border: 1px solid var(--line);
    }
  }
}

/* ===== 通道栏 ===== */
.chbar {
  display: flex; flex-wrap: wrap; gap: 6px; padding: 14px; background: var(--card2);
  border-bottom: 1px solid var(--line);
  .ch {
    padding: 6px 12px; background: var(--card); border: 1px solid var(--line);
    border-radius: 7px; font-size: 12px; font-weight: 600; color: var(--sub);
    cursor: pointer; transition: all .2s; font-family: var(--mono);
    &:hover { border-color: var(--cyan); color: var(--cyan); }
    &.on { background: var(--cyan); color: var(--bg); border-color: var(--cyan);
      box-shadow: 0 0 12px rgba(34,211,238,0.4); }
  }
  .sep { flex: 1; min-width: 16px; }
  .tg {
    padding: 6px 12px; background: var(--card); border: 1px solid var(--line);
    border-radius: 7px; font-size: 12px; font-weight: 500; color: var(--sub);
    cursor: pointer; transition: all .2s;
    &:hover { border-color: var(--cyan); color: var(--cyan); }
    &.on { background: rgba(34,211,238,0.15); color: var(--cyan); border-color: var(--cyan); }
  }
}

/* ===== 布局 ===== */
.ovbody { display: flex; gap: 16px; padding: 16px; }
.mapcol { flex: 1; display: flex; flex-direction: column; gap: 10px; }
.rcol { width: 320px; flex-shrink: 0; display: flex; flex-direction: column; gap: 12px; }
/* 垂直剖面模式：画布收窄，右侧信息列加宽（与参考设计一致） */
.ovbody.profile {
  .mapcol { flex: 0 1 620px; }
  .rcol { flex: 1; width: auto; min-width: 320px; }
}

/* ===== 地图 ===== */
.mapwrap {
  position: relative; background: var(--card2); border-radius: 10px;
  border: 1px solid var(--line); overflow: hidden;
  #map { width: 100%; display: block; cursor: crosshair; }
  .tip {
    position: absolute; background: var(--card); padding: 9px 12px;
    border-radius: 8px; border: 1px solid var(--line2); pointer-events: none;
    font-size: 12px; color: var(--ink); font-family: var(--mono); line-height: 1.55;
    box-shadow: 0 6px 18px rgba(0,0,0,0.35); backdrop-filter: blur(8px);
    white-space: nowrap; display: none; z-index: 10;
    &.on { display: block; }
  }
  :deep(.tip .hd) {
    display: flex; justify-content: space-between; gap: 16px; padding-bottom: 5px;
    margin-bottom: 5px; border-bottom: 1px solid var(--line2); color: var(--cyan);
    font-weight: 600;
  }
  :deep(.tip .r) {
    display: flex; justify-content: space-between; gap: 16px; color: var(--sub);
    b { color: var(--ink); font-weight: 600; }
  }
}
.legend {
  display: flex; align-items: center; gap: 10px; padding: 10px 14px;
  background: var(--card2); border-radius: 8px; border: 1px solid var(--line);
  font-size: 12px; font-family: var(--mono); color: var(--sub);
  .nm { font-weight: 700; color: var(--ice); margin-right: 4px;
    em { font-style: normal; font-weight: 500; color: var(--dim); margin-left: 4px; font-size: 10px; }
  }
  .lgp { color: var(--dim); font-size: 10px; }
  .ramp { flex: 1; height: 18px; border-radius: 4px; border: 1px solid var(--line); }
}
/* 站点定位小地图 */
.loc-map {
  width: 100%; height: 220px; display: block; cursor: default;
  border-radius: 0 0 10px 10px;
}
.mapfoot {
  display: grid; grid-template-columns: repeat(4,1fr); gap: 10px;
  .stat {
    background: var(--card2); padding: 8px 12px; border-radius: 7px;
    border: 1px solid var(--line); display: flex; flex-direction: column; gap: 3px;
    span { font-size: 10px; color: var(--dim); text-transform: uppercase;
      letter-spacing: 0.5px; font-weight: 500; }
    b { font-size: 16px; font-family: var(--mono); color: var(--cyan); }
  }
}

/* ===== 右侧列 ===== */
.pane {
  background: var(--card); border: 1px solid var(--line); border-radius: 10px;
  overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.phead {
  display: flex; align-items: center; gap: 8px; padding: 10px 14px;
  border-bottom: 1px solid var(--line); background: var(--card2);
  .dot {
    width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
    background: var(--cyan); box-shadow: 0 0 10px var(--cyan);
  }
  .time { font-size: 13px; font-family: var(--mono); color: var(--ink); font-weight: 600; }
  .loc { font-size: 11px; color: var(--dim); margin-left: auto; font-family: var(--mono); }
}

/* ===== 时序曲线区域（独立大区域） ===== */
.series-chart {
  margin-top: 16px; background: var(--card); border: 1px solid var(--line);
  border-radius: 14px; overflow: hidden; box-shadow: var(--shd);
  .chart-container {
    padding: 20px 30px 10px;
    cursor: pointer;
    transition: background .2s;
    &:hover { background: var(--card2); }
  }
}
.ser-lgd {
  padding: 10px 16px; font-size: 12px; color: var(--dim); text-align: center;
  border-top: 1px solid var(--line); background: var(--card2);
  :deep(span) { display: inline-flex; align-items: center; margin: 0 10px; }
  :deep(i) { display: inline-block; vertical-align: middle; margin-right: 6px; }
}

.det { display: grid; grid-template-columns: repeat(2,1fr); gap: 9px; }
/* detGrid 由 v-html 注入，需要 :deep 才能命中 */
.det :deep(.kv) { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.det :deep(.kv .k) {
  font-size: 10px; color: var(--dim); text-transform: uppercase;
  letter-spacing: 0.4px; font-weight: 500; overflow: hidden; text-overflow: ellipsis;
}
.det :deep(.kv .v) {
  font-size: 13px; font-family: var(--mono); color: var(--ink); font-weight: 600;
}
.det :deep(.kv .m) {
  display: block; height: 5px; background: var(--card2); border-radius: 3px;
  overflow: hidden; border: 1px solid var(--line);
}
.det :deep(.kv .m i) {
  display: block; height: 100%; background: linear-gradient(90deg,var(--cyan),var(--blue));
  transition: width .3s;
}
.alist {
  max-height: 320px; overflow-y: auto; padding: 6px;
  &::-webkit-scrollbar { width: 6px; }
  &::-webkit-scrollbar-track { background: var(--card2); }
  &::-webkit-scrollbar-thumb { background: var(--line); border-radius: 3px;
    &:hover { background: var(--dim); }
  }
}
.arow {
  display: grid; grid-template-columns: 60px 24px 1fr 50px; gap: 8px;
  align-items: center; padding: 7px 9px; border-radius: 6px; cursor: pointer;
  transition: all .15s; font-size: 11px; font-family: var(--mono);
  &:hover { background: var(--card2); }
  &.on { background: rgba(34,211,238,0.12); border: 1px solid var(--cyan); }
  span:first-child { color: var(--dim); }
  .bdg {
    display: inline-flex; align-items: center; justify-content: center;
    width: 20px; height: 18px; border-radius: 4px; font-size: 10px; font-weight: 700;
    &.ok { background: rgba(52,211,153,0.15); color: var(--ok); border: 1px solid var(--ok); }
    &.am { background: rgba(248,113,113,0.15); color: var(--err); border: 1px solid var(--err); }
    &.pu { background: rgba(251,191,36,0.15); color: var(--amber); border: 1px solid var(--amber); }
  }
  .bar {
    height: 12px; background: var(--card2); border-radius: 3px; overflow: hidden;
    border: 1px solid var(--line);
    i { display: block; height: 100%; background: var(--cyan); }
  }
  .v { color: var(--cyan); font-weight: 600; text-align: right; }
}

/* ===== 表格 ===== */
.scroll {
  overflow-x: auto; max-height: 500px; overflow-y: auto;
  &::-webkit-scrollbar { width: 8px; height: 8px; }
  &::-webkit-scrollbar-track { background: var(--card2); }
  &::-webkit-scrollbar-thumb { background: var(--line); border-radius: 4px;
    &:hover { background: var(--dim); }
  }
}
table {
  width: 100%; border-collapse: collapse; font-size: 13px;
  thead {
    position: sticky; top: 0; background: var(--card2); z-index: 2;
    th {
      padding: 10px 14px; text-align: left; font-weight: 600; color: var(--sub);
      border-bottom: 2px solid var(--line); font-size: 12px; white-space: nowrap;
      &.mono { font-family: var(--mono); }
    }
  }
  tbody {
    tr {
      border-bottom: 1px solid var(--line); transition: background .15s;
      &:hover { background: var(--card2); }
      &.selected { background: rgba(34,211,238,0.12); }
      td {
        padding: 9px 14px; color: var(--sub); white-space: nowrap;
        &.mono { font-family: var(--mono); }
        b { color: var(--ink); font-weight: 600; }
        .bdg {
          display: inline-flex; align-items: center; justify-content: center;
          min-width: 22px; height: 20px; padding: 0 6px; border-radius: 5px;
          font-size: 11px; font-weight: 700; font-family: var(--mono);
          &.ok { background: rgba(52,211,153,0.15); color: var(--ok); border: 1px solid var(--ok); }
          &.am { background: rgba(248,113,113,0.15); color: var(--err); border: 1px solid var(--err); }
          &.pu { background: rgba(251,191,36,0.15); color: var(--amber); border: 1px solid var(--amber); }
        }
      }
    }
  }
}
.note {
  margin-top: 12px; padding: 14px 18px; background: var(--card2); border-radius: 8px;
  border-left: 3px solid var(--cyan); font-size: 12px; color: var(--sub); line-height: 1.7;
  b { color: var(--cyan); margin-right: 8px; font-weight: 600; }
}

/* ===== 底部 ===== */
.foot {
  display: flex; justify-content: space-between; align-items: center; padding: 16px 20px;
  background: var(--card); border-radius: 10px; border: 1px solid var(--line);
  margin-top: 32px; font-size: 12px; color: var(--dim);
  .mono { font-family: var(--mono); }
}

/* ===== 响应式 ===== */
@media (max-width: 1200px) {
  .kpis { grid-template-columns: repeat(2, 1fr); }
  .ovbody, .ovbody.profile { flex-direction: column; }
  .rcol, .ovbody.profile .rcol { width: auto; min-width: 0; }
  .ovbody.profile .mapcol { flex: 1 1 auto; }
  .mapfoot { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 720px) {
  .wrap { padding: 12px; }
  .kpis { grid-template-columns: 1fr; }
  .mapfoot { grid-template-columns: 1fr; }
  .hero { padding: 22px 16px; }
  .hero h1 { font-size: 24px; }
  .top { flex-wrap: wrap; }
  .foot { flex-direction: column; gap: 6px; align-items: flex-start; }
}
</style>

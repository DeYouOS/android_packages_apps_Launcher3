# robot/ — AI 机器人动画系统

Launcher3 第一屏的车载 AI 机器人，18 个 Java 文件、9424 行纯自研代码。
不依赖 Launcher3 上游任何 robot 相关代码。

## 架构

```
RobotPageView (DragLayer Overlay, Launcher.java 唯一集成点)
├── RobotSurfaceView (TextureView 渲染层)
│   └── RobotRenderThread (60fps 游戏循环)
│       ├── CarSensorManager → CarMotionState      # 传感器输入
│       ├── RobotPhysicsEngine                      # 物理模拟
│       │   ├── SpringSolver          (身体弹簧)
│       │   ├── TailPhysics           (尾巴6段链)
│       │   ├── SpeedTracker          (GPS速度区间)
│       │   ├── EnvironmentTracker    (驾驶模式)
│       │   └── AnimationPriorityManager (P0-P5仲裁)
│       ├── CatAnimator               # 15个动画子系统
│       └── CatRenderer               # Canvas 2D 绘制
│           └── ParticleSystem         (5类粒子)
└── RobotCommandOverlay (WiFi二维码/信息卡片)
    └── RobotCommandBus (LiveData 事件总线)
```

## 每帧数据流

```
传感器 → CarSensorManager(滤波) → CarMotionState(快照)
  → RobotPhysicsEngine.update(dt, motionState, robotState)
    → CatAnimator.update(dt, robotState)
      → CatRenderer.draw(canvas, robotState, particleSystem)
```

## 文件清单

| 文件 | 行数 | 职责 |
|------|------|------|
| **CatRenderer** | 2625 | Canvas 2D 绘制：身体/眼睛/嘴巴/手臂/眉毛/耳朵/尾巴/天线/胸部/腮红/思维气泡/指示灯 |
| **CatAnimator** | 1696 | 动画状态机：15 个子系统（呼吸/眨眼/表情过渡/耳朵抽动/手臂/嘴巴/眉毛/身体动作/眼睛特效/思维气泡/天线/指示灯/开机序列/Demo模式） |
| **RobotState** | 1110 | 状态容器：15 个枚举 + 63 个字段，所有动画参数的 single source of truth |
| **RobotPhysicsEngine** | 567 | 物理主控：整合弹簧/尾巴/速度/环境/优先级，映射传感器→动画状态 |
| **CarSensorManager** | 537 | 传感器单例：加速度计+陀螺仪+GPS，低通滤波+互补滤波+死区 |
| **EnvironmentTracker** | 419 | 环境追踪：驾驶模式/困倦度/昼夜时段/充电状态 |
| **ParticleSystem** | 398 | 5类粒子：环境/拖尾/呼噜/爆发/星空，对象池+分层绘制 |
| **RobotCommandOverlay** | 313 | UI 覆盖层：WiFi 二维码展示和信息卡片 |
| **SpeedTracker** | 301 | 速度区间：停车/市区/高速/超速/危险，6dB 滞回防震荡 |
| **TailPhysics** | 263 | 尾巴物理：6 段弹簧链+鞭梢放大+表情响应 |
| **AnimationPriorityManager** | 258 | 优先级仲裁：9 个身体部件 × 6 层优先级（P0安全→P5空闲） |
| **RobotRenderThread** | 174 | 渲染线程：传感器→物理→动画→绘制→帧率控制流水线 |
| **RobotPageView** | 146 | 页面容器：管理 SurfaceView + Overlay 生命周期 |
| **SpringSolver** | 144 | 弹簧求解器：半隐式欧拉积分 |
| **RobotSurfaceView** | 129 | TextureView：管理物理引擎和渲染线程实例 |
| **RobotCommand** | 120 | 命令基类：WiFi二维码/信息/表情/动画/气泡 5 种子命令 |
| **CarMotionState** | 118 | 传感器快照：不可变，三轴力/GPS速度/航向 |
| **RobotCommandBus** | 106 | 命令总线：LiveData 单例，跨线程命令分发 |

## 优先级体系（P0 最高）

| 级别 | 名称 | 触发 | 示例 |
|------|------|------|------|
| P0 | SAFETY | 危险车速 >120km/h | 抓握+惊恐+警告灯 |
| P1 | AI_COMMAND | 用户 AI 交互 | 说话表情+导航指引 |
| P2 | DRIVING_REACT | 急转/急刹 | 指向+皱眉+颤抖 |
| P3 | SPEED_AMBIENT | 速度区间氛围 | 专注/微笑 |
| P4 | SCENE | 时间/环境场景 | 打盹/充电 |
| P5 | IDLE | 随机空闲 | 挥手/托腮 |

## RobotState 枚举速查

| 枚举 | 值 |
|------|------|
| Expression | IDLE, EXCITED, SURPRISED |
| ArmPose | IDLE_SIDE, WAVE, BOTH_UP, POINT_LEFT, POINT_RIGHT, GRAB_HOLD, THINKING, CLAP, WIPE_SWEAT |
| MouthShape | NEUTRAL, SMILE, WIDE_SMILE, OPEN_O, OPEN_D, FLAT, WAVY |
| EyebrowState | NEUTRAL, RAISED, FURROWED, ONE_UP, SAD, ANGRY |
| EyeSpecial | NONE, SPARKLE, DIZZY, HEART, SLEEPY |
| BodyAction | NONE, NOD, SHAKE_HEAD, BOUNCE, SHIVER, TILT, YAWN, STRETCH |
| ThoughtBubbleType | NONE, DOTS, QUESTION, EXCLAMATION, HEART_BUBBLE, MUSIC_NOTE, ZZZ, SWEAT, ANGRY_MARK, SPARKLE_BURST, LOADING |
| IndicatorState | NORMAL, BREATHING, WARNING, ERROR, AI_ACTIVE |
| ChestMode | NORMAL, CHARGING, QR_CODE |
| AIEmotion | NEUTRAL, HAPPY, EXCITED, CURIOUS, CONFUSED, SORRY, WORRIED, PROUD, SHY, SLEEPY_REPLY |
| SpeedZone | PARKED, CITY, HIGHWAY, OVER_SPEED, DANGER |
| DrivingMode | PARKED, CITY_CRUISE, HIGHWAY_CRUISE, SPORT, ECO |

## 与 Launcher3 核心的集成

唯一集成文件: `src/com/android/launcher3/Launcher.java` (+86 行)

```java
import com.android.launcher3.robot.RobotPageView;
// RobotPageView 作为 DragLayer Overlay 叠加在 Workspace 之上
// 第一屏（pageIndex==0）时 setActive(true)，其它页 setActive(false)
// 触摸事件完全穿透，不影响桌面滑动
```

其它修改:
- `states/RotationHelper.java`: 强制竖屏 `SCREEN_ORIENTATION_PORTRAIT`
- `quickstep/AndroidManifest.xml`: +GPS 权限 (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)

## 调试

```bash
# 启用 Demo 模式（30秒循环所有动画）
adb -s 3b7c279b shell setprop persist.launcher.robot_demo 1

# 关闭 Demo 模式
adb -s 3b7c279b shell setprop persist.launcher.robot_demo 0
```

## 注意事项

- **CatRenderer.java 保护**: 曾被失控代理反复覆盖。并行编辑时先 `chmod 444` 保护
- **RobotState 是可变对象**: 仅在物理引擎线程修改，渲染线程通过快照读取
- **所有角度单位为度(°)**: 坐标单位为像素(px)，dp 在渲染时转换
- **Demo 模式属性检查有 2 秒延迟**: 避免每帧读取 SystemProperties

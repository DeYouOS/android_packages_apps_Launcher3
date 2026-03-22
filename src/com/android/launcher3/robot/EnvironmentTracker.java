package com.android.launcher3.robot;

import java.util.Calendar;

/**
 * 环境状态追踪器
 *
 * <h2>核心职责</h2>
 * 追踪与动画系统相关的环境状态信息，包括时间（昼夜）、充电状态、
 * 闲置/驾驶持续时长、驾驶模式分类以及困倦因子计算。
 * 为场景层（P4 SCENE）和闲置层（P5 IDLE）的动画决策提供输入。
 *
 * <h2>驾驶模式分类</h2>
 * <ul>
 *   <li>SLEEPING — 闲置超过 60 秒且未充电</li>
 *   <li>PARKED — 速度为 0 且闲置超过 3 秒</li>
 *   <li>STARTING — 从 PARKED 过渡到有速度，持续 1 秒</li>
 *   <li>CRUISING — 有速度且合力 &lt; 0.15（平稳行驶）</li>
 *   <li>CORNERING — |横向力| &gt; 0.3（过弯中）</li>
 *   <li>BUMPY — 垂直力方差超过阈值（颠簸路面）</li>
 *   <li>REVERSING — 需要挡位信息，暂时归入 CRUISING</li>
 * </ul>
 *
 * <h2>困倦因子计算</h2>
 * 综合夜间时段（+0.3）、长时间闲置（最高 +0.5）和长时间驾驶（+0.2）
 * 计算出 [0,1] 的困倦程度值，用于驱动困倦相关的动画效果。
 *
 * <h2>线程安全</h2>
 * 本类非线程安全，应在单一线程（物理引擎线程）中调用 update()，
 * setCharging()、setLateralForce()、setVerticalForce() 可从其他线程调用
 * （均为简单赋值操作，使用 volatile 保证可见性）。
 */
public class EnvironmentTracker {

    // ==================== 阈值常量 ====================

    /** 判定为闲置的合力阈值：合力低于此值视为无运动 */
    private static final float IDLE_FORCE_THRESHOLD = 0.05f;

    /** 触发 SLEEPING 模式的闲置时长阈值（秒） */
    private static final float SLEEPING_IDLE_THRESHOLD = 60.0f;

    /** 触发 PARKED 模式的闲置时长阈值（秒） */
    private static final float PARKED_IDLE_THRESHOLD = 3.0f;

    /** STARTING 模式固定持续时长（秒） */
    private static final float STARTING_DURATION = 1.0f;

    /** 判定为 CRUISING 的合力阈值：合力低于此值视为平稳 */
    private static final float CRUISING_FORCE_THRESHOLD = 0.15f;

    /** 判定为 CORNERING 的横向力阈值 */
    private static final float CORNERING_LATERAL_THRESHOLD = 0.3f;

    /** 判定为 BUMPY 的垂直力方差阈值 */
    private static final float BUMPY_VARIANCE_THRESHOLD = 0.05f;

    /** 垂直力采样缓冲区大小（用于计算滚动方差） */
    private static final int VERTICAL_BUFFER_SIZE = 20;

    /** 驾驶模式最小保持时间（秒），防止模式快速震荡（STARTING 除外） */
    private static final float MODE_HOLD_TIME = 1.0f;

    /** 夜间时段开始小时（22:00） */
    private static final int NIGHT_START_HOUR = 22;

    /** 夜间时段结束小时（06:00） */
    private static final int NIGHT_END_HOUR = 6;

    /** 困倦计算：开始累积的闲置时长阈值（秒） */
    private static final float DROWSY_IDLE_START = 30.0f;

    /** 困倦计算：闲置满量程时长（秒），闲置 90 秒时困倦贡献达到上限 */
    private static final float DROWSY_IDLE_FULL = 60.0f;

    /** 困倦计算：长时间驾驶阈值（秒），2 小时 */
    private static final float DROWSY_DRIVING_THRESHOLD = 7200.0f;

    // ==================== 内部状态 ====================

    /** 连续闲置持续时间（秒），速度为 0 且合力低于阈值时累加 */
    private float idleDuration;

    /** 连续驾驶持续时间（秒），速度 > 0 时累加 */
    private float drivingDuration;

    /** 充电状态标志，volatile 保证跨线程可见性 */
    private volatile boolean charging;

    /** 当前驾驶模式 */
    private RobotState.DrivingMode currentMode;

    /** STARTING 模式倒计时器（秒） */
    private float startingTimer;

    /** 驾驶模式保持计时器（秒），大于 0 时锁定当前模式 */
    private float modeHoldTimer;

    /** 外部设置的当前横向力值，volatile 保证跨线程可见性 */
    private volatile float lateralForce;

    /** 垂直力采样环形缓冲区 */
    private final float[] verticalBuffer;

    /** 环形缓冲区当前写入位置 */
    private int verticalBufferIndex;

    /** 环形缓冲区已填充的样本数量（最大为 VERTICAL_BUFFER_SIZE） */
    private int verticalBufferCount;

    /** 垂直力采样的滚动方差 */
    private float verticalVariance;

    /**
     * 构造函数
     *
     * 初始化为 PARKED 模式，所有计时器和传感器数据清零。
     */
    public EnvironmentTracker() {
        idleDuration = 0f;
        drivingDuration = 0f;
        charging = false;
        currentMode = RobotState.DrivingMode.PARKED;
        startingTimer = 0f;
        modeHoldTimer = 0f;
        lateralForce = 0f;
        verticalBuffer = new float[VERTICAL_BUFFER_SIZE];
        verticalBufferIndex = 0;
        verticalBufferCount = 0;
        verticalVariance = 0f;
    }

    /**
     * 每帧更新环境状态
     *
     * 更新闲置/驾驶计时器，并根据当前运动参数分类驾驶模式。
     * 应在每个物理帧调用一次。
     *
     * @param dt         距上一帧的时间间隔（秒）
     * @param totalForce 当前运动合力 [0,1+]，由传感器管理器提供
     * @param speedKmh   当前车速 (km/h)，由 SpeedTracker 提供
     */
    public void update(float dt, float totalForce, float speedKmh) {
        if (dt <= 0f) {
            return;
        }

        // 更新闲置/驾驶计时器
        if (speedKmh <= 0f && totalForce < IDLE_FORCE_THRESHOLD) {
            idleDuration += dt;
            // 开始闲置时重置驾驶计时
            drivingDuration = 0f;
        } else if (speedKmh > 0f) {
            drivingDuration += dt;
            // 开始驾驶时重置闲置计时
            idleDuration = 0f;
        }

        // 更新模式保持计时器
        if (modeHoldTimer > 0f) {
            modeHoldTimer -= dt;
        }

        // 更新 STARTING 倒计时
        if (startingTimer > 0f) {
            startingTimer -= dt;
        }

        // 分类驾驶模式
        RobotState.DrivingMode newMode = classifyMode(totalForce, speedKmh);

        // 应用模式保持逻辑（STARTING 模式不受保持时间限制，固定 1 秒）
        if (newMode != currentMode) {
            if (currentMode == RobotState.DrivingMode.STARTING && startingTimer > 0f) {
                // STARTING 模式未结束，保持不变
                return;
            }
            if (modeHoldTimer <= 0f) {
                currentMode = newMode;
                modeHoldTimer = MODE_HOLD_TIME;
            }
        }
    }

    /**
     * 获取当前小时（24 小时制）
     *
     * @return 当前小时 (0-23)
     */
    public int getHourOfDay() {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 判断当前是否为夜间时段（22:00 - 06:00）
     *
     * @return true 如果当前处于夜间时段
     */
    public boolean isNightTime() {
        int hour = getHourOfDay();
        // 夜间时段跨越午夜：22:00~23:59 或 00:00~05:59
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }

    /**
     * 获取连续闲置持续时间
     *
     * 当车辆速度为 0 且合力低于阈值时持续累加，
     * 一旦开始移动立即重置为 0。
     *
     * @return 闲置持续时间（秒）
     */
    public float getIdleDuration() {
        return idleDuration;
    }

    /**
     * 获取连续驾驶持续时间
     *
     * 当车速 &gt; 0 时持续累加，停车后重置为 0。
     *
     * @return 驾驶持续时间（秒）
     */
    public float getDrivingDuration() {
        return drivingDuration;
    }

    /**
     * 设置充电状态
     *
     * 由外部系统（如 BroadcastReceiver）在充电状态变化时调用。
     * volatile 变量，跨线程安全。
     *
     * @param charging true 表示正在充电
     */
    public void setCharging(boolean charging) {
        this.charging = charging;
    }

    /**
     * 获取当前充电状态
     *
     * @return true 如果设备正在充电
     */
    public boolean isCharging() {
        return charging;
    }

    /**
     * 获取当前驾驶模式
     *
     * 基于速度、合力、横向力和垂直力方差综合判定。
     *
     * @return 当前驾驶模式枚举值
     */
    public RobotState.DrivingMode getDrivingMode() {
        return currentMode;
    }

    /**
     * 计算困倦因子
     *
     * 综合三个因素线性叠加并 clamp 到 [0, 1]：
     * <ol>
     *   <li>夜间时段（22:00-06:00）：+0.3</li>
     *   <li>长时间闲置（30s 开始累积，90s 达上限）：最高 +0.5</li>
     *   <li>长时间连续驾驶（超过 2 小时）：+0.2</li>
     * </ol>
     *
     * @return 困倦因子 [0, 1]，值越大越困倦
     */
    public float getDrowsiness() {
        float base = 0f;

        // 夜间时段加成
        if (isNightTime()) {
            base += 0.3f;
        }

        // 长时间闲置加成：30 秒开始累积，在 30~90 秒区间内线性增长到 0.5
        if (idleDuration > DROWSY_IDLE_START) {
            float idleContribution = (idleDuration - DROWSY_IDLE_START) / DROWSY_IDLE_FULL;
            if (idleContribution > 0.5f) {
                idleContribution = 0.5f;
            }
            base += idleContribution;
        }

        // 长时间驾驶加成：超过 2 小时连续驾驶
        if (drivingDuration > DROWSY_DRIVING_THRESHOLD) {
            base += 0.2f;
        }

        // Clamp 到 [0, 1]
        if (base < 0f) {
            base = 0f;
        }
        if (base > 1.0f) {
            base = 1.0f;
        }
        return base;
    }

    /**
     * 设置当前横向力
     *
     * 由传感器管理器在每帧或传感器事件时调用。
     * volatile 变量，跨线程安全。
     *
     * @param force 横向力值 [-1, 1]，负值为左转，正值为右转
     */
    public void setLateralForce(float force) {
        this.lateralForce = force;
    }

    /**
     * 设置当前垂直力并更新滚动方差
     *
     * 将新样本写入环形缓冲区，并重新计算缓冲区内所有样本的方差。
     * 方差值用于判定颠簸路面（BUMPY 模式）。
     *
     * @param force 垂直力值，归一化到 [-1, 1]
     */
    public void setVerticalForce(float force) {
        // 写入环形缓冲区
        verticalBuffer[verticalBufferIndex] = force;
        verticalBufferIndex = (verticalBufferIndex + 1) % VERTICAL_BUFFER_SIZE;
        if (verticalBufferCount < VERTICAL_BUFFER_SIZE) {
            verticalBufferCount++;
        }

        // 计算滚动方差
        verticalVariance = computeVariance();
    }

    /**
     * 分类当前驾驶模式
     *
     * 优先级：SLEEPING > PARKED > STARTING > CORNERING > BUMPY > CRUISING
     * 其中 STARTING 是从 PARKED 到有速度的过渡状态，固定持续 1 秒。
     * REVERSING 需要挡位信息，暂时归入 CRUISING。
     *
     * @param totalForce 运动合力
     * @param speedKmh   车速 (km/h)
     * @return 分类后的驾驶模式
     */
    private RobotState.DrivingMode classifyMode(float totalForce, float speedKmh) {
        // SLEEPING：长时间闲置且未充电
        if (idleDuration > SLEEPING_IDLE_THRESHOLD && !charging) {
            return RobotState.DrivingMode.SLEEPING;
        }

        // PARKED：速度为 0 且闲置超过短阈值
        if (speedKmh <= 0f && idleDuration > PARKED_IDLE_THRESHOLD) {
            return RobotState.DrivingMode.PARKED;
        }

        // STARTING：从 PARKED 转为有速度的过渡，触发 1 秒过渡动画
        if (currentMode == RobotState.DrivingMode.PARKED && speedKmh > 0f) {
            startingTimer = STARTING_DURATION;
            return RobotState.DrivingMode.STARTING;
        }

        // STARTING 模式未结束时保持
        if (currentMode == RobotState.DrivingMode.STARTING && startingTimer > 0f) {
            return RobotState.DrivingMode.STARTING;
        }

        // 以下为行驶中的模式分类（speed > 0）

        // CORNERING：横向力超过阈值
        float absLateral = lateralForce < 0f ? -lateralForce : lateralForce;
        if (absLateral > CORNERING_LATERAL_THRESHOLD) {
            return RobotState.DrivingMode.CORNERING;
        }

        // BUMPY：垂直力方差超过阈值
        if (verticalVariance > BUMPY_VARIANCE_THRESHOLD && verticalBufferCount >= VERTICAL_BUFFER_SIZE) {
            return RobotState.DrivingMode.BUMPY;
        }

        // CRUISING：有速度且合力较低的平稳行驶（也包含 REVERSING 暂归入此处）
        if (speedKmh > 0f) {
            return RobotState.DrivingMode.CRUISING;
        }

        // 兜底：速度为 0 但闲置未超过 PARKED 阈值
        return RobotState.DrivingMode.PARKED;
    }

    /**
     * 计算垂直力采样缓冲区的方差
     *
     * 方差 = E[X²] - (E[X])²，使用在线算法对缓冲区内所有有效样本计算。
     * 方差越大表示路面越颠簸。
     *
     * @return 缓冲区样本的方差值，样本不足时返回 0
     */
    private float computeVariance() {
        if (verticalBufferCount < 2) {
            return 0f;
        }

        // 计算均值
        float sum = 0f;
        for (int i = 0; i < verticalBufferCount; i++) {
            sum += verticalBuffer[i];
        }
        float mean = sum / verticalBufferCount;

        // 计算方差：Σ(xi - mean)² / n
        float variance = 0f;
        for (int i = 0; i < verticalBufferCount; i++) {
            float diff = verticalBuffer[i] - mean;
            variance += diff * diff;
        }
        return variance / verticalBufferCount;
    }
}

package com.android.launcher3.robot;

/**
 * GPS 速度追踪与速度区间分类器
 *
 * <h2>核心职责</h2>
 * 接收原始 GPS 速度读数，经低通滤波平滑后进行速度区间分类。
 * 使用滞回（hysteresis）机制避免在区间边界来回抖动，并提供
 * 加速度计算和区间变化检测，供动画系统驱动速度相关的氛围效果。
 *
 * <h2>速度区间定义</h2>
 * <ul>
 *   <li>PARKED — 0 km/h（静止）</li>
 *   <li>CITY — 进入 &gt;2 km/h，退出 &lt;1 km/h</li>
 *   <li>NORMAL — 进入 &gt;42 km/h，退出 &lt;38 km/h</li>
 *   <li>HIGHWAY — 进入 &gt;82 km/h，退出 &lt;78 km/h</li>
 *   <li>OVER_LIMIT — 进入 &gt;122 km/h，退出 &lt;118 km/h</li>
 *   <li>DANGER — 进入 &gt;142 km/h，退出 &lt;138 km/h</li>
 * </ul>
 *
 * <h2>滤波与平滑</h2>
 * 使用 RC = 0.5s 的一阶低通滤波器消除 GPS 速度抖动：
 * {@code alpha = dt / (RC + dt)}，
 * {@code filteredSpeed = alpha * raw + (1 - alpha) * filteredSpeed}。
 *
 * <h2>线程安全</h2>
 * 本类非线程安全，应在单一线程（物理引擎线程）中调用 update()，
 * 其他线程通过快照读取结果。
 */
public class SpeedTracker {

    // ==================== 滞回阈值常量 ====================
    // 每个区间定义 [进入阈值, 退出阈值]

    /** CITY 区间进入阈值 (km/h) */
    private static final float CITY_ENTER = 2.0f;

    /** CITY 区间退出阈值 (km/h) */
    private static final float CITY_EXIT = 1.0f;

    /** NORMAL 区间进入阈值 (km/h) */
    private static final float NORMAL_ENTER = 42.0f;

    /** NORMAL 区间退出阈值 (km/h) */
    private static final float NORMAL_EXIT = 38.0f;

    /** HIGHWAY 区间进入阈值 (km/h) */
    private static final float HIGHWAY_ENTER = 82.0f;

    /** HIGHWAY 区间退出阈值 (km/h) */
    private static final float HIGHWAY_EXIT = 78.0f;

    /** OVER_LIMIT 区间进入阈值 (km/h) */
    private static final float OVER_LIMIT_ENTER = 122.0f;

    /** OVER_LIMIT 区间退出阈值 (km/h) */
    private static final float OVER_LIMIT_EXIT = 118.0f;

    /** DANGER 区间进入阈值 (km/h) */
    private static final float DANGER_ENTER = 142.0f;

    /** DANGER 区间退出阈值 (km/h) */
    private static final float DANGER_EXIT = 138.0f;

    // ==================== 滤波参数 ====================

    /** 低通滤波器 RC 时间常数（秒），值越大平滑效果越强 */
    private static final float FILTER_RC = 0.5f;

    /** 区间切换最小保持时间（秒），防止快速震荡 */
    private static final float ZONE_HOLD_TIME = 1.0f;

    // ==================== 内部状态 ====================

    /** 低通滤波后的当前速度 (km/h) */
    private float filteredSpeed;

    /** 上一帧的滤波速度 (km/h)，用于加速度计算 */
    private float prevSpeed;

    /** 当前加速度 (km/h/s)，正值为加速，负值为减速 */
    private float acceleration;

    /** 当前所在速度区间 */
    private RobotState.SpeedZone currentZone;

    /** 上一次的速度区间，用于区间变化检测 */
    private RobotState.SpeedZone prevZone;

    /** 区间保持计时器（秒），大于 0 时锁定当前区间不允许切换 */
    private float zoneHoldTimer;

    /** 本帧是否发生了区间切换 */
    private boolean zoneChanged;

    /**
     * 构造函数
     *
     * 初始化为 PARKED 区间，速度和加速度均为零。
     */
    public SpeedTracker() {
        filteredSpeed = 0f;
        prevSpeed = 0f;
        acceleration = 0f;
        currentZone = RobotState.SpeedZone.PARKED;
        prevZone = RobotState.SpeedZone.PARKED;
        zoneHoldTimer = 0f;
        zoneChanged = false;
    }

    /**
     * 更新速度跟踪状态
     *
     * 接收最新的 GPS 速度读数，进行低通滤波、加速度计算和区间分类。
     * 应在每个物理帧调用一次。
     *
     * @param speedKmh 原始 GPS 速度读数 (km/h)，不允许负值
     * @param dt       距上一帧的时间间隔（秒），必须大于 0
     * @return 当前所在的速度区间
     */
    public RobotState.SpeedZone update(float speedKmh, float dt) {
        // 保护性处理：负速度视为 0
        if (speedKmh < 0f) {
            speedKmh = 0f;
        }
        // 保护性处理：dt 必须有效
        if (dt <= 0f) {
            return currentZone;
        }

        // 保存上一帧的滤波速度
        prevSpeed = filteredSpeed;

        // 一阶低通滤波：alpha = dt / (RC + dt)
        float alpha = dt / (FILTER_RC + dt);
        filteredSpeed = alpha * speedKmh + (1.0f - alpha) * filteredSpeed;

        // 计算加速度 (km/h/s)
        acceleration = (filteredSpeed - prevSpeed) / dt;

        // 更新区间保持计时器
        if (zoneHoldTimer > 0f) {
            zoneHoldTimer -= dt;
        }

        // 执行带滞回的区间分类
        RobotState.SpeedZone newZone = classifyZone(filteredSpeed);

        // 检测区间变化
        zoneChanged = false;
        if (newZone != currentZone && zoneHoldTimer <= 0f) {
            prevZone = currentZone;
            currentZone = newZone;
            zoneHoldTimer = ZONE_HOLD_TIME;
            zoneChanged = true;
        }

        return currentZone;
    }

    /**
     * 获取当前速度
     *
     * 返回低通滤波后的平滑速度值，而非原始 GPS 读数。
     *
     * @return 当前滤波速度 (km/h)
     */
    public float getSpeedKmh() {
        return filteredSpeed;
    }

    /**
     * 获取当前加速度
     *
     * 基于相邻两帧滤波速度差计算的速度变化率。
     * 正值表示加速，负值表示减速。
     *
     * @return 加速度 (km/h/s)
     */
    public float getAcceleration() {
        return acceleration;
    }

    /**
     * 获取当前速度区间
     *
     * @return 当前所在的速度区间枚举值
     */
    public RobotState.SpeedZone getSpeedZone() {
        return currentZone;
    }

    /**
     * 检查本帧是否发生了速度区间切换
     *
     * 用于触发区间切换时的过渡动画。每次 update() 后有效，
     * 下一次 update() 会重置。
     *
     * @return true 如果本帧发生了区间切换
     */
    public boolean hasZoneChanged() {
        return zoneChanged;
    }

    /**
     * 获取上一个速度区间
     *
     * 配合 hasZoneChanged() 使用，用于确定区间切换方向
     * （例如从 CITY 进入 NORMAL 还是从 HIGHWAY 退回 NORMAL）。
     *
     * @return 切换前的速度区间
     */
    public RobotState.SpeedZone getPreviousZone() {
        return prevZone;
    }

    /**
     * 基于滞回阈值的速度区间分类
     *
     * 使用双阈值机制：从低区间向高区间切换需要超过进入阈值，
     * 从高区间向低区间切换需要低于退出阈值。
     * 进入阈值 > 退出阈值，形成滞回区域，避免在边界来回震荡。
     *
     * 分类策略：先判断最高区间再逐级降低，
     * 同时考虑当前所在区间的退出条件。
     *
     * @param speed 滤波后的速度 (km/h)
     * @return 分类后的速度区间
     */
    private RobotState.SpeedZone classifyZone(float speed) {
        // 获取当前区间的序号，用于判断是"上行"还是"下行"穿越
        int currentOrdinal = zoneOrdinal(currentZone);

        // DANGER 区间判定
        int dangerOrd = zoneOrdinal(RobotState.SpeedZone.DANGER);
        if (speed > DANGER_ENTER && currentOrdinal < dangerOrd) {
            return RobotState.SpeedZone.DANGER;
        }
        if (currentOrdinal == dangerOrd && speed >= DANGER_EXIT) {
            return RobotState.SpeedZone.DANGER;
        }

        // OVER_LIMIT 区间判定
        int overLimitOrd = zoneOrdinal(RobotState.SpeedZone.OVER_LIMIT);
        if (speed > OVER_LIMIT_ENTER && currentOrdinal < overLimitOrd) {
            return RobotState.SpeedZone.OVER_LIMIT;
        }
        if (currentOrdinal == overLimitOrd && speed >= OVER_LIMIT_EXIT) {
            return RobotState.SpeedZone.OVER_LIMIT;
        }

        // HIGHWAY 区间判定
        int highwayOrd = zoneOrdinal(RobotState.SpeedZone.HIGHWAY);
        if (speed > HIGHWAY_ENTER && currentOrdinal < highwayOrd) {
            return RobotState.SpeedZone.HIGHWAY;
        }
        if (currentOrdinal == highwayOrd && speed >= HIGHWAY_EXIT) {
            return RobotState.SpeedZone.HIGHWAY;
        }

        // NORMAL 区间判定
        int normalOrd = zoneOrdinal(RobotState.SpeedZone.NORMAL);
        if (speed > NORMAL_ENTER && currentOrdinal < normalOrd) {
            return RobotState.SpeedZone.NORMAL;
        }
        if (currentOrdinal == normalOrd && speed >= NORMAL_EXIT) {
            return RobotState.SpeedZone.NORMAL;
        }

        // CITY 区间判定
        int cityOrd = zoneOrdinal(RobotState.SpeedZone.CITY);
        if (speed > CITY_ENTER && currentOrdinal < cityOrd) {
            return RobotState.SpeedZone.CITY;
        }
        if (currentOrdinal == cityOrd && speed >= CITY_EXIT) {
            return RobotState.SpeedZone.CITY;
        }

        // 默认：速度极低或处于下行过程中降到最低
        return RobotState.SpeedZone.PARKED;
    }

    /**
     * 将 SpeedZone 枚举映射为有序序号
     *
     * 用于比较当前区间与目标区间的高低关系，
     * 序号越大表示速度越高的区间。
     *
     * @param zone 速度区间枚举
     * @return 有序序号 (0=PARKED, 1=CITY, ..., 5=DANGER)
     */
    private int zoneOrdinal(RobotState.SpeedZone zone) {
        if (zone == RobotState.SpeedZone.PARKED) return 0;
        if (zone == RobotState.SpeedZone.CITY) return 1;
        if (zone == RobotState.SpeedZone.NORMAL) return 2;
        if (zone == RobotState.SpeedZone.HIGHWAY) return 3;
        if (zone == RobotState.SpeedZone.OVER_LIMIT) return 4;
        if (zone == RobotState.SpeedZone.DANGER) return 5;
        return 0;
    }
}

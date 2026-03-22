package com.android.launcher3.robot;

/**
 * 车载运动状态数据类（不可变）
 *
 * 封装经过滤波处理后的传感器和 GPS 输出数据，供渲染线程无锁读取。
 * 所有力的值均归一化到 [-1, 1] 区间，方便动画系统直接映射。
 *
 * 该类为不可变对象（immutable），线程安全，可在传感器线程写入后
 * 由渲染线程安全读取，无需额外同步机制。
 */
public final class CarMotionState {

    /** 横向力：-1 表示急左转（离心力向右），+1 表示急右转（离心力向左） */
    public final float lateralForce;

    /** 纵向力：-1 表示急刹车（惯性向前），+1 表示急加速（惯性向后） */
    public final float longitudinalForce;

    /** 垂直力：预留用于颠簸检测，-1 表示下陷，+1 表示隆起 */
    public final float verticalForce;

    /** 时间戳（纳秒），对应传感器事件的时间 */
    public final long timestamp;

    /** GPS 时速（km/h），来自定位服务的实时行驶速度 */
    public final float speedKmh;

    /** 速度变化率（km/h/s），正值为加速，负值为减速，用于检测急加速/急刹车 */
    public final float speedAcceleration;

    /** GPS 航向角（度）[0, 360)，0 = 正北，90 = 正东，180 = 正南，270 = 正西 */
    public final float bearing;

    /** GPS 是否可用，false 表示信号丢失或未授权，速度数据不可靠 */
    public final boolean gpsAvailable;

    /**
     * 默认构造函数
     *
     * 创建一个所有力均为零、GPS 数据为默认值的静止状态。
     * 适用于初始化阶段，表示车辆静止或传感器尚未产生有效数据。
     */
    public CarMotionState() {
        this(0f, 0f, 0f, 0L, 0f, 0f, 0f, false);
    }

    /**
     * 力学参数构造函数（向后兼容）
     *
     * 仅指定力学和时间戳参数，GPS 相关字段使用默认值。
     *
     * @param lateralForce      横向力，范围 [-1, 1]，负值为左转，正值为右转
     * @param longitudinalForce 纵向力，范围 [-1, 1]，负值为刹车，正值为加速
     * @param verticalForce     垂直力，范围 [-1, 1]，预留颠簸检测
     * @param timestamp         传感器事件时间戳（纳秒）
     */
    public CarMotionState(float lateralForce, float longitudinalForce,
                          float verticalForce, long timestamp) {
        this(lateralForce, longitudinalForce, verticalForce, timestamp,
                0f, 0f, 0f, false);
    }

    /**
     * 全参数构造函数
     *
     * 同时指定力学参数和 GPS 数据，用于传感器与定位数据融合后的完整状态。
     *
     * @param lateralForce      横向力，范围 [-1, 1]，负值为左转，正值为右转
     * @param longitudinalForce 纵向力，范围 [-1, 1]，负值为刹车，正值为加速
     * @param verticalForce     垂直力，范围 [-1, 1]，预留颠簸检测
     * @param timestamp         传感器事件时间戳（纳秒）
     * @param speedKmh          GPS 时速（km/h）
     * @param speedAcceleration 速度变化率（km/h/s），正值加速，负值减速
     * @param bearing           GPS 航向角（度），范围 [0, 360)
     * @param gpsAvailable      GPS 信号是否可用
     */
    public CarMotionState(float lateralForce, float longitudinalForce,
                          float verticalForce, long timestamp,
                          float speedKmh, float speedAcceleration,
                          float bearing, boolean gpsAvailable) {
        this.lateralForce = lateralForce;
        this.longitudinalForce = longitudinalForce;
        this.verticalForce = verticalForce;
        this.timestamp = timestamp;
        this.speedKmh = speedKmh;
        this.speedAcceleration = speedAcceleration;
        this.bearing = bearing;
        this.gpsAvailable = gpsAvailable;
    }

    /**
     * 计算合力大小
     *
     * 使用横向力和纵向力的欧几里得范数计算总力大小，
     * 可用于判断车辆运动的剧烈程度（如触发表情变化）。
     *
     * @return 合力大小，范围 [0, √2]，通常不超过 1.414
     */
    public float getTotalForce() {
        return (float) Math.sqrt(lateralForce * lateralForce
                + longitudinalForce * longitudinalForce);
    }

    @Override
    public String toString() {
        return "CarMotionState{"
                + "lateral=" + lateralForce
                + ", longitudinal=" + longitudinalForce
                + ", vertical=" + verticalForce
                + ", ts=" + timestamp
                + ", speedKmh=" + speedKmh
                + ", speedAccel=" + speedAcceleration
                + ", bearing=" + bearing
                + ", gps=" + gpsAvailable
                + '}';
    }
}

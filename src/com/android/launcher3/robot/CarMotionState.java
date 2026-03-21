package com.android.launcher3.robot;

/**
 * 车载运动状态数据类（不可变）
 *
 * 封装经过滤波处理后的传感器输出数据，供渲染线程无锁读取。
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

    /**
     * 默认构造函数
     *
     * 创建一个所有力均为零的静止状态，时间戳为 0。
     * 适用于初始化阶段，表示车辆静止或传感器尚未产生有效数据。
     */
    public CarMotionState() {
        this.lateralForce = 0f;
        this.longitudinalForce = 0f;
        this.verticalForce = 0f;
        this.timestamp = 0L;
    }

    /**
     * 参数化构造函数
     *
     * @param lateralForce      横向力，范围 [-1, 1]，负值为左转，正值为右转
     * @param longitudinalForce 纵向力，范围 [-1, 1]，负值为刹车，正值为加速
     * @param verticalForce     垂直力，范围 [-1, 1]，预留颠簸检测
     * @param timestamp         传感器事件时间戳（纳秒）
     */
    public CarMotionState(float lateralForce, float longitudinalForce,
                          float verticalForce, long timestamp) {
        this.lateralForce = lateralForce;
        this.longitudinalForce = longitudinalForce;
        this.verticalForce = verticalForce;
        this.timestamp = timestamp;
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
                + '}';
    }
}

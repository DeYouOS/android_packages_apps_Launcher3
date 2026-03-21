package com.android.launcher3.robot;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

/**
 * 车载传感器管理器（单例）
 *
 * 传感器数据处理管线：加速度计 + 陀螺仪 → 低通滤波 → 互补滤波 → 死区过滤 → CarMotionState
 *
 * 设计要点：
 * - 采样率：SENSOR_DELAY_GAME（约 50Hz），兼顾精度与功耗
 * - 低通滤波：RC=0.3s，截止频率约 0.53Hz，滤除 >5Hz 的车身高频振动，
 *   保留 0.5~2Hz 的转弯/刹车有效信号
 * - 互补滤波：α=0.96（陀螺仪主导，加速度计修正漂移），比卡尔曼滤波轻量，
 *   适合嵌入式实时场景
 * - 死区：加速度 0.15 m/s²、陀螺仪 0.02 rad/s，消除静止时的传感器噪声
 * - 输出为 volatile CarMotionState，渲染线程可无锁读取（引用赋值的原子性）
 * - 坐标映射：手机横屏车载时，Y 轴加速度对应左右转弯，X 轴对应刹车/加速
 */
public class CarSensorManager implements SensorEventListener {

    /** 传感器采样率：GAME 级别约 50Hz，满足动画流畅度需求 */
    private static final int SENSOR_RATE = SensorManager.SENSOR_DELAY_GAME;

    /**
     * 低通滤波 RC 时间常数（秒）
     * RC=0.3 对应截止频率 fc = 1/(2π*RC) ≈ 0.53Hz
     * 有效滤除车身高频振动（>5Hz），保留转弯信号（0.5~2Hz）
     */
    private static final float LOW_PASS_RC = 0.3f;

    /**
     * 互补滤波系数：陀螺仪权重
     * α=0.96 表示 96% 信任陀螺仪短期精度，4% 用加速度计修正长期漂移
     */
    private static final float COMPLEMENTARY_ALPHA = 0.96f;

    /** 加速度计死区阈值（m/s²），低于此值视为静止噪声 */
    private static final float ACCEL_DEAD_ZONE = 0.15f;

    /** 陀螺仪死区阈值（rad/s），低于此值视为静止噪声 */
    private static final float GYRO_DEAD_ZONE = 0.02f;

    /** 加速度归一化最大值（m/s²），对应输出 ±1.0 */
    private static final float MAX_ACCEL = 9.81f;

    /** 陀螺仪归一化最大值（rad/s），对应输出 ±1.0 */
    private static final float MAX_GYRO = 3.0f;

    /** 单例实例，使用 volatile 保证多线程可见性 */
    private static volatile CarSensorManager sInstance;

    /** 系统传感器管理器 */
    private final SensorManager mSensorManager;

    /** 窗口管理器，用于获取屏幕旋转状态 */
    private final WindowManager mWindowManager;

    /** 加速度计传感器 */
    private final Sensor mAccelerometer;

    /** 陀螺仪传感器 */
    private final Sensor mGyroscope;

    /**
     * 当前运动状态输出
     * volatile 保证渲染线程读取到最新值（Java 引用赋值是原子操作）
     */
    private volatile CarMotionState mCurrentState = new CarMotionState();

    /** 传感器监听是否已注册 */
    private boolean mIsRunning = false;

    // ---- 低通滤波状态 ----
    /** 低通滤波后的加速度值（X/Y/Z 三轴） */
    private final float[] mFilteredAccel = new float[3];

    /** 低通滤波后的陀螺仪值（X/Y/Z 三轴） */
    private final float[] mFilteredGyro = new float[3];

    /** 上一次加速度计事件时间戳（纳秒） */
    private long mLastAccelTimestamp = 0;

    /** 上一次陀螺仪事件时间戳（纳秒） */
    private long mLastGyroTimestamp = 0;

    // ---- 互补滤波状态 ----
    /** 互补滤波融合后的横向力（归一化 [-1, 1]） */
    private float mFusedLateral = 0f;

    /** 互补滤波融合后的纵向力（归一化 [-1, 1]） */
    private float mFusedLongitudinal = 0f;

    /**
     * 私有构造函数（单例模式）
     *
     * @param context 应用上下文，用于获取系统服务
     */
    private CarSensorManager(Context context) {
        Context appContext = context.getApplicationContext();
        mSensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        mWindowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    /**
     * 获取单例实例
     *
     * 使用双重检查锁定（DCL）保证线程安全且避免不必要的同步开销。
     *
     * @param context 任意 Context，内部会自动取 ApplicationContext 避免内存泄漏
     * @return CarSensorManager 单例
     */
    public static CarSensorManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (CarSensorManager.class) {
                if (sInstance == null) {
                    sInstance = new CarSensorManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * 启动传感器监听
     *
     * 注册加速度计和陀螺仪监听器，开始数据采集和滤波处理。
     * 重复调用安全（会先检查是否已在运行）。
     */
    public void start() {
        if (mIsRunning) {
            return;
        }
        if (mAccelerometer != null) {
            mSensorManager.registerListener(this, mAccelerometer, SENSOR_RATE);
        }
        if (mGyroscope != null) {
            mSensorManager.registerListener(this, mGyroscope, SENSOR_RATE);
        }
        mIsRunning = true;
    }

    /**
     * 停止传感器监听
     *
     * 注销所有传感器监听器，停止数据采集以节省电量。
     * 重复调用安全。
     */
    public void stop() {
        if (!mIsRunning) {
            return;
        }
        mSensorManager.unregisterListener(this);
        mIsRunning = false;
        // 重置滤波状态，避免下次启动时残留旧数据
        resetFilterState();
    }

    /**
     * 获取当前车载运动状态
     *
     * 渲染线程可直接调用，无需加锁（volatile 引用赋值保证原子性）。
     *
     * @return 最新的 CarMotionState 不可变快照
     */
    public CarMotionState getCurrentState() {
        return mCurrentState;
    }

    /**
     * 传感器数据回调
     *
     * 根据传感器类型分别进行低通滤波，然后执行互补融合并输出最终状态。
     *
     * @param event 传感器事件，包含三轴原始数据和时间戳
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensorType = event.sensor.getType();

        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            // 计算距上次事件的时间间隔（秒）
            float dt = computeDt(event.timestamp, mLastAccelTimestamp);
            mLastAccelTimestamp = event.timestamp;

            if (dt <= 0f || dt > 1.0f) {
                // 首次事件或时间间隔异常，仅记录原始值不做滤波
                System.arraycopy(event.values, 0, mFilteredAccel, 0, 3);
                return;
            }

            // 对加速度计三轴数据施加低通滤波
            for (int i = 0; i < 3; i++) {
                mFilteredAccel[i] = lowPassFilter(mFilteredAccel[i], event.values[i], dt);
            }

            // 死区过滤后，映射到车载坐标系并归一化
            float[] remapped = remapAxes(mFilteredAccel);
            float lateralAccel = applyDeadZone(remapped[0], ACCEL_DEAD_ZONE) / MAX_ACCEL;
            float longitudinalAccel = applyDeadZone(remapped[1], ACCEL_DEAD_ZONE) / MAX_ACCEL;

            // 互补滤波：加速度计部分（修正漂移）
            mFusedLateral = mFusedLateral * COMPLEMENTARY_ALPHA
                    + lateralAccel * (1f - COMPLEMENTARY_ALPHA);
            mFusedLongitudinal = mFusedLongitudinal * COMPLEMENTARY_ALPHA
                    + longitudinalAccel * (1f - COMPLEMENTARY_ALPHA);

            // 输出最终状态（clamp 到 [-1, 1]）
            emitState(event.timestamp);

        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            // 计算距上次事件的时间间隔（秒）
            float dt = computeDt(event.timestamp, mLastGyroTimestamp);
            mLastGyroTimestamp = event.timestamp;

            if (dt <= 0f || dt > 1.0f) {
                System.arraycopy(event.values, 0, mFilteredGyro, 0, 3);
                return;
            }

            // 对陀螺仪三轴数据施加低通滤波
            for (int i = 0; i < 3; i++) {
                mFilteredGyro[i] = lowPassFilter(mFilteredGyro[i], event.values[i], dt);
            }

            // 死区过滤后映射并归一化
            float[] remapped = remapAxes(mFilteredGyro);
            float lateralGyro = applyDeadZone(remapped[0], GYRO_DEAD_ZONE) / MAX_GYRO;
            float longitudinalGyro = applyDeadZone(remapped[1], GYRO_DEAD_ZONE) / MAX_GYRO;

            /*
             * 互补滤波：陀螺仪部分（α 权重主导）
             * 陀螺仪积分 dt 后叠加到融合值上，提供高频响应
             */
            mFusedLateral += COMPLEMENTARY_ALPHA * lateralGyro * dt;
            mFusedLongitudinal += COMPLEMENTARY_ALPHA * longitudinalGyro * dt;

            emitState(event.timestamp);
        }
    }

    /**
     * 传感器精度变化回调（本系统不关心精度变化，留空即可）
     *
     * @param sensor   精度发生变化的传感器
     * @param accuracy 新的精度等级
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 本系统不需要响应精度变化
    }

    /**
     * 一阶 RC 低通滤波器
     *
     * 离散化公式：y[n] = y[n-1] + α * (x[n] - y[n-1])
     * 其中 α = dt / (RC + dt)
     *
     * @param prevFiltered 上一次滤波输出值
     * @param rawInput     当前原始输入值
     * @param dt           时间间隔（秒）
     * @return 本次滤波输出值
     */
    private float lowPassFilter(float prevFiltered, float rawInput, float dt) {
        float alpha = dt / (LOW_PASS_RC + dt);
        return prevFiltered + alpha * (rawInput - prevFiltered);
    }

    /**
     * 死区过滤
     *
     * 当传感器值的绝对值低于阈值时返回 0，消除静止状态下的噪声抖动。
     *
     * @param value     输入值
     * @param threshold 死区阈值（正数）
     * @return 过滤后的值，死区内返回 0
     */
    private float applyDeadZone(float value, float threshold) {
        if (Math.abs(value) < threshold) {
            return 0f;
        }
        return value;
    }

    /**
     * 根据屏幕旋转状态重映射传感器轴
     *
     * 手机横屏固定在车载支架上时，传感器的物理轴与逻辑轴不一致：
     * - ROTATION_0（竖屏）：X=横向，Y=纵向（默认）
     * - ROTATION_90（逆时针横屏）：物理 Y 轴 → 逻辑横向，物理 -X 轴 → 逻辑纵向
     * - ROTATION_270（顺时针横屏）：物理 -Y 轴 → 逻辑横向，物理 X 轴 → 逻辑纵向
     * - ROTATION_180（倒置竖屏）：物理 -X → 逻辑横向，物理 -Y → 逻辑纵向
     *
     * @param values 传感器三轴原始数据 [X, Y, Z]
     * @return 重映射后的 [横向（左右转弯）, 纵向（刹车/加速）, 垂直] 数组
     */
    private float[] remapAxes(float[] values) {
        float[] result = new float[3];
        Display display = mWindowManager.getDefaultDisplay();
        int rotation = display.getRotation();

        switch (rotation) {
            case Surface.ROTATION_90:
                // 逆时针横屏：Y→横向，-X→纵向
                result[0] = values[1];
                result[1] = -values[0];
                result[2] = values[2];
                break;
            case Surface.ROTATION_270:
                // 顺时针横屏：-Y→横向，X→纵向
                result[0] = -values[1];
                result[1] = values[0];
                result[2] = values[2];
                break;
            case Surface.ROTATION_180:
                // 倒置竖屏：-X→横向，-Y→纵向
                result[0] = -values[0];
                result[1] = -values[1];
                result[2] = values[2];
                break;
            default:
                // ROTATION_0（竖屏默认）：X→横向，Y→纵向
                result[0] = values[0];
                result[1] = values[1];
                result[2] = values[2];
                break;
        }
        return result;
    }

    /**
     * 计算两次事件的时间间隔
     *
     * @param currentNanos  当前事件时间戳（纳秒）
     * @param previousNanos 上一次事件时间戳（纳秒）
     * @return 时间间隔（秒），首次调用（previousNanos=0）时返回 0
     */
    private float computeDt(long currentNanos, long previousNanos) {
        if (previousNanos == 0) {
            return 0f;
        }
        return (currentNanos - previousNanos) * 1e-9f;
    }

    /**
     * 将融合后的数据输出为不可变的 CarMotionState
     *
     * 对所有维度 clamp 到 [-1, 1] 范围，然后创建新的不可变状态对象。
     * volatile 赋值保证渲染线程立即可见。
     *
     * @param timestamp 事件时间戳（纳秒）
     */
    private void emitState(long timestamp) {
        float lateral = clamp(mFusedLateral, -1f, 1f);
        float longitudinal = clamp(mFusedLongitudinal, -1f, 1f);
        // 垂直力暂时直接从加速度计 Z 轴获取（归一化）
        float vertical = clamp(mFilteredAccel[2] / MAX_ACCEL, -1f, 1f);
        mCurrentState = new CarMotionState(lateral, longitudinal, vertical, timestamp);
    }

    /**
     * 数值钳位（限幅）
     *
     * @param value 输入值
     * @param min   最小值
     * @param max   最大值
     * @return 钳位后的值，保证在 [min, max] 范围内
     */
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 重置所有滤波状态
     *
     * 在停止传感器时调用，清除历史数据，避免下次启动时
     * 低通滤波器从旧值缓慢收敛，导致初始响应迟钝。
     */
    private void resetFilterState() {
        for (int i = 0; i < 3; i++) {
            mFilteredAccel[i] = 0f;
            mFilteredGyro[i] = 0f;
        }
        mLastAccelTimestamp = 0;
        mLastGyroTimestamp = 0;
        mFusedLateral = 0f;
        mFusedLongitudinal = 0f;
        mCurrentState = new CarMotionState();
    }
}

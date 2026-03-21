package com.android.launcher3.robot;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Process;
import android.view.SurfaceHolder;

/**
 * 机器人动画渲染线程
 *
 * 独立线程以 60fps 运行游戏循环，将物理更新和渲染解耦：
 * 1. 从 CarSensorManager 获取最新传感器数据
 * 2. 物理引擎更新机器人状态（固定步长 1/60 秒）
 * 3. 粒子系统更新（背景粒子 + 拖尾粒子）
 * 4. Canvas 渲染（机器人 + 粒子）
 * 5. 帧率控制（精确 sleep 到 16.67ms 边界）
 *
 * 使用 System.nanoTime() 进行高精度帧率控制，避免 SystemClock 的毫秒级误差。
 */
public class RobotRenderThread extends Thread {

    /** 物理更新固定时间步长：1/60 秒（约 16.67ms） */
    private static final float PHYSICS_DT = 1f / 60f;

    /** 目标帧间隔（纳秒）：16,666,666 ns ≈ 16.67ms */
    private static final long TARGET_FRAME_NS = 16_666_666L;

    /** 非活跃表情时拖尾粒子每帧发射数量 */
    private static final int TRAIL_EMIT_COUNT = 3;

    /** SurfaceHolder 引用，用于锁定/解锁 Canvas */
    private final SurfaceHolder mHolder;

    /** 物理引擎，计算机器人位置和姿态 */
    private final RobotPhysicsEngine mPhysics;

    /** 渲染器，将机器人状态绘制到 Canvas */
    private final RobotRenderer mRenderer;

    /** 背景环境粒子系统 */
    private final ParticleSystem mBgParticles;

    /** 运动拖尾粒子系统 */
    private final ParticleSystem mTrailParticles;

    /** 传感器管理器引用，用于获取最新运动数据 */
    private final CarSensorManager mSensorManager;

    /** 粒子绘制复用画笔 */
    private final Paint mParticlePaint;

    /** 线程运行标志，volatile 保证跨线程可见性 */
    private volatile boolean mRunning;

    /**
     * 构造渲染线程
     *
     * @param holder         Surface 持有者，用于获取 Canvas
     * @param physics        物理引擎实例
     * @param renderer       渲染器实例
     * @param bgParticles    背景粒子系统
     * @param trailParticles 拖尾粒子系统
     * @param sensorManager  传感器管理器，用于获取最新运动数据
     */
    public RobotRenderThread(SurfaceHolder holder,
                             RobotPhysicsEngine physics,
                             RobotRenderer renderer,
                             ParticleSystem bgParticles,
                             ParticleSystem trailParticles,
                             CarSensorManager sensorManager) {
        super("RobotRenderThread");
        mHolder = holder;
        mPhysics = physics;
        mRenderer = renderer;
        mBgParticles = bgParticles;
        mTrailParticles = trailParticles;
        mSensorManager = sensorManager;
        mParticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mRunning = false;
    }

    /**
     * 渲染线程主循环
     *
     * 设置线程优先级为 DISPLAY 级别后进入主循环，每帧执行：
     * 1. 读取传感器数据
     * 2. 物理更新（固定步长）
     * 3. 粒子更新 + 发射
     * 4. Canvas 绘制
     * 5. 帧率控制（精确 sleep 到目标帧间隔）
     *
     * Canvas 获取失败（Surface 被销毁）时跳过本帧。
     */
    @Override
    public void run() {
        // 提升线程优先级到显示级别，减少被调度器抢占的概率
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        mRunning = true;

        while (mRunning) {
            long frameStart = System.nanoTime();

            // 步骤1：获取最新传感器数据
            CarMotionState motion = mSensorManager.getCurrentState();

            // 步骤2：物理引擎更新
            mPhysics.update(PHYSICS_DT, motion);

            // 步骤3：粒子系统更新
            mBgParticles.update(PHYSICS_DT);
            mTrailParticles.update(PHYSICS_DT);

            // 步骤4：非 IDLE 表情时发射拖尾粒子
            RobotState state = mPhysics.getRobotState();
            if (state.expression != RobotState.Expression.IDLE) {
                mTrailParticles.emit(state.bodyX, state.bodyY, TRAIL_EMIT_COUNT);
            }

            // 步骤5：Canvas 绘制
            Canvas canvas = null;
            try {
                canvas = mHolder.lockCanvas();
                if (canvas != null) {
                    // 绘制机器人主体
                    mRenderer.draw(canvas, state);
                    // 绘制背景粒子（在机器人之上叠加）
                    mBgParticles.draw(canvas, mParticlePaint);
                    // 绘制拖尾粒子
                    mTrailParticles.draw(canvas, mParticlePaint);
                }
            } finally {
                // 确保 Canvas 被正确释放，即使绘制过程出现异常
                if (canvas != null) {
                    try {
                        mHolder.unlockCanvasAndPost(canvas);
                    } catch (IllegalStateException e) {
                        // Surface 已被销毁，忽略异常
                    }
                }
            }

            // 步骤6：帧率控制 — sleep 剩余时间到 16.67ms 边界
            long elapsed = System.nanoTime() - frameStart;
            long sleepNs = TARGET_FRAME_NS - elapsed;
            if (sleepNs > 0) {
                try {
                    long sleepMs = sleepNs / 1_000_000L;
                    int sleepNanos = (int) (sleepNs % 1_000_000L);
                    Thread.sleep(sleepMs, sleepNanos);
                } catch (InterruptedException e) {
                    // 线程被中断，退出循环
                    mRunning = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 请求停止渲染线程
     *
     * 设置运行标志为 false 并等待线程结束（最多 100ms）。
     * 在 SurfaceView.surfaceDestroyed() 中调用。
     */
    public void requestStop() {
        mRunning = false;
        try {
            join(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 检查渲染线程是否正在运行
     *
     * @return true 表示主循环正在执行
     */
    public boolean isRunning() {
        return mRunning;
    }
}

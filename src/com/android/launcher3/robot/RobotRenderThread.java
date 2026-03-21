package com.android.launcher3.robot;

import android.graphics.Canvas;
import android.os.Process;
import android.view.TextureView;

/**
 * 霓虹猫动画渲染线程（重构版）
 *
 * 独立线程以 60fps 运行游戏循环，整合新的猫动画组件：
 * 1. 从 CarSensorManager 获取最新传感器数据
 * 2. RobotPhysicsEngine 更新物理状态（身体位置、旋转、瞳孔、尾巴）
 * 3. CatAnimator 更新动画参数（发光、眨眼、耳朵抽动、表情过渡）
 * 4. 处理粒子发射（呼噜、爆发、环境粒子）
 * 5. CatRenderer 统一绘制（背景 + 猫 + 粒子分层）
 * 6. 帧率控制（精确 sleep 到 16.67ms 边界）
 */
public class RobotRenderThread extends Thread {

    /** 物理更新固定时间步长：1/60 秒 */
    private static final float PHYSICS_DT = 1f / 60f;

    /** 目标帧间隔（纳秒）：16,666,666 ns ≈ 16.67ms */
    private static final long TARGET_FRAME_NS = 16_666_666L;

    /** TextureView 引用 */
    private final TextureView mTextureView;

    /** 物理引擎 */
    private final RobotPhysicsEngine mPhysics;

    /** 猫渲染器（由 batch 1 创建，内部管理粒子绘制） */
    private final CatRenderer mCatRenderer;

    /** 动画状态机（发光、眨眼、耳朵、表情过渡） */
    private final CatAnimator mAnimator;

    /** 传感器管理器 */
    private final CarSensorManager mSensorManager;

    /** 线程运行标志 */
    private volatile boolean mRunning;

    /**
     * 构造渲染线程
     *
     * @param textureView   TextureView 实例
     * @param physics       物理引擎
     * @param catRenderer   猫渲染器（CatRenderer，由 batch 1 定义）
     * @param animator      动画状态机
     * @param sensorManager 传感器管理器
     */
    public RobotRenderThread(TextureView textureView,
                             RobotPhysicsEngine physics,
                             CatRenderer catRenderer,
                             CatAnimator animator,
                             CarSensorManager sensorManager) {
        super("RobotRenderThread");
        mTextureView = textureView;
        mPhysics = physics;
        mCatRenderer = catRenderer;
        mAnimator = animator;
        mSensorManager = sensorManager;
        mRunning = false;
    }

    /**
     * 渲染线程主循环
     *
     * 每帧执行：物理更新 → 动画更新 → 粒子发射 → Canvas 绘制 → 帧率控制
     */
    @Override
    public void run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        mRunning = true;
        android.util.Log.d("AutoPilot", "RobotRenderThread started (cat mode)");
        int frameCount = 0;

        while (mRunning) {
            frameCount++;
            long frameStart = System.nanoTime();

            // 步骤 1：获取传感器数据
            CarMotionState motion = mSensorManager.getCurrentState();

            // 步骤 2：物理引擎更新（身体位置、旋转、瞳孔、尾巴、表情状态机）
            mPhysics.update(PHYSICS_DT, motion);
            RobotState state = mPhysics.getRobotState();

            // 步骤 3：动画状态机更新（发光、眨眼、耳朵、表情过渡、震动）
            mAnimator.update(PHYSICS_DT, state);

            // 步骤 4：处理粒子发射
            handleParticleEmissions(state);

            // 步骤 5：Canvas 绘制
            Canvas canvas = null;
            try {
                canvas = mTextureView.lockCanvas();
                if (canvas != null) {
                    if (frameCount <= 3) {
                        android.util.Log.d("AutoPilot", "RenderThread(cat) frame=" + frameCount
                                + " canvas=" + canvas.getWidth() + "x" + canvas.getHeight()
                                + " catPos=" + state.bodyX + "," + state.bodyY);
                    }
                    // CatRenderer.draw() 统一处理清屏、背景粒子、猫绘制、前景粒子
                    mCatRenderer.draw(canvas, state);
                } else if (frameCount <= 3) {
                    android.util.Log.w("AutoPilot", "RenderThread(cat) frame=" + frameCount
                            + " canvas=null");
                }
            } catch (Exception e) {
                android.util.Log.e("AutoPilot", "RenderThread(cat) draw error", e);
            } finally {
                if (canvas != null) {
                    try {
                        mTextureView.unlockCanvasAndPost(canvas);
                    } catch (Exception e) {
                        // Surface 已销毁，忽略
                    }
                }
            }

            // 步骤 6：帧率控制
            long elapsed = System.nanoTime() - frameStart;
            long sleepNs = TARGET_FRAME_NS - elapsed;
            if (sleepNs > 0) {
                try {
                    long sleepMs = sleepNs / 1_000_000L;
                    int sleepNanos = (int) (sleepNs % 1_000_000L);
                    Thread.sleep(sleepMs, sleepNanos);
                } catch (InterruptedException e) {
                    mRunning = false;
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 处理粒子发射逻辑
     *
     * 粒子发射通过 RobotState 字段（purringAmplitude, particleBurstRequest）
     * 传递给 CatRenderer，CatRenderer 在 draw() 中根据这些字段决定粒子行为。
     *
     * 此方法负责在 draw 之前做预处理（当前为预留扩展点）。
     * particleBurstRequest 由 CatAnimator 设置，CatRenderer.draw() 读取并自行清零。
     *
     * @param state 当前机器人状态
     */
    private void handleParticleEmissions(RobotState state) {
        // CatRenderer.draw() 会读取 state.particleBurstRequest 和 state.purringAmplitude
        // 并在 draw 完成后自行清零 particleBurstRequest，无需在此处理
    }

    /**
     * 请求停止渲染线程
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
     * 检查是否正在运行
     */
    public boolean isRunning() {
        return mRunning;
    }
}

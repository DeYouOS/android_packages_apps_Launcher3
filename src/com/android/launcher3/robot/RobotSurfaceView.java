package com.android.launcher3.robot;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;

import java.util.Random;

/**
 * 机器人动画 TextureView
 *
 * 使用 TextureView 替代 SurfaceView，在正常 View 层级中渲染。
 * TextureView 没有独立 Surface 层，不存在 Z-order 问题，
 * 作为 DragLayer Overlay 时不会用黑色遮挡下层 Workspace。
 *
 * 内部组件：
 * - RobotPhysicsEngine：物理模拟（位置、手臂角度、表情）
 * - RobotRenderer：Canvas 2D 绘制
 * - ParticleSystem × 2：背景粒子（50个）+ 拖尾粒子（30个）
 * - RobotRenderThread：60fps 渲染线程
 */
public class RobotSurfaceView extends TextureView implements TextureView.SurfaceTextureListener {

    /** 背景粒子池容量 */
    private static final int BG_PARTICLE_COUNT = 50;

    /** 拖尾粒子池容量 */
    private static final int TRAIL_PARTICLE_COUNT = 30;

    /** 初始化背景粒子时每次发射的数量 */
    private static final int BG_INIT_EMIT_COUNT = 5;

    /** 物理引擎实例 */
    private final RobotPhysicsEngine mPhysics;

    /** 渲染器实例 */
    private final RobotRenderer mRenderer;

    /** 背景环境粒子系统 */
    private final ParticleSystem mBgParticles;

    /** 运动拖尾粒子系统 */
    private final ParticleSystem mTrailParticles;

    /** 渲染线程（SurfaceTexture 存在期间创建和销毁） */
    private RobotRenderThread mRenderThread;

    /** 随机数生成器，用于初始化背景粒子位置 */
    private final Random mRandom;

    /** SurfaceTexture 是否可用 */
    private boolean mSurfaceAvailable = false;

    public RobotSurfaceView(Context context) {
        this(context, null);
    }

    public RobotSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * 初始化所有内部组件并注册 SurfaceTexture 监听。
     * setOpaque(false) 启用透明背景，未渲染区域显示下层 Workspace。
     */
    public RobotSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mPhysics = new RobotPhysicsEngine();
        mRenderer = new RobotRenderer();
        mBgParticles = new ParticleSystem(BG_PARTICLE_COUNT);
        mTrailParticles = new ParticleSystem(TRAIL_PARTICLE_COUNT);
        mRandom = new Random();

        // TextureView 透明模式：未绘制区域透明，不遮挡下层 View
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        android.util.Log.d("AutoPilot", "onSurfaceTextureAvailable: " + width + "x" + height);
        mSurfaceAvailable = true;
        mPhysics.setScreenSize(width, height);
        if (width > 0 && height > 0) {
            initBackgroundParticles(width, height);
        }
        startRendering();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mPhysics.setScreenSize(width, height);
        initBackgroundParticles(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mSurfaceAvailable = false;
        stopRendering();
        // 返回 true 表示由系统释放 SurfaceTexture
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // 每帧更新回调，无需额外处理
    }

    /**
     * 启动渲染线程。如已在运行或 SurfaceTexture 不可用则跳过。
     */
    public void startRendering() {
        android.util.Log.d("AutoPilot", "startRendering: surfaceAvailable=" + mSurfaceAvailable
                + " threadRunning=" + (mRenderThread != null && mRenderThread.isRunning()));
        if (!mSurfaceAvailable) return;
        if (mRenderThread != null && mRenderThread.isRunning()) return;

        CarSensorManager sensorManager = CarSensorManager.getInstance(getContext());
        mRenderThread = new RobotRenderThread(
                this,
                mPhysics,
                mRenderer,
                mBgParticles,
                mTrailParticles,
                sensorManager
        );
        mRenderThread.start();
    }

    /**
     * 停止渲染线程。
     */
    public void stopRendering() {
        if (mRenderThread != null) {
            mRenderThread.requestStop();
            mRenderThread = null;
        }
    }

    /**
     * 处理外部命令（表情变化等）。
     */
    public void onCommand(RobotCommand cmd) {
        if (cmd instanceof RobotCommand.ChangeExpression) {
            RobotCommand.ChangeExpression exprCmd = (RobotCommand.ChangeExpression) cmd;
            RobotState state = mPhysics.getRobotState();
            state.expression = exprCmd.expression;
        }
    }

    /**
     * 在屏幕范围内随机散布初始背景粒子，使动画启动时有氛围感。
     */
    private void initBackgroundParticles(int width, int height) {
        int batchCount = BG_PARTICLE_COUNT / BG_INIT_EMIT_COUNT;
        for (int i = 0; i < batchCount; i++) {
            float rx = mRandom.nextFloat() * width;
            float ry = mRandom.nextFloat() * height;
            mBgParticles.emitBackground(rx, ry, BG_INIT_EMIT_COUNT);
        }
    }
}

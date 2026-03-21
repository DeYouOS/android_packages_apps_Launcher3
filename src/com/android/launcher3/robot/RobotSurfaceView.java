package com.android.launcher3.robot;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.view.TextureView;

/**
 * 霓虹猫动画 TextureView（重构版）
 *
 * 使用 TextureView 在正常 View 层级中渲染猫动画，无 Z-order 问题。
 *
 * 内部组件（重构后）：
 * - RobotPhysicsEngine：物理模拟（位置、旋转、瞳孔、尾巴）
 * - CatRenderer：Canvas 2D 猫绘制（由 batch 1 创建，内含 ParticleSystem）
 * - CatAnimator：动画状态机（发光、眨眼、耳朵、表情过渡）
 * - RobotRenderThread：60fps 渲染线程
 *
 * 与旧版的区别：移除了独立的 ParticleSystem 和 RobotRenderer，
 * 改由 CatRenderer 统一管理所有绘制逻辑。
 */
public class RobotSurfaceView extends TextureView implements TextureView.SurfaceTextureListener {

    /** 物理引擎实例 */
    private final RobotPhysicsEngine mPhysics;

    /** 猫渲染器（CatRenderer 由 batch 1 定义，内部管理粒子系统） */
    private final CatRenderer mCatRenderer;

    /** 动画状态机 */
    private final CatAnimator mAnimator;

    /** 渲染线程 */
    private RobotRenderThread mRenderThread;

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
        mCatRenderer = new CatRenderer();
        mAnimator = new CatAnimator();

        // TextureView 透明模式
        setOpaque(false);
        setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        android.util.Log.d("AutoPilot", "onSurfaceTextureAvailable(cat): " + width + "x" + height);
        mSurfaceAvailable = true;
        mPhysics.setScreenSize(width, height);
        startRendering();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        mPhysics.setScreenSize(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mSurfaceAvailable = false;
        stopRendering();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // 无需额外处理
    }

    /**
     * 启动渲染线程
     *
     * 创建并启动 RobotRenderThread，传入物理引擎、CatRenderer、CatAnimator。
     */
    public void startRendering() {
        android.util.Log.d("AutoPilot", "startRendering(cat): surfaceAvailable=" + mSurfaceAvailable
                + " threadRunning=" + (mRenderThread != null && mRenderThread.isRunning()));
        if (!mSurfaceAvailable) return;
        if (mRenderThread != null && mRenderThread.isRunning()) return;

        CarSensorManager sensorManager = CarSensorManager.getInstance(getContext());
        mRenderThread = new RobotRenderThread(
                this,
                mPhysics,
                mCatRenderer,
                mAnimator,
                sensorManager
        );
        mRenderThread.start();
    }

    /**
     * 停止渲染线程
     */
    public void stopRendering() {
        if (mRenderThread != null) {
            mRenderThread.requestStop();
            mRenderThread = null;
        }
    }

    /**
     * 处理外部命令（表情变化等）
     */
    public void onCommand(RobotCommand cmd) {
        if (cmd instanceof RobotCommand.ChangeExpression) {
            RobotCommand.ChangeExpression exprCmd = (RobotCommand.ChangeExpression) cmd;
            RobotState state = mPhysics.getRobotState();
            state.expression = exprCmd.expression;
        }
    }
}

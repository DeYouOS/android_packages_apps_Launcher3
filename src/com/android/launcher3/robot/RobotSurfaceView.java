package com.android.launcher3.robot;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.Random;

/**
 * 机器人动画 SurfaceView
 *
 * 封装 SurfaceView 的生命周期管理，协调物理引擎、渲染器、
 * 粒子系统和渲染线程。外部只需将此 View 添加到布局中并调用
 * startRendering()/stopRendering() 控制动画播放。
 *
 * 内部组件：
 * - RobotPhysicsEngine：物理模拟（位置、手臂角度、表情）
 * - RobotRenderer：Canvas 2D 绘制
 * - ParticleSystem × 2：背景粒子（50个）+ 拖尾粒子（30个）
 * - RobotRenderThread：60fps 渲染线程
 *
 * setZOrderOnTop(false) 确保此 SurfaceView 不会遮挡上层的 View 覆盖层。
 */
public class RobotSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

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

    /** 渲染线程（Surface 存在期间创建和销毁） */
    private RobotRenderThread mRenderThread;

    /** 随机数生成器，用于初始化背景粒子位置 */
    private final Random mRandom;

    /**
     * 代码创建构造函数
     *
     * @param context Android 上下文
     */
    public RobotSurfaceView(Context context) {
        this(context, null);
    }

    /**
     * XML 布局构造函数
     *
     * @param context Android 上下文
     * @param attrs   XML 属性集
     */
    public RobotSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * 完整参数构造函数
     *
     * 初始化所有内部组件并注册 SurfaceHolder 回调。
     * setZOrderOnTop(false) 确保不遮挡上层 View（如 RobotCommandOverlay）。
     *
     * @param context  Android 上下文
     * @param attrs    XML 属性集
     * @param defStyle 默认样式
     */
    public RobotSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mPhysics = new RobotPhysicsEngine();
        mRenderer = new RobotRenderer();
        mBgParticles = new ParticleSystem(BG_PARTICLE_COUNT);
        mTrailParticles = new ParticleSystem(TRAIL_PARTICLE_COUNT);
        mRandom = new Random();

        // 不遮挡上层 View 覆盖层
        setZOrderOnTop(false);
        getHolder().addCallback(this);
    }

    /**
     * Surface 创建回调
     *
     * Surface 可用后创建渲染线程并启动。
     * 同时在屏幕上随机散布初始背景粒子。
     *
     * @param holder Surface 持有者
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 在全屏范围内随机散布初始背景粒子
        int width = getWidth();
        int height = getHeight();
        if (width > 0 && height > 0) {
            initBackgroundParticles(width, height);
        }

        startRendering();
    }

    /**
     * Surface 尺寸变化回调
     *
     * 更新物理引擎的屏幕尺寸，机器人位置将被重置到新中心。
     *
     * @param holder Surface 持有者
     * @param format 像素格式
     * @param width  新宽度（像素）
     * @param height 新高度（像素）
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mPhysics.setScreenSize(width, height);

        // 尺寸变化后重新散布背景粒子
        initBackgroundParticles(width, height);
    }

    /**
     * Surface 销毁回调
     *
     * Surface 即将被销毁前停止渲染线程，避免在无效 Surface 上绘制。
     *
     * @param holder Surface 持有者
     */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopRendering();
    }

    /**
     * 启动渲染
     *
     * 创建并启动渲染线程。如果线程已在运行则不重复创建。
     * 需要在 Surface 可用后调用。
     */
    public void startRendering() {
        if (mRenderThread != null && mRenderThread.isRunning()) {
            return;
        }

        // 获取传感器管理器（使用 View 的 Context）
        CarSensorManager sensorManager = CarSensorManager.getInstance(getContext());

        mRenderThread = new RobotRenderThread(
                getHolder(),
                mPhysics,
                mRenderer,
                mBgParticles,
                mTrailParticles,
                sensorManager
        );
        mRenderThread.start();
    }

    /**
     * 停止渲染
     *
     * 安全停止渲染线程，等待其退出后释放引用。
     * 在 Surface 销毁或 Activity 暂停时调用。
     */
    public void stopRendering() {
        if (mRenderThread != null) {
            mRenderThread.requestStop();
            mRenderThread = null;
        }
    }

    /**
     * 处理外部命令
     *
     * 将命令转发给渲染器处理表情变化等效果。
     * 目前支持 ChangeExpression 命令。
     *
     * @param cmd 机器人命令
     */
    public void onCommand(RobotCommand cmd) {
        if (cmd instanceof RobotCommand.ChangeExpression) {
            RobotCommand.ChangeExpression exprCmd = (RobotCommand.ChangeExpression) cmd;
            RobotState state = mPhysics.getRobotState();
            state.expression = exprCmd.expression;
        }
    }

    /**
     * 在屏幕范围内随机散布初始背景粒子
     *
     * 将粒子均匀分布在整个屏幕区域，使动画启动时就有氛围感，
     * 而不是从空白开始逐渐出现。
     *
     * @param width  屏幕宽度（像素）
     * @param height 屏幕高度（像素）
     */
    private void initBackgroundParticles(int width, int height) {
        // 每次发射少量粒子，在不同位置多次调用以覆盖全屏
        int batchCount = BG_PARTICLE_COUNT / BG_INIT_EMIT_COUNT;
        for (int i = 0; i < batchCount; i++) {
            float rx = mRandom.nextFloat() * width;
            float ry = mRandom.nextFloat() * height;
            mBgParticles.emitBackground(rx, ry, BG_INIT_EMIT_COUNT);
        }
    }
}

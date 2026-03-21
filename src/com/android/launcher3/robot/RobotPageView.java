/*
 * RobotPageView — 机器人动画页的容器 View
 *
 * 作为 Workspace 的第 0 页嵌入 PagedView，替代原有 CellLayout。
 * 继承 FrameLayout 以兼容 PagedView 对子 View 的测量/布局逻辑。
 *
 * 内部分层结构：
 * 1. RobotSurfaceView（底层）：独立渲染线程，60fps 绘制机器人动画
 * 2. RobotCommandOverlay（顶层）：AI 指令触发的 UI 浮层（二维码、信息卡片等）
 *
 * 生命周期由 Workspace.onPageEndTransition() 驱动：
 * - 当用户翻到机器人页 → setActive(true) → 启动传感器 + 渲染
 * - 当用户离开机器人页 → setActive(false) → 停止传感器 + 渲染（省电）
 *
 * 触控策略：不拦截水平滑动（让 PagedView 处理翻页），
 * 仅拦截点击事件（未来用于 AI 交互）。
 */
package com.android.launcher3.robot;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.lifecycle.Observer;

import com.android.launcher3.Launcher;

/**
 * 机器人动画页容器
 *
 * 职责：
 * 1. 管理 RobotSurfaceView 和 RobotCommandOverlay 的生命周期
 * 2. 接收 Workspace 的 setActive() 回调，控制渲染和传感器启停
 * 3. 订阅 RobotCommandBus，将指令分发给动画层和 UI 浮层
 * 4. 不消费水平滑动事件，确保 PagedView 翻页正常工作
 */
public class RobotPageView extends FrameLayout {

    private static final String TAG = "RobotPageView";

    /** 机器人动画渲染层（SurfaceView，独立线程 60fps） */
    private RobotSurfaceView mSurfaceView;

    /** AI 指令 UI 浮层（标准 View，叠加在 SurfaceView 之上） */
    private RobotCommandOverlay mCommandOverlay;

    /** 车载传感器管理器（单例引用） */
    private CarSensorManager mSensorManager;

    /** 命令观察者引用，用于 onDetachedFromWindow 时手动移除 */
    private final Observer<RobotCommand> mCommandObserver = this::onCommand;

    /** 当前页面是否处于活跃状态（可见 + 渲染中） */
    private boolean mIsActive = false;

    /**
     * 构造机器人动画页
     *
     * @param context 上下文
     * @param launcher Launcher 实例，用于生命周期绑定
     */
    public RobotPageView(Context context, Launcher launcher) {
        super(context);

        // 初始化传感器管理器（单例，全局共享）
        mSensorManager = CarSensorManager.getInstance(context);

        // 创建 SurfaceView 渲染层（底层）
        mSurfaceView = new RobotSurfaceView(context);
        addView(mSurfaceView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 创建 AI 指令 UI 浮层（顶层）
        mCommandOverlay = new RobotCommandOverlay(context);
        addView(mCommandOverlay, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 订阅 AI 指令总线（Launcher 不是 LifecycleOwner，用 observeForever + 手动清理）
        RobotCommandBus.getInstance().observeForever(mCommandObserver);
    }

    /**
     * 由 Workspace.onPageEndTransition() 调用，控制页面活跃状态
     *
     * 活跃时启动传感器监听和渲染线程，离开时停止以节省电量。
     * 车载场景持续充电，但停止不可见页面的渲染仍是最佳实践。
     *
     * @param active true=页面可见且应渲染, false=页面不可见应暂停
     */
    public void setActive(boolean active) {
        if (mIsActive == active) return;
        mIsActive = active;

        if (active) {
            mSensorManager.start();
            mSurfaceView.startRendering();
        } else {
            mSensorManager.stop();
            mSurfaceView.stopRendering();
        }
    }

    /**
     * 处理 AI 指令
     *
     * 将指令分发给两个子系统：
     * 1. CommandOverlay：处理需要 UI 展示的指令（二维码、信息卡片）
     * 2. SurfaceView：处理需要改变动画的指令（表情变化、特殊动画）
     *
     * @param cmd 待处理的机器人指令
     */
    private void onCommand(RobotCommand cmd) {
        mCommandOverlay.executeCommand(cmd);
        mSurfaceView.onCommand(cmd);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 移除 observeForever 注册的观察者，防止内存泄漏
        RobotCommandBus.getInstance().removeObserver(mCommandObserver);
        // 确保传感器和渲染停止
        setActive(false);
    }

    /**
     * 触控拦截策略：不拦截水平滑动
     *
     * 让 PagedView（Workspace）处理水平滑动翻页，
     * 本 View 只处理点击事件（未来 AI 交互用）。
     * 返回 false 表示不拦截任何触控事件。
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return false;
    }
}

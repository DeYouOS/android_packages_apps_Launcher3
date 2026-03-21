package com.android.launcher3.robot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 机器人命令覆盖层
 *
 * 叠加在 RobotSurfaceView 之上的 FrameLayout，用于展示非动画 UI 内容：
 * - WiFi 二维码卡片（当前为文本占位，后续可集成 QR 生成库）
 * - 信息卡片（标题 + 正文，定时自动消失）
 *
 * 视觉风格：
 * - 半透明深色背景 + 圆角 + 科技蓝边框
 * - 淡入/淡出动画（alpha 0↔1）
 *
 * 所有 UI 操作在主线程执行（通过 Handler 保证）。
 */
public class RobotCommandOverlay extends FrameLayout {

    /** 科技蓝主色 */
    private static final int COLOR_PRIMARY = 0xFF00D4FF;

    /** 卡片背景色：半透明深色 */
    private static final int COLOR_CARD_BG = 0xDD0A0A2A;

    /** 卡片圆角半径（dp） */
    private static final float CARD_CORNER_DP = 16f;

    /** 卡片边框宽度（dp） */
    private static final float CARD_BORDER_DP = 2f;

    /** 淡入/淡出动画时长（毫秒） */
    private static final long ANIM_DURATION_MS = 300L;

    /** 卡片内边距（dp） */
    private static final float CARD_PADDING_DP = 24f;

    /** 标题字号（sp） */
    private static final float TITLE_SIZE_SP = 20f;

    /** 正文字号（sp） */
    private static final float CONTENT_SIZE_SP = 14f;

    /** 主线程 Handler，用于延迟任务 */
    private final Handler mHandler;

    /** 当前显示的卡片 View（用于移除和动画管理） */
    private View mCurrentCard;

    /**
     * 代码创建构造函数
     *
     * @param context Android 上下文
     */
    public RobotCommandOverlay(Context context) {
        this(context, null);
    }

    /**
     * XML 布局构造函数
     *
     * @param context Android 上下文
     * @param attrs   XML 属性集
     */
    public RobotCommandOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * 完整参数构造函数
     *
     * @param context  Android 上下文
     * @param attrs    XML 属性集
     * @param defStyle 默认样式
     */
    public RobotCommandOverlay(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 执行命令
     *
     * 根据命令类型分发到对应的处理方法。
     * 不支持的命令类型将被静默忽略。
     *
     * @param cmd 机器人命令
     */
    public void executeCommand(RobotCommand cmd) {
        if (cmd instanceof RobotCommand.ShowWifiQR) {
            showQRCode((RobotCommand.ShowWifiQR) cmd);
        } else if (cmd instanceof RobotCommand.ShowInfo) {
            showInfoCard((RobotCommand.ShowInfo) cmd);
        }
    }

    /**
     * 显示 WiFi 二维码卡片
     *
     * 将 WiFi 凭据格式化为标准 WiFi QR 码字符串：
     * WIFI:T:WPA;S:{ssid};P:{password};;
     *
     * 当前实现为文本占位卡片，显示 SSID、密码和 QR 字符串。
     * 后续可替换为实际的 QR 码位图渲染。
     *
     * @param cmd WiFi 二维码命令
     */
    private void showQRCode(RobotCommand.ShowWifiQR cmd) {
        // 先移除当前卡片
        dismissCurrentCard();

        // 构建标准 WiFi QR 码字符串
        String wifiQrString = "WIFI:T:WPA;S:" + cmd.ssid + ";P:" + cmd.password + ";;";

        // 创建卡片布局
        LinearLayout card = createCardLayout();

        // 标题
        TextView titleView = createTitleText("WiFi 连接");
        card.addView(titleView);

        // SSID 信息
        TextView ssidView = createContentText("网络名称: " + cmd.ssid);
        card.addView(ssidView);

        // 密码信息
        TextView passwordView = createContentText("密码: " + cmd.password);
        card.addView(passwordView);

        // QR 字符串（供调试和后续 QR 渲染使用）
        TextView qrStringView = createContentText(wifiQrString);
        qrStringView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        qrStringView.setTextColor(0x8000D4FF); // 半透明科技蓝
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        qrParams.topMargin = dpToPx(12f);
        qrStringView.setLayoutParams(qrParams);
        card.addView(qrStringView);

        // 添加到覆盖层并播放淡入动画
        showCard(card);
    }

    /**
     * 显示信息卡片
     *
     * 展示带标题和正文的信息卡片，在指定时间后自动淡出消失。
     *
     * @param cmd 信息卡片命令
     */
    private void showInfoCard(RobotCommand.ShowInfo cmd) {
        // 先移除当前卡片
        dismissCurrentCard();

        // 创建卡片布局
        LinearLayout card = createCardLayout();

        // 标题
        TextView titleView = createTitleText(cmd.title);
        card.addView(titleView);

        // 正文内容
        TextView contentView = createContentText(cmd.content);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = dpToPx(8f);
        contentView.setLayoutParams(contentParams);
        card.addView(contentView);

        // 添加到覆盖层并播放淡入动画
        showCard(card);

        // 定时自动消失
        mHandler.postDelayed(this::dismissCurrentCard, cmd.durationMs);
    }

    /**
     * 创建卡片容器布局
     *
     * 垂直 LinearLayout，带半透明深色背景、圆角和科技蓝边框。
     *
     * @return 配置好样式的 LinearLayout
     */
    private LinearLayout createCardLayout() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        int padding = dpToPx(CARD_PADDING_DP);
        layout.setPadding(padding, padding, padding, padding);

        // 圆角 + 边框背景
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD_BG);
        bg.setCornerRadius(dpToPx(CARD_CORNER_DP));
        bg.setStroke(dpToPx(CARD_BORDER_DP), COLOR_PRIMARY);
        layout.setBackground(bg);

        // 居中放置在覆盖层中
        LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        layout.setLayoutParams(lp);

        return layout;
    }

    /**
     * 创建标题文本 View
     *
     * @param text 标题文字
     * @return 配置好样式的 TextView
     */
    private TextView createTitleText(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SIZE_SP);
        tv.setTextColor(COLOR_PRIMARY);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /**
     * 创建正文文本 View
     *
     * @param text 正文文字
     * @return 配置好样式的 TextView
     */
    private TextView createContentText(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, CONTENT_SIZE_SP);
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    /**
     * 将卡片添加到覆盖层并播放淡入动画
     *
     * 卡片初始 alpha=0，通过 300ms 动画渐变到 alpha=1。
     *
     * @param card 要显示的卡片 View
     */
    private void showCard(View card) {
        mCurrentCard = card;
        card.setAlpha(0f);
        addView(card);
        card.animate()
                .alpha(1f)
                .setDuration(ANIM_DURATION_MS)
                .start();
    }

    /**
     * 淡出并移除当前卡片
     *
     * 通过 300ms 动画将 alpha 从 1 渐变到 0，动画结束后从 View 树中移除。
     * 同时清除所有待执行的延迟任务（防止重复触发消失）。
     */
    private void dismissCurrentCard() {
        // 清除待执行的延迟消失任务
        mHandler.removeCallbacksAndMessages(null);

        if (mCurrentCard == null) {
            return;
        }

        final View card = mCurrentCard;
        mCurrentCard = null;

        card.animate()
                .alpha(0f)
                .setDuration(ANIM_DURATION_MS)
                .setListener(new AnimatorListenerAdapter() {
                    /**
                     * 淡出动画结束后从 View 树中移除卡片
                     *
                     * @param animation 结束的动画实例
                     */
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        removeView(card);
                    }
                })
                .start();
    }

    /**
     * dp 转 px（整数值）
     *
     * @param dp 密度无关像素
     * @return 物理像素值（四舍五入取整）
     */
    private int dpToPx(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}

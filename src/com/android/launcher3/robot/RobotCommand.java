package com.android.launcher3.robot;

import android.os.SystemClock;

/**
 * 机器人命令抽象基类
 *
 * 所有发送给机器人系统的指令均继承此类。每个命令在创建时自动记录时间戳，
 * 用于命令排序和过期检测。
 *
 * 子命令通过静态内部类定义，支持以下命令类型：
 * - ShowWifiQR：显示 WiFi 二维码
 * - ShowInfo：显示信息卡片
 * - ChangeExpression：切换表情
 * - PlayAnimation：播放动画
 * - ShowSpeechBubble：显示对话气泡
 */
public abstract class RobotCommand {

    /** 命令创建时间戳（毫秒，基于 SystemClock.elapsedRealtime 防深度睡眠漂移） */
    public final long timestamp = SystemClock.elapsedRealtime();

    /**
     * 显示 WiFi 二维码命令
     *
     * 生成包含 SSID 和密码的 WiFi 二维码并在覆盖层上展示，
     * 方便乘客扫码连接车载网络。
     */
    public static class ShowWifiQR extends RobotCommand {
        /** WiFi 网络名称 */
        public final String ssid;
        /** WiFi 密码 */
        public final String password;

        /**
         * @param ssid     WiFi 网络名称
         * @param password WiFi 密码
         */
        public ShowWifiQR(String ssid, String password) {
            this.ssid = ssid;
            this.password = password;
        }
    }

    /**
     * 显示信息卡片命令
     *
     * 在覆盖层上展示带标题和正文的信息卡片，指定持续时间后自动消失。
     */
    public static class ShowInfo extends RobotCommand {
        /** 卡片标题 */
        public final String title;
        /** 卡片正文内容 */
        public final String content;
        /** 显示持续时间（毫秒），到期后自动关闭 */
        public final int durationMs;

        /**
         * @param title      卡片标题
         * @param content    卡片正文内容
         * @param durationMs 显示持续时间（毫秒）
         */
        public ShowInfo(String title, String content, int durationMs) {
            this.title = title;
            this.content = content;
            this.durationMs = durationMs;
        }
    }

    /**
     * 切换表情命令
     *
     * 立即将机器人表情切换到指定状态，影响眼睛和嘴巴的绘制方式。
     */
    public static class ChangeExpression extends RobotCommand {
        /** 目标表情状态 */
        public final RobotState.Expression expression;

        /**
         * @param expression 目标表情（IDLE / EXCITED / SURPRISED）
         */
        public ChangeExpression(RobotState.Expression expression) {
            this.expression = expression;
        }
    }

    /**
     * 播放动画命令
     *
     * 触发指定 ID 的预设动画序列（如招手、跳跃等）。
     */
    public static class PlayAnimation extends RobotCommand {
        /** 动画标识符 */
        public final String animationId;

        /**
         * @param animationId 预设动画的唯一标识符
         */
        public PlayAnimation(String animationId) {
            this.animationId = animationId;
        }
    }

    /**
     * 显示对话气泡命令
     *
     * 在机器人头部上方显示文字对话气泡，模拟机器人"说话"。
     */
    public static class ShowSpeechBubble extends RobotCommand {
        /** 气泡中显示的文字内容 */
        public final String text;

        /**
         * @param text 对话气泡文字
         */
        public ShowSpeechBubble(String text) {
            this.text = text;
        }
    }
}

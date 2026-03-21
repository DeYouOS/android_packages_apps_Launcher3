package com.android.launcher3.robot;

/**
 * 机器人动画状态容器（可变）
 *
 * 持有机器人所有动画参数的当前值，由 RobotPhysicsEngine 每帧更新，
 * 供渲染器读取以绘制机器人各部件。
 *
 * 该类为可变对象，仅在物理引擎线程中修改，渲染线程通过快照读取。
 * 所有角度单位为度（°），坐标单位为像素（px）。
 */
public class RobotState {

    /** 身体 X 坐标（像素），相对于屏幕中心的水平偏移 */
    public float bodyX;

    /** 身体 Y 坐标（像素），相对于屏幕中心的垂直偏移 */
    public float bodyY;

    /** 身体旋转角度（度），正值为顺时针旋转 */
    public float rotation;

    /** 左臂角度（度），0 为自然下垂，负值为向外展开 */
    public float leftArmAngle;

    /** 右臂角度（度），0 为自然下垂，正值为向外展开 */
    public float rightArmAngle;

    /** 当前表情状态，决定机器人面部绘制方式 */
    public Expression expression;

    /**
     * 闲置计时器（秒）
     * 持续累加，用于驱动呼吸动画（正弦波）和眨眼动画（周期触发）。
     * 当车辆运动时不重置，渲染器根据此值计算周期动画相位。
     */
    public float idleTimer;

    /** 屏幕宽度（像素），用于计算边缘检测和位置归一化 */
    public float screenWidth;

    /** 屏幕高度（像素），用于计算垂直方向的运动范围 */
    public float screenHeight;

    /** 屏幕旋转状态，对应 Surface.ROTATION_0/90/180/270 */
    public int displayRotation;

    /**
     * 机器人表情枚举
     *
     * 表情根据车辆运动合力大小自动切换：
     * - IDLE：合力 ≤ 0.3，日常状态（呼吸 + 眨眼动画）
     * - EXCITED：合力 > 0.3，兴奋状态（眼睛变大，轻微摇摆）
     * - SURPRISED：合力 > 0.7，惊讶状态（嘴巴张开，眼睛最大）
     */
    public enum Expression {
        /** 闲置状态：平静，执行呼吸和眨眼周期动画 */
        IDLE,
        /** 兴奋状态：中等运动强度，眼睛变大 */
        EXCITED,
        /** 惊讶状态：剧烈运动，嘴巴张开、眼睛圆睁 */
        SURPRISED
    }

    /**
     * 默认构造函数
     *
     * 初始化为居中、无旋转、手臂自然下垂、闲置表情的默认状态。
     */
    public RobotState() {
        bodyX = 0f;
        bodyY = 0f;
        rotation = 0f;
        leftArmAngle = 0f;
        rightArmAngle = 0f;
        expression = Expression.IDLE;
        idleTimer = 0f;
        screenWidth = 0f;
        screenHeight = 0f;
        displayRotation = 0;
    }
}

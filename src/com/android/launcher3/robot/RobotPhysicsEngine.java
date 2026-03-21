package com.android.launcher3.robot;

/**
 * 机器人物理引擎
 *
 * 使用弹簧-阻尼系统驱动机器人所有运动参数，将传感器数据映射为流畅的动画。
 *
 * 弹簧参数设计思路：
 * - 身体位置（STIFFNESS=200, DAMPING=0.75）：中等刚度+欠阻尼，产生轻微过冲，
 *   让身体移动有"弹性"感而不是机械感
 * - 身体旋转（STIFFNESS=80, DAMPING=0.6）：较低刚度+更低阻尼，旋转响应比位移慢，
 *   产生明显的振荡效果，增强动感
 * - 手臂（STIFFNESS=400, DAMPING=0.5）：高刚度+低阻尼，手臂甩动快速且有弹跳，
 *   视觉上最活跃的部位
 *
 * 表情状态机：
 *   合力 > 0.7 → SURPRISED（惊讶）
 *   合力 > 0.3 → EXCITED（兴奋）
 *   其他      → IDLE（闲置）
 */
public class RobotPhysicsEngine {

    // ---- 身体位置弹簧参数 ----
    /** 身体位置弹簧刚度 */
    private static final float BODY_STIFFNESS = 200f;
    /** 身体位置弹簧阻尼比 */
    private static final float BODY_DAMPING = 0.75f;

    // ---- 旋转弹簧参数 ----
    /** 旋转弹簧刚度 */
    private static final float ROTATION_STIFFNESS = 80f;
    /** 旋转弹簧阻尼比 */
    private static final float ROTATION_DAMPING = 0.6f;

    // ---- 手臂弹簧参数 ----
    /** 手臂弹簧刚度 */
    private static final float ARM_STIFFNESS = 400f;
    /** 手臂弹簧阻尼比 */
    private static final float ARM_DAMPING = 0.5f;

    /** 手臂最大展开角度（度） */
    private static final float ARM_MAX_ANGLE = 60f;

    /**
     * 边缘检测阈值系数
     * 身体 X 坐标超过 screenWidth * 0.35 时触发手臂展开
     */
    private static final float EDGE_THRESHOLD_RATIO = 0.35f;

    /** 表情切换阈值：合力超过此值切换为 SURPRISED */
    private static final float SURPRISED_THRESHOLD = 0.7f;

    /** 表情切换阈值：合力超过此值切换为 EXCITED */
    private static final float EXCITED_THRESHOLD = 0.3f;

    /** 身体 X 方向弹簧求解器 */
    private final SpringSolver mBodyXSpring;

    /** 身体 Y 方向弹簧求解器 */
    private final SpringSolver mBodyYSpring;

    /** 身体旋转弹簧求解器 */
    private final SpringSolver mRotationSpring;

    /** 左臂角度弹簧求解器 */
    private final SpringSolver mLeftArmSpring;

    /** 右臂角度弹簧求解器 */
    private final SpringSolver mRightArmSpring;

    /** 当前机器人动画状态 */
    private final RobotState mState;

    /** 屏幕宽度（像素） */
    private float mScreenWidth;

    /** 屏幕高度（像素） */
    private float mScreenHeight;

    /**
     * 构造物理引擎
     *
     * 初始化所有弹簧求解器和状态容器。
     * 必须在开始更新前调用 setScreenSize() 设置屏幕尺寸。
     */
    public RobotPhysicsEngine() {
        mBodyXSpring = new SpringSolver(BODY_STIFFNESS, BODY_DAMPING);
        mBodyYSpring = new SpringSolver(BODY_STIFFNESS, BODY_DAMPING);
        mRotationSpring = new SpringSolver(ROTATION_STIFFNESS, ROTATION_DAMPING);
        mLeftArmSpring = new SpringSolver(ARM_STIFFNESS, ARM_DAMPING);
        mRightArmSpring = new SpringSolver(ARM_STIFFNESS, ARM_DAMPING);
        mState = new RobotState();
        mScreenWidth = 0f;
        mScreenHeight = 0f;
    }

    /**
     * 设置屏幕尺寸
     *
     * 屏幕尺寸用于计算身体运动范围和边缘检测阈值。
     * 应在屏幕尺寸变化（如旋转）时重新调用。
     *
     * @param width  屏幕宽度（像素）
     * @param height 屏幕高度（像素）
     */
    public void setScreenSize(float width, float height) {
        mScreenWidth = width;
        mScreenHeight = height;
        mState.screenWidth = width;
        mState.screenHeight = height;
    }

    /**
     * 每帧更新物理状态
     *
     * 完整的更新流程：
     * 1. 根据传感器数据计算各弹簧的目标值
     * 2. 步进所有弹簧求解器
     * 3. 根据身体位置决定手臂展开状态
     * 4. 根据合力大小切换表情
     * 5. 累加闲置计时器
     *
     * @param dt     帧间隔时间（秒），通常为 1/60
     * @param motion 当前车载运动状态，来自 CarSensorManager
     */
    public void update(float dt, CarMotionState motion) {
        // 防御性检查：屏幕尺寸未设置时跳过更新
        if (mScreenWidth <= 0f || mScreenHeight <= 0f) {
            return;
        }

        // ---- 步骤 1：计算目标位置 ----
        /*
         * 横向力映射为 X 偏移：lateralForce * 屏幕宽度 * 0.35
         * 正横向力（右转）→ 身体向左偏移（惯性效果），取负号
         * 纵向力映射为 Y 偏移：longitudinalForce * 屏幕高度 * 0.2
         * 正纵向力（加速）→ 身体向下偏移（惯性效果），取正号
         */
        float targetX = -motion.lateralForce * mScreenWidth * EDGE_THRESHOLD_RATIO;
        float targetY = motion.longitudinalForce * mScreenHeight * 0.2f;

        mBodyXSpring.setTarget(targetX);
        mBodyYSpring.setTarget(targetY);

        /*
         * 旋转目标：横向力 × 最大旋转角度（15°）
         * 左转时身体向右倾斜（正角度），增强视觉惯性感
         */
        float targetRotation = -motion.lateralForce * 15f;
        mRotationSpring.setTarget(targetRotation);

        // ---- 步骤 2：步进所有弹簧 ----
        mBodyXSpring.step(dt);
        mBodyYSpring.step(dt);
        mRotationSpring.step(dt);
        mLeftArmSpring.step(dt);
        mRightArmSpring.step(dt);

        // 将弹簧输出写入状态
        mState.bodyX = mBodyXSpring.getPosition();
        mState.bodyY = mBodyYSpring.getPosition();
        mState.rotation = mRotationSpring.getPosition();

        // ---- 步骤 3：手臂状态 ----
        /*
         * 边缘检测：身体接近屏幕边缘时手臂展开
         * - 身体偏向左侧（bodyX < -edgeThreshold）→ 左臂向外展开（负角度 -60°）
         * - 身体偏向右侧（bodyX > edgeThreshold）→ 右臂向外展开（正角度 +60°）
         * - 在中央区域时手臂自然下垂（0°）
         */
        float edgeThreshold = mScreenWidth * EDGE_THRESHOLD_RATIO;
        float currentBodyX = mBodyXSpring.getPosition();

        float leftArmTarget = 0f;
        float rightArmTarget = 0f;

        if (currentBodyX < -edgeThreshold) {
            // 身体偏左，左臂展开以示"抓住"边缘
            leftArmTarget = -ARM_MAX_ANGLE;
        }
        if (currentBodyX > edgeThreshold) {
            // 身体偏右，右臂展开以示"抓住"边缘
            rightArmTarget = ARM_MAX_ANGLE;
        }

        mLeftArmSpring.setTarget(leftArmTarget);
        mRightArmSpring.setTarget(rightArmTarget);

        mState.leftArmAngle = mLeftArmSpring.getPosition();
        mState.rightArmAngle = mRightArmSpring.getPosition();

        // ---- 步骤 4：表情状态机 ----
        /*
         * 根据合力（横向 + 纵向的欧几里得范数）决定表情：
         * - totalForce > 0.7 → SURPRISED（惊讶：急转弯或急刹车）
         * - totalForce > 0.3 → EXCITED（兴奋：中等幅度转弯）
         * - 否则              → IDLE（闲置：正常行驶或静止）
         */
        float totalForce = motion.getTotalForce();

        if (totalForce > SURPRISED_THRESHOLD) {
            mState.expression = RobotState.Expression.SURPRISED;
        } else if (totalForce > EXCITED_THRESHOLD) {
            mState.expression = RobotState.Expression.EXCITED;
        } else {
            mState.expression = RobotState.Expression.IDLE;
        }

        // ---- 步骤 5：累加闲置计时器 ----
        mState.idleTimer += dt;
    }

    /**
     * 获取当前机器人动画状态
     *
     * 返回内部可变状态对象的引用，渲染器应在同一帧内读取完毕。
     * 不要跨帧持有此引用，因为下一帧 update() 会修改其中的值。
     *
     * @return 当前 RobotState，包含所有动画参数
     */
    public RobotState getRobotState() {
        return mState;
    }
}

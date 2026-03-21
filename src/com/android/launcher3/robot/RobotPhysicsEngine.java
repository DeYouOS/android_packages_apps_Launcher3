package com.android.launcher3.robot;

/**
 * 霓虹猫物理引擎（重写版）
 *
 * 将传感器数据映射为猫的运动动画参数，使用弹簧-阻尼系统驱动：
 * 1. 身体位置：弹簧阻尼，屏幕中心 + 传感器偏移（缩小范围，猫体积更大）
 * 2. 身体旋转：弹簧阻尼，lateralForce × 12°
 * 3. 眼睛瞳孔追踪：传感器 → 瞳孔偏移，椭圆约束 + 低通滤波
 * 4. 表情状态机：根据合力大小切换 IDLE/EXCITED/SURPRISED
 * 5. 尾巴物理：委托 TailPhysics 更新，拷贝角度到 RobotState
 *
 * 弹簧参数设计：
 * - 身体位置（200, 0.75）：中等刚度 + 欠阻尼，产生弹性移动感
 * - 身体旋转（80, 0.6）：较低刚度 + 更低阻尼，旋转有明显振荡
 */
public class RobotPhysicsEngine {

    // ---- 身体位置弹簧参数 ----
    private static final float BODY_STIFFNESS = 200f;
    private static final float BODY_DAMPING = 0.75f;

    // ---- 旋转弹簧参数 ----
    private static final float ROTATION_STIFFNESS = 80f;
    private static final float ROTATION_DAMPING = 0.6f;

    /**
     * 身体 X 方向传感器响应系数
     * lateralForce × screenWidth × 0.15 → 比旧版 0.35 更小，因为猫占屏比例更大
     */
    private static final float BODY_X_RANGE = 0.15f;

    /**
     * 身体 Y 方向传感器响应系数
     * longitudinalForce × screenHeight × 0.10
     */
    private static final float BODY_Y_RANGE = 0.10f;

    /** 最大身体旋转角度（度），lateralForce × 12° */
    private static final float MAX_ROTATION_DEG = 12f;

    // ---- 瞳孔追踪参数 ----
    /** 瞳孔 X 方向最大偏移（dp） */
    private static final float PUPIL_MAX_X = 5.0f;
    /** 瞳孔 Y 方向最大偏移（dp） */
    private static final float PUPIL_MAX_Y = 4.0f;
    /**
     * 瞳孔低通滤波平滑系数
     * 每帧混合 12% 的目标值，88% 保留旧值，产生平滑追踪效果
     */
    private static final float PUPIL_SMOOTHING = 0.12f;

    // ---- 表情切换阈值 ----
    /** 合力超过此值切换为 SURPRISED */
    private static final float SURPRISED_THRESHOLD = 0.7f;
    /** 合力超过此值切换为 EXCITED */
    private static final float EXCITED_THRESHOLD = 0.3f;

    /** 身体 X 方向弹簧求解器 */
    private final SpringSolver mBodyXSpring;
    /** 身体 Y 方向弹簧求解器 */
    private final SpringSolver mBodyYSpring;
    /** 身体旋转弹簧求解器 */
    private final SpringSolver mRotationSpring;

    /** 尾巴物理引擎（由 batch 1 创建） */
    private final TailPhysics mTailPhysics;

    /** 当前机器人动画状态 */
    private final RobotState mState;

    /** 屏幕宽度（像素） */
    private float mScreenWidth;
    /** 屏幕高度（像素） */
    private float mScreenHeight;

    /** 瞳孔当前平滑 X 偏移（dp） */
    private float mPupilSmoothX;
    /** 瞳孔当前平滑 Y 偏移（dp） */
    private float mPupilSmoothY;

    /**
     * 构造物理引擎
     *
     * 初始化弹簧求解器、尾巴物理和状态容器。
     * 必须在开始更新前调用 setScreenSize() 设置屏幕尺寸。
     */
    public RobotPhysicsEngine() {
        mBodyXSpring = new SpringSolver(BODY_STIFFNESS, BODY_DAMPING);
        mBodyYSpring = new SpringSolver(BODY_STIFFNESS, BODY_DAMPING);
        mRotationSpring = new SpringSolver(ROTATION_STIFFNESS, ROTATION_DAMPING);
        mTailPhysics = new TailPhysics();
        mState = new RobotState();
        mScreenWidth = 0f;
        mScreenHeight = 0f;
        mPupilSmoothX = 0f;
        mPupilSmoothY = 0f;
    }

    /**
     * 设置屏幕尺寸
     *
     * 用于计算身体运动范围。应在屏幕尺寸变化时重新调用。
     *
     * @param width  屏幕宽度（像素）
     * @param height 屏幕高度（像素）
     */
    public void setScreenSize(float width, float height) {
        mScreenWidth = width;
        mScreenHeight = height;
        mState.screenWidth = width;
        mState.screenHeight = height;
        // 初始化身体到屏幕中心
        float cx = width / 2f;
        float cy = height / 2f;
        mBodyXSpring.setPosition(cx);
        mBodyYSpring.setPosition(cy);
        mState.bodyX = cx;
        mState.bodyY = cy;
    }

    /**
     * 每帧更新物理状态
     *
     * 完整更新流程：
     * 1. 传感器数据 → 身体位置/旋转弹簧目标值
     * 2. 步进弹簧求解器
     * 3. 瞳孔追踪（椭圆约束 + 低通滤波）
     * 4. 表情状态机（合力 → IDLE/EXCITED/SURPRISED）
     * 5. 尾巴物理更新 + 角度拷贝
     * 6. 传感器原始值写入 state（供 CatAnimator 等使用）
     *
     * @param dt     帧间隔（秒），通常 1/60
     * @param motion 当前传感器运动状态
     */
    public void update(float dt, CarMotionState motion) {
        if (mScreenWidth <= 0f || mScreenHeight <= 0f) {
            return;
        }

        // ---- 1. 身体位置目标 ----
        float centerX = mScreenWidth / 2f;
        float centerY = mScreenHeight / 2f;
        // 横向力取负（惯性方向相反）
        float targetX = centerX + (-motion.lateralForce * mScreenWidth * BODY_X_RANGE);
        float targetY = centerY + (motion.longitudinalForce * mScreenHeight * BODY_Y_RANGE);
        mBodyXSpring.setTarget(targetX);
        mBodyYSpring.setTarget(targetY);

        // 旋转目标
        float targetRotation = -motion.lateralForce * MAX_ROTATION_DEG;
        mRotationSpring.setTarget(targetRotation);

        // ---- 2. 步进弹簧 ----
        mBodyXSpring.step(dt);
        mBodyYSpring.step(dt);
        mRotationSpring.step(dt);

        mState.bodyX = mBodyXSpring.getPosition();
        mState.bodyY = mBodyYSpring.getPosition();
        mState.rotation = mRotationSpring.getPosition();

        // ---- 3. 瞳孔追踪 ----
        // 目标偏移：传感器力取反（眼睛追随运动方向的反向，即看向来力方向）
        float targetPupilX = -motion.lateralForce * PUPIL_MAX_X;
        float targetPupilY = -motion.longitudinalForce * PUPIL_MAX_Y;

        // 椭圆约束：归一化到单位椭圆，超出时缩放到边界
        float normX = (PUPIL_MAX_X > 0f) ? (targetPupilX / PUPIL_MAX_X) : 0f;
        float normY = (PUPIL_MAX_Y > 0f) ? (targetPupilY / PUPIL_MAX_Y) : 0f;
        float ellipseDist = normX * normX + normY * normY;
        if (ellipseDist > 1.0f) {
            float scale = 1.0f / (float) Math.sqrt(ellipseDist);
            targetPupilX *= scale;
            targetPupilY *= scale;
        }

        // 低通滤波平滑
        mPupilSmoothX += (targetPupilX - mPupilSmoothX) * PUPIL_SMOOTHING;
        mPupilSmoothY += (targetPupilY - mPupilSmoothY) * PUPIL_SMOOTHING;

        mState.eyePupilOffsetX = mPupilSmoothX;
        mState.eyePupilOffsetY = mPupilSmoothY;

        // ---- 4. 表情状态机 ----
        float totalForce = motion.getTotalForce();
        if (totalForce > SURPRISED_THRESHOLD) {
            mState.expression = RobotState.Expression.SURPRISED;
        } else if (totalForce > EXCITED_THRESHOLD) {
            mState.expression = RobotState.Expression.EXCITED;
        } else {
            mState.expression = RobotState.Expression.IDLE;
        }

        // ---- 5. 尾巴物理 ----
        mTailPhysics.setExpression(mState.expression);
        mTailPhysics.update(dt, motion.lateralForce, computeYawRate(motion));

        // 拷贝尾巴角度到 state
        float[] tailAngles = mTailPhysics.getAngles();
        System.arraycopy(tailAngles, 0, mState.tailAngles, 0,
                Math.min(tailAngles.length, mState.tailAngles.length));

        // ---- 6. 传感器原始值写入 state（供其他组件使用） ----
        mState.lateralForce = motion.lateralForce;
        mState.longitudinalForce = motion.longitudinalForce;
        mState.yawRate = computeYawRate(motion);
    }

    /**
     * 获取当前机器人状态
     *
     * 返回内部可变状态对象的引用，渲染器应在同一帧内读取完毕。
     *
     * @return 当前 RobotState
     */
    public RobotState getRobotState() {
        return mState;
    }

    /**
     * 从传感器数据中估算偏航角速度
     *
     * 使用横向力作为偏航率的近似值。实际项目中可从陀螺仪 Z 轴获取。
     *
     * @param motion 传感器运动状态
     * @return 估算偏航率（归一化 [-1, 1]）
     */
    private float computeYawRate(CarMotionState motion) {
        // 使用横向力近似偏航率
        return motion.lateralForce;
    }
}

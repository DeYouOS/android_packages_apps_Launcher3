package com.android.launcher3.robot;

/**
 * 霓虹猫物理引擎（完整重写版）
 *
 * 将传感器 + GPS 数据映射为猫的全部动画参数，核心架构：
 * 1. 弹簧-阻尼系统：驱动身体位置、旋转的物理运动
 * 2. SpeedTracker：GPS 速度跟踪与速度区间分类
 * 3. EnvironmentTracker：环境状态追踪（驾驶模式、困倦度、昼夜）
 * 4. AnimationPriorityManager：逐部件优先级仲裁，解决多状态并发冲突
 * 5. TailPhysics：尾巴 6 段弹簧链物理
 * 6. 瞳孔追踪：椭圆约束 + 低通滤波
 *
 * 优先级动画映射层：
 * - P0 SAFETY：危险/超速 → 抓握、惊恐表情、指示灯警告
 * - P2 DRIVING_REACT：急转/急刹/急加速 → 实时肢体反应
 * - P3 SPEED_AMBIENT：速度区间基础氛围
 * - P4 SCENE：睡眠/长时间闲置 → 打盹、思考、打哈欠
 * - P5 IDLE：由 CatAnimator 控制，物理引擎不设置
 *
 * 弹簧参数设计（保持不变）：
 * - 身体位置（200, 0.75）：中等刚度 + 欠阻尼，弹性移动感
 * - 身体旋转（80, 0.6）：较低刚度 + 更低阻尼，旋转有明显振荡
 */
public class RobotPhysicsEngine {

    // ---- 身体位置弹簧参数（保持原有值） ----
    private static final float BODY_STIFFNESS = 200f;
    private static final float BODY_DAMPING = 0.75f;

    // ---- 旋转弹簧参数（保持原有值） ----
    private static final float ROTATION_STIFFNESS = 80f;
    private static final float ROTATION_DAMPING = 0.6f;

    /**
     * 身体 X 方向传感器响应系数
     * lateralForce × screenWidth × 0.15 → 猫占屏比例较大，范围缩小
     */
    private static final float BODY_X_RANGE = 0.15f;

    /**
     * 身体 Y 方向传感器响应系数
     * longitudinalForce × screenHeight × 0.10
     */
    private static final float BODY_Y_RANGE = 0.10f;

    /** 最大身体旋转角度（度），lateralForce × 12° */
    private static final float MAX_ROTATION_DEG = 12f;

    // ---- 瞳孔追踪参数（保持原有值） ----
    /** 瞳孔 X 方向最大偏移（dp） */
    private static final float PUPIL_MAX_X = 5.0f;
    /** 瞳孔 Y 方向最大偏移（dp） */
    private static final float PUPIL_MAX_Y = 4.0f;
    /**
     * 瞳孔低通滤波平滑系数
     * 每帧混合 12% 的目标值，88% 保留旧值，产生平滑追踪效果
     */
    private static final float PUPIL_SMOOTHING = 0.12f;

    // ---- 表情切换阈值（保持原有值） ----
    /** 合力超过此值切换为 SURPRISED */
    private static final float SURPRISED_THRESHOLD = 0.7f;
    /** 合力超过此值切换为 EXCITED */
    private static final float EXCITED_THRESHOLD = 0.3f;

    // ---- 驾驶反应力阈值 ----
    /** 急转弯横向力阈值 */
    private static final float SHARP_TURN_THRESHOLD = 0.5f;
    /** 急刹/急加速纵向力阈值 */
    private static final float HARD_BRAKE_ACCEL_THRESHOLD = 0.5f;

    // ---- 弹簧求解器（保持原有实例化逻辑） ----
    /** 身体 X 方向弹簧求解器 */
    private final SpringSolver mBodyXSpring;
    /** 身体 Y 方向弹簧求解器 */
    private final SpringSolver mBodyYSpring;
    /** 身体旋转弹簧求解器 */
    private final SpringSolver mRotationSpring;

    /** 尾巴物理引擎 */
    private final TailPhysics mTailPhysics;

    // ---- 新增：追踪器和管理器 ----
    /** GPS 速度追踪与区间分类器 */
    private final SpeedTracker mSpeedTracker;
    /** 环境状态追踪器（驾驶模式、困倦度、昼夜） */
    private final EnvironmentTracker mEnvironmentTracker;
    /** 逐部件动画优先级仲裁管理器 */
    private final AnimationPriorityManager mPriorityManager;

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
     * 初始化弹簧求解器、尾巴物理、速度追踪器、环境追踪器、
     * 优先级管理器和状态容器。
     * 必须在开始更新前调用 setScreenSize() 设置屏幕尺寸。
     */
    public RobotPhysicsEngine() {
        mBodyXSpring = new SpringSolver(BODY_STIFFNESS, BODY_DAMPING);
        mBodyYSpring = new SpringSolver(BODY_STIFFNESS, BODY_DAMPING);
        mRotationSpring = new SpringSolver(ROTATION_STIFFNESS, ROTATION_DAMPING);
        mTailPhysics = new TailPhysics();
        mSpeedTracker = new SpeedTracker();
        mEnvironmentTracker = new EnvironmentTracker();
        mPriorityManager = new AnimationPriorityManager();
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
     * 1. 更新速度追踪器和环境追踪器
     * 2. 身体位置/旋转弹簧目标 → 步进弹簧
     * 3. 瞳孔追踪（椭圆约束 + 低通滤波）
     * 4. GPS/速度数据写入 state
     * 5. 环境数据写入 state
     * 6. 优先级动画映射（P0 安全 → P2 驾驶反应 → P3 速度氛围 → P4 场景）
     * 7. 优先级仲裁结果应用到 state
     * 8. 表情状态机（合力 + 安全覆盖）
     * 9. 尾巴物理更新
     * 10. 传感器原始值写入 state
     *
     * @param dt     帧间隔（秒），通常 1/60
     * @param motion 当前传感器运动状态
     */
    public void update(float dt, CarMotionState motion) {
        if (mScreenWidth <= 0f || mScreenHeight <= 0f) {
            return;
        }

        // ---- 1. 更新追踪器 ----
        mSpeedTracker.update(motion.speedKmh, dt);
        mEnvironmentTracker.update(dt, motion.getTotalForce(), motion.speedKmh);
        mEnvironmentTracker.setLateralForce(motion.lateralForce);
        mEnvironmentTracker.setVerticalForce(motion.verticalForce);

        // ---- 2. 身体位置弹簧 ----
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

        // 步进弹簧
        mBodyXSpring.step(dt);
        mBodyYSpring.step(dt);
        mRotationSpring.step(dt);

        mState.bodyX = mBodyXSpring.getPosition();
        mState.bodyY = mBodyYSpring.getPosition();
        mState.rotation = mRotationSpring.getPosition();

        // ---- 3. 瞳孔追踪（椭圆约束 + 低通滤波） ----
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

        // ---- 4. GPS/速度数据写入 state ----
        mState.speedKmh = mSpeedTracker.getSpeedKmh();
        mState.speedAcceleration = mSpeedTracker.getAcceleration();
        mState.speedZone = mSpeedTracker.getSpeedZone();
        mState.gpsAvailable = motion.gpsAvailable;
        mState.bearing = motion.bearing;

        // ---- 5. 环境数据写入 state ----
        mState.drivingMode = mEnvironmentTracker.getDrivingMode();
        mState.idleDuration = mEnvironmentTracker.getIdleDuration();
        mState.drivingDuration = mEnvironmentTracker.getDrivingDuration();
        mState.isCharging = mEnvironmentTracker.isCharging();
        mState.hourOfDay = mEnvironmentTracker.getHourOfDay();
        mState.sleepiness = mEnvironmentTracker.getDrowsiness();

        // ---- 6. 优先级动画映射 ----
        applyPriorityAnimations(motion);

        // ---- 7. 优先级仲裁结果应用到 state ----
        applyPriorityResults();

        // ---- 8. 表情状态机 ----
        // P0 安全层会覆盖为 SURPRISED，其他情况由合力驱动
        float totalForce = motion.getTotalForce();
        RobotState.SpeedZone zone = mSpeedTracker.getSpeedZone();
        if (zone == RobotState.SpeedZone.DANGER || zone == RobotState.SpeedZone.OVER_LIMIT) {
            mState.expression = RobotState.Expression.SURPRISED;
        } else if (totalForce > SURPRISED_THRESHOLD) {
            mState.expression = RobotState.Expression.SURPRISED;
        } else if (totalForce > EXCITED_THRESHOLD) {
            mState.expression = RobotState.Expression.EXCITED;
        } else {
            mState.expression = RobotState.Expression.IDLE;
        }

        // ---- 9. 尾巴物理 ----
        mTailPhysics.setExpression(mState.expression);
        mTailPhysics.update(dt, motion.lateralForce, computeYawRate(motion));

        // 拷贝尾巴角度到 state
        float[] tailAngles = mTailPhysics.getAngles();
        System.arraycopy(tailAngles, 0, mState.tailAngles, 0,
                Math.min(tailAngles.length, mState.tailAngles.length));

        // ---- 10. 传感器原始值写入 state ----
        mState.lateralForce = motion.lateralForce;
        mState.longitudinalForce = motion.longitudinalForce;
        mState.yawRate = computeYawRate(motion);
    }

    /**
     * 执行优先级动画映射
     *
     * 根据速度区间、驾驶状态、传感器力度等条件，向 AnimationPriorityManager
     * 的各优先级层提交或释放动画请求。优先级数值越小越高：
     * P0(0)=安全 > P2(2)=驾驶反应 > P3(3)=速度氛围 > P4(4)=场景
     *
     * @param motion 当前传感器运动状态
     */
    private void applyPriorityAnimations(CarMotionState motion) {
        RobotState.SpeedZone zone = mSpeedTracker.getSpeedZone();
        RobotState.DrivingMode mode = mEnvironmentTracker.getDrivingMode();
        float lateral = motion.lateralForce;
        float longitudinal = motion.longitudinalForce;
        float absLateral = Math.abs(lateral);

        // ======== P0 SAFETY：危险车速 / 超速 ========
        if (zone == RobotState.SpeedZone.DANGER
                || zone == RobotState.SpeedZone.OVER_LIMIT) {
            mPriorityManager.request(AnimationPriorityManager.PART_ARM,
                    AnimationPriorityManager.PRIORITY_SAFETY,
                    RobotState.ArmPose.GRAB_HOLD);
            mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                    AnimationPriorityManager.PRIORITY_SAFETY,
                    zone == RobotState.SpeedZone.DANGER
                            ? RobotState.MouthShape.OPEN_O
                            : RobotState.MouthShape.FLAT);
            mPriorityManager.request(AnimationPriorityManager.PART_EYEBROW,
                    AnimationPriorityManager.PRIORITY_SAFETY,
                    RobotState.EyebrowState.FURROWED);
            mPriorityManager.request(AnimationPriorityManager.PART_BODY,
                    AnimationPriorityManager.PRIORITY_SAFETY,
                    RobotState.BodyAction.SHIVER);
            mPriorityManager.request(AnimationPriorityManager.PART_INDICATOR,
                    AnimationPriorityManager.PRIORITY_SAFETY,
                    zone == RobotState.SpeedZone.DANGER
                            ? RobotState.IndicatorState.ERROR
                            : RobotState.IndicatorState.WARNING);
        } else {
            // 安全条件解除，释放 P0 层所有部件
            mPriorityManager.releaseAll(AnimationPriorityManager.PRIORITY_SAFETY);
        }

        // ======== P2 DRIVING_REACT：传感器驱动的实时驾驶反应 ========
        if (absLateral > SHARP_TURN_THRESHOLD) {
            // 急转弯：手臂指向转弯方向，皱眉，扁嘴
            mPriorityManager.request(AnimationPriorityManager.PART_ARM,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    lateral < 0 ? RobotState.ArmPose.POINT_LEFT
                            : RobotState.ArmPose.POINT_RIGHT);
            mPriorityManager.request(AnimationPriorityManager.PART_EYEBROW,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.EyebrowState.FURROWED);
            mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.MouthShape.FLAT);
        } else if (longitudinal < -HARD_BRAKE_ACCEL_THRESHOLD) {
            // 急刹车：抓握、O 嘴、皱眉、颤抖
            mPriorityManager.request(AnimationPriorityManager.PART_ARM,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.ArmPose.GRAB_HOLD);
            mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.MouthShape.OPEN_O);
            mPriorityManager.request(AnimationPriorityManager.PART_EYEBROW,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.EyebrowState.FURROWED);
            mPriorityManager.request(AnimationPriorityManager.PART_BODY,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.BodyAction.SHIVER);
        } else if (longitudinal > HARD_BRAKE_ACCEL_THRESHOLD) {
            // 急加速：双手上举、大笑、挑眉、眼睛闪光
            mPriorityManager.request(AnimationPriorityManager.PART_ARM,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.ArmPose.BOTH_UP);
            mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.MouthShape.WIDE_SMILE);
            mPriorityManager.request(AnimationPriorityManager.PART_EYEBROW,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.EyebrowState.RAISED);
            mPriorityManager.request(AnimationPriorityManager.PART_EYE,
                    AnimationPriorityManager.PRIORITY_DRIVING_REACT,
                    RobotState.EyeSpecial.SPARKLE);
        } else {
            // 力度不足，释放 P2 层
            mPriorityManager.releaseAll(AnimationPriorityManager.PRIORITY_DRIVING_REACT);
        }

        // ======== P3 SPEED_AMBIENT：速度区间基础氛围 ========
        switch (zone) {
            case HIGHWAY:
                // 高速行驶：扁嘴（专注）、皱眉
                mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                        AnimationPriorityManager.PRIORITY_SPEED_AMBIENT,
                        RobotState.MouthShape.FLAT);
                mPriorityManager.request(AnimationPriorityManager.PART_EYEBROW,
                        AnimationPriorityManager.PRIORITY_SPEED_AMBIENT,
                        RobotState.EyebrowState.FURROWED);
                break;
            case NORMAL:
                // 正常巡航：微笑
                mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                        AnimationPriorityManager.PRIORITY_SPEED_AMBIENT,
                        RobotState.MouthShape.SMILE);
                // 释放眉毛层（正常巡航不需要特殊眉毛）
                mPriorityManager.release(AnimationPriorityManager.PART_EYEBROW,
                        AnimationPriorityManager.PRIORITY_SPEED_AMBIENT);
                break;
            case CITY:
                // 市区低速：微笑
                mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                        AnimationPriorityManager.PRIORITY_SPEED_AMBIENT,
                        RobotState.MouthShape.SMILE);
                mPriorityManager.release(AnimationPriorityManager.PART_EYEBROW,
                        AnimationPriorityManager.PRIORITY_SPEED_AMBIENT);
                break;
            case PARKED:
            default:
                // 停车或其他：释放 P3 层全部
                mPriorityManager.releaseAll(AnimationPriorityManager.PRIORITY_SPEED_AMBIENT);
                break;
        }

        // ======== P4 SCENE：时间/环境场景动画 ========
        float idleSec = mEnvironmentTracker.getIdleDuration();
        if (mode == RobotState.DrivingMode.SLEEPING) {
            // 睡眠模式：瞌睡眼、中性嘴、ZZZ 气泡
            mPriorityManager.request(AnimationPriorityManager.PART_EYE,
                    AnimationPriorityManager.PRIORITY_SCENE,
                    RobotState.EyeSpecial.SLEEPY);
            mPriorityManager.request(AnimationPriorityManager.PART_MOUTH,
                    AnimationPriorityManager.PRIORITY_SCENE,
                    RobotState.MouthShape.NEUTRAL);
            mPriorityManager.request(AnimationPriorityManager.PART_BUBBLE,
                    AnimationPriorityManager.PRIORITY_SCENE,
                    RobotState.ThoughtBubbleType.ZZZ);
        } else if (idleSec > 45f) {
            // 长时间闲置（>45 秒）：打哈欠
            mPriorityManager.request(AnimationPriorityManager.PART_BODY,
                    AnimationPriorityManager.PRIORITY_SCENE,
                    RobotState.BodyAction.YAWN);
            // 释放不需要的部件
            mPriorityManager.release(AnimationPriorityManager.PART_ARM,
                    AnimationPriorityManager.PRIORITY_SCENE);
            mPriorityManager.release(AnimationPriorityManager.PART_EYEBROW,
                    AnimationPriorityManager.PRIORITY_SCENE);
            mPriorityManager.release(AnimationPriorityManager.PART_EYE,
                    AnimationPriorityManager.PRIORITY_SCENE);
            mPriorityManager.release(AnimationPriorityManager.PART_MOUTH,
                    AnimationPriorityManager.PRIORITY_SCENE);
            mPriorityManager.release(AnimationPriorityManager.PART_BUBBLE,
                    AnimationPriorityManager.PRIORITY_SCENE);
        } else if (idleSec > 30f) {
            // 中等闲置（>30 秒）：托腮思考、侧倾、单挑眉
            mPriorityManager.request(AnimationPriorityManager.PART_ARM,
                    AnimationPriorityManager.PRIORITY_SCENE,
                    RobotState.ArmPose.THINKING);
            mPriorityManager.request(AnimationPriorityManager.PART_BODY,
                    AnimationPriorityManager.PRIORITY_SCENE,
                    RobotState.BodyAction.TILT);
            mPriorityManager.request(AnimationPriorityManager.PART_EYEBROW,
                    AnimationPriorityManager.PRIORITY_SCENE,
                    RobotState.EyebrowState.ONE_UP);
            // 释放不需要的部件
            mPriorityManager.release(AnimationPriorityManager.PART_EYE,
                    AnimationPriorityManager.PRIORITY_SCENE);
            mPriorityManager.release(AnimationPriorityManager.PART_MOUTH,
                    AnimationPriorityManager.PRIORITY_SCENE);
            mPriorityManager.release(AnimationPriorityManager.PART_BUBBLE,
                    AnimationPriorityManager.PRIORITY_SCENE);
        } else {
            // 闲置时间不够，释放 P4 层全部
            mPriorityManager.releaseAll(AnimationPriorityManager.PRIORITY_SCENE);
        }

        // P5 IDLE 层由 CatAnimator 通过 getPriorityManager() 独立控制
    }

    /**
     * 从优先级管理器读取仲裁结果，安全转型后写入 RobotState
     *
     * 对每个部件调用 getEffectiveValue()，仅在返回非 null 时更新对应字段。
     * 使用 instanceof 检查防止类型转换异常。
     */
    private void applyPriorityResults() {
        Object val;

        // 手臂姿态
        val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_ARM);
        if (val instanceof RobotState.ArmPose) {
            mState.armPose = (RobotState.ArmPose) val;
        }

        // 嘴巴形状
        val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_MOUTH);
        if (val instanceof RobotState.MouthShape) {
            mState.mouthShape = (RobotState.MouthShape) val;
        }

        // 眉毛状态
        val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_EYEBROW);
        if (val instanceof RobotState.EyebrowState) {
            mState.eyebrowState = (RobotState.EyebrowState) val;
        }

        // 眼睛特效
        val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_EYE);
        if (val instanceof RobotState.EyeSpecial) {
            mState.eyeSpecial = (RobotState.EyeSpecial) val;
        }

        // 身体动作
        val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_BODY);
        if (val instanceof RobotState.BodyAction) {
            mState.bodyAction = (RobotState.BodyAction) val;
        }

        // 指示灯状态
        val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_INDICATOR);
        if (val instanceof RobotState.IndicatorState) {
            mState.indicatorState = (RobotState.IndicatorState) val;
        }

        // 思维气泡
        val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_BUBBLE);
        if (val instanceof RobotState.ThoughtBubbleType) {
            mState.thoughtBubbleType = (RobotState.ThoughtBubbleType) val;
        }

        // 天线（当前优先级系统暂未设置天线值，预留接口）
        // val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_ANTENNA);

        // 胸部（当前优先级系统暂未设置胸部值，预留接口）
        // val = mPriorityManager.getEffectiveValue(AnimationPriorityManager.PART_CHEST);
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
     * 获取优先级管理器
     *
     * 供 CatAnimator 在 P5 IDLE 层设置随机闲置动画，
     * 以及其他外部系统在 P1 AI_COMMAND 层设置 AI 交互动画。
     *
     * @return AnimationPriorityManager 实例
     */
    public AnimationPriorityManager getPriorityManager() {
        return mPriorityManager;
    }

    /**
     * 获取环境追踪器
     *
     * 供 CatAnimator 读取困倦度、驾驶模式等环境信息，
     * 用于驱动闲置动画和细节表现。
     *
     * @return EnvironmentTracker 实例
     */
    public EnvironmentTracker getEnvironmentTracker() {
        return mEnvironmentTracker;
    }

    /**
     * 获取速度追踪器
     *
     * 供外部系统读取滤波后的速度、加速度和速度区间信息。
     *
     * @return SpeedTracker 实例
     */
    public SpeedTracker getSpeedTracker() {
        return mSpeedTracker;
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

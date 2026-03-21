package com.android.launcher3.robot;

/**
 * 猫尾 6 段弹簧链物理引擎
 *
 * 使用半隐式欧拉积分（Symplectic Euler）模拟 6 段弹簧阻尼链条，
 * 实现猫尾巴的自然摆动、重力下垂和外力响应。
 *
 * 物理模型：
 * 每段独立的扭转弹簧，角度 θ[i] 围绕各自的静息角 θ_rest[i] 振荡。
 * 弹簧方程：θ'' = -K[i] * (θ - θ_rest) - D[i] * θ' + gravity_bias + external_force
 *
 * 关键设计：
 * - 根部（段0）弹簧刚度最高（K=28），尾尖（段5）最低（K=8），
 *   实现「根硬尖软」的自然柔性效果
 * - 外力通过「鞭梢放大」系数 (1 + i * 0.3) 传递到各段，
 *   越靠近尾尖放大倍数越大，模拟真实鞭子的物理行为
 * - 角度限幅 ±45° 防止尾巴穿模
 *
 * 表情影响：
 * - IDLE：温和下垂，静息角 [30,5,5,5,5,5]°
 * - EXCITED：上翘卷曲，叠加 8Hz 高频颤动（振幅 3°）
 * - SURPRISED：随机张开 + 弹簧刚度 ×3（炸毛效果） + 线宽 ×1.8
 */
public class TailPhysics {

    /** 尾巴段数 */
    private static final int SEGMENT_COUNT = 6;

    /** 每段弹簧长度（dp，已含 1.45x 缩放，根→尖递减） */
    private static final float[] SEGMENT_LENGTHS = {20f, 19f, 17f, 15f, 12f, 9f};

    /** 基础弹簧刚度（根→尖递减） */
    private static final float[] BASE_SPRING_K = {28f, 24f, 20f, 16f, 12f, 8f};

    /** 基础阻尼系数（根→尖递减） */
    private static final float[] BASE_DAMPING = {4.5f, 4.0f, 3.5f, 3.0f, 2.5f, 2.0f};

    /** 基础线宽（dp，根→尖递减） */
    private static final float[] BASE_LINE_WIDTHS = {7f, 6.5f, 5.8f, 4.4f, 3.6f, 2.2f};

    /** 每段最大角度偏移（度） */
    private static final float MAX_ANGLE = 45f;

    /** 重力偏置（度/秒²），使尾巴自然下垂 */
    private static final float GRAVITY_BIAS = 12f;

    /** 外力横向分量系数 */
    private static final float LATERAL_FORCE_SCALE = 60f;

    /** 外力偏航分量系数 */
    private static final float YAW_FORCE_SCALE = 40f;

    /** 鞭梢放大基础增量（每段递增此值） */
    private static final float WHIPLASH_INCREMENT = 0.3f;

    /** EXCITED 状态颤动频率（Hz） */
    private static final float EXCITED_TREMOR_FREQ = 8f;

    /** EXCITED 状态颤动振幅（度） */
    private static final float EXCITED_TREMOR_AMP = 3f;

    /** SURPRISED 状态弹簧刚度倍数 */
    private static final float SURPRISED_K_MULTIPLIER = 3f;

    /** SURPRISED 状态线宽倍数 */
    private static final float SURPRISED_WIDTH_MULTIPLIER = 1.8f;

    // ---- IDLE 状态静息角度（度） ----
    /** 温和下垂：根部稍微翘起，其余略微下垂 */
    private static final float[] REST_IDLE = {30f, 5f, 5f, 5f, 5f, 5f};

    // ---- EXCITED 状态静息角度（度） ----
    /** 上翘卷曲：根部近乎竖直，中段向后弯曲 */
    private static final float[] REST_EXCITED = {10f, -5f, -5f, -5f, 0f, 0f};

    // ==================== 运行时状态 ====================

    /** 各段当前角度（度） */
    private final float[] mAngles = new float[SEGMENT_COUNT];

    /** 各段当前角速度（度/秒） */
    private final float[] mAngularVel = new float[SEGMENT_COUNT];

    /** 各段当前静息角度（度），随表情变化 */
    private final float[] mRestAngles = new float[SEGMENT_COUNT];

    /** 各段当前弹簧刚度，随表情变化 */
    private final float[] mSpringK = new float[SEGMENT_COUNT];

    /** 各段当前阻尼系数，随表情变化 */
    private final float[] mDamping = new float[SEGMENT_COUNT];

    /** 各段当前线宽（dp），随表情变化 */
    private final float[] mLineWidths = new float[SEGMENT_COUNT];

    /** 当前表情状态 */
    private RobotState.Expression mExpression = RobotState.Expression.IDLE;

    /** EXCITED 颤动计时器（秒），用于 sin 波驱动 */
    private float mTremorTimer = 0f;

    /**
     * 构造尾巴物理引擎
     *
     * 初始化所有段的角度、角速度为零，弹簧参数为 IDLE 模式默认值。
     */
    public TailPhysics() {
        // 初始化为 IDLE 表情参数
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            mAngles[i] = REST_IDLE[i];
            mAngularVel[i] = 0f;
            mRestAngles[i] = REST_IDLE[i];
            mSpringK[i] = BASE_SPRING_K[i];
            mDamping[i] = BASE_DAMPING[i];
            mLineWidths[i] = BASE_LINE_WIDTHS[i];
        }
    }

    /**
     * 设置表情状态，更新弹簧参数和静息角度
     *
     * 不同表情对尾巴的影响：
     * - IDLE：标准弹簧参数，温和下垂静息角
     * - EXCITED：标准弹簧参数，上翘静息角，叠加高频颤动
     * - SURPRISED：弹簧刚度 ×3（炸毛僵硬），随机静息角 [-20,20]°，线宽 ×1.8
     *
     * @param expr 新的表情状态
     */
    public void setExpression(RobotState.Expression expr) {
        if (mExpression == expr) return;
        mExpression = expr;

        switch (expr) {
            case IDLE:
                for (int i = 0; i < SEGMENT_COUNT; i++) {
                    mRestAngles[i] = REST_IDLE[i];
                    mSpringK[i] = BASE_SPRING_K[i];
                    mDamping[i] = BASE_DAMPING[i];
                    mLineWidths[i] = BASE_LINE_WIDTHS[i];
                }
                break;

            case EXCITED:
                for (int i = 0; i < SEGMENT_COUNT; i++) {
                    mRestAngles[i] = REST_EXCITED[i];
                    mSpringK[i] = BASE_SPRING_K[i];
                    mDamping[i] = BASE_DAMPING[i];
                    mLineWidths[i] = BASE_LINE_WIDTHS[i];
                }
                break;

            case SURPRISED:
                // 炸毛：随机角度 + 高刚度 + 加粗线宽
                for (int i = 0; i < SEGMENT_COUNT; i++) {
                    mRestAngles[i] = -20f + (float) Math.random() * 40f;
                    mSpringK[i] = BASE_SPRING_K[i] * SURPRISED_K_MULTIPLIER;
                    mDamping[i] = BASE_DAMPING[i];
                    mLineWidths[i] = BASE_LINE_WIDTHS[i] * SURPRISED_WIDTH_MULTIPLIER;
                }
                break;
        }
    }

    /**
     * 物理更新步骤（每帧调用一次）
     *
     * 使用半隐式欧拉积分（同 SpringSolver 模式）：
     * 1. 计算弹簧回复力 + 阻尼力 + 重力 + 外力 + 颤动
     * 2. 先更新角速度（v += a * dt）
     * 3. 再用新角速度更新角度（θ += v * dt）
     * 4. 角度限幅到 ±MAX_ANGLE
     *
     * 外力经过鞭梢放大：越靠近尾尖的段，外力影响越大。
     * 公式：force * (1 + segmentIndex * 0.3)
     *
     * @param dt           帧间隔（秒），通常 1/60
     * @param lateralForce 横向力 [-1, 1]，来自传感器
     * @param yawRate      偏航速率 [-1, 1]，来自陀螺仪
     */
    public void update(float dt, float lateralForce, float yawRate) {
        // 防止异常帧间隔导致数值爆炸
        if (dt <= 0f || dt > 0.1f) return;

        // 计算合成外力（横向 + 偏航）
        float externalForce = lateralForce * LATERAL_FORCE_SCALE + yawRate * YAW_FORCE_SCALE;

        // 累加颤动计时器
        mTremorTimer += dt;

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            // 弹簧回复力：-K * (θ - θ_rest)
            float springForce = -mSpringK[i] * (mAngles[i] - mRestAngles[i]);

            // 阻尼力：-D * θ'
            float dampingForce = -mDamping[i] * mAngularVel[i];

            // 重力偏置（使尾巴自然下垂）
            float gravity = GRAVITY_BIAS;

            // 外力 × 鞭梢放大系数
            float whiplashMultiplier = 1f + i * WHIPLASH_INCREMENT;
            float external = externalForce * whiplashMultiplier;

            // EXCITED 状态叠加高频颤动（8Hz 正弦波）
            float tremor = 0f;
            if (mExpression == RobotState.Expression.EXCITED) {
                tremor = EXCITED_TREMOR_AMP
                        * (float) Math.sin(mTremorTimer * EXCITED_TREMOR_FREQ * Math.PI * 2);
            }

            // 合成加速度
            float acceleration = springForce + dampingForce + gravity + external + tremor;

            // 半隐式欧拉：先更新速度
            mAngularVel[i] += acceleration * dt;

            // 再用新速度更新角度
            mAngles[i] += mAngularVel[i] * dt;

            // 角度限幅（防止穿模）
            if (mAngles[i] > MAX_ANGLE) {
                mAngles[i] = MAX_ANGLE;
                if (mAngularVel[i] > 0) mAngularVel[i] = 0;
            } else if (mAngles[i] < -MAX_ANGLE) {
                mAngles[i] = -MAX_ANGLE;
                if (mAngularVel[i] < 0) mAngularVel[i] = 0;
            }
        }
    }

    /**
     * 获取当前 6 段尾巴角度
     *
     * 返回内部数组的引用（非拷贝），调用方应在同一帧内读取完毕。
     *
     * @return 6 个角度值的数组（度），索引 0=根部，5=尾尖
     */
    public float[] getAngles() {
        return mAngles;
    }

    /**
     * 获取当前 6 段尾巴线宽
     *
     * 线宽受 SURPRISED 表情影响（×1.8 炸毛效果）。
     * 返回内部数组引用。
     *
     * @return 6 个线宽值的数组（dp），索引 0=根部（最粗），5=尾尖（最细）
     */
    public float[] getLineWidths() {
        return mLineWidths;
    }

    /**
     * 获取当前角速度数组
     *
     * @return 6 个角速度值的数组（度/秒）
     */
    public float[] getAngularVelocities() {
        return mAngularVel;
    }
}

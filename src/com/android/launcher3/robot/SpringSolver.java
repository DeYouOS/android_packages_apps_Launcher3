package com.android.launcher3.robot;

/**
 * 弹簧-阻尼微分方程求解器
 *
 * 使用半隐式欧拉积分（Semi-Implicit Euler / Symplectic Euler）求解二阶弹簧方程：
 *   x'' = -stiffness * (x - target) - 2 * dampingRatio * √stiffness * x'
 *
 * 半隐式欧拉相比显式欧拉的优势：
 * - 先更新速度，再用新速度更新位置，能量守恒性更好
 * - 在相同步长下数值稳定性更高，不易发散
 * - 计算量与显式欧拉相同，适合实时动画场景
 *
 * 典型参数范围：
 * - stiffness（刚度）：50~500，值越大弹簧越硬、响应越快
 * - dampingRatio（阻尼比）：
 *   - <1.0 欠阻尼（有振荡）
 *   - =1.0 临界阻尼（最快无振荡收敛）
 *   - >1.0 过阻尼（缓慢收敛）
 */
public class SpringSolver {

    /** 弹簧刚度：决定回复力的强度 */
    private final float mStiffness;

    /**
     * 阻尼系数（预计算值）
     * = 2 * dampingRatio * √stiffness
     * 预计算避免每帧重复开方运算
     */
    private final float mDamping;

    /** 当前位置 */
    private float mPosition;

    /** 当前速度 */
    private float mVelocity;

    /** 目标位置（弹簧自然长度对应的平衡点） */
    private float mTarget;

    /**
     * 构造弹簧求解器
     *
     * @param stiffness    刚度系数（>0），值越大弹簧越硬
     * @param dampingRatio 阻尼比（>0），1.0 为临界阻尼
     */
    public SpringSolver(float stiffness, float dampingRatio) {
        mStiffness = stiffness;
        // 预计算阻尼系数：c = 2 * ζ * √k
        mDamping = 2f * dampingRatio * (float) Math.sqrt(stiffness);
        mPosition = 0f;
        mVelocity = 0f;
        mTarget = 0f;
    }

    /**
     * 设置弹簧目标位置
     *
     * 弹簧会从当前位置向目标位置运动，运动特性由刚度和阻尼决定。
     *
     * @param target 新的目标位置
     */
    public void setTarget(float target) {
        mTarget = target;
    }

    /**
     * 获取当前弹簧位置
     *
     * @return 当前位置值
     */
    public float getPosition() {
        return mPosition;
    }

    /**
     * 获取当前弹簧速度
     *
     * @return 当前速度值
     */
    public float getVelocity() {
        return mVelocity;
    }

    /**
     * 执行一步积分
     *
     * 使用半隐式欧拉方法（Symplectic Euler）：
     * 1. 先用当前状态计算加速度
     * 2. 用加速度更新速度（v_new = v + a * dt）
     * 3. 用 **新速度** 更新位置（x_new = x + v_new * dt）
     *
     * 与显式欧拉（先更新位置）相比，半隐式欧拉的关键区别在第 3 步：
     * 使用已更新的速度计算位置，这使得积分器具有辛性质（symplectic），
     * 长期能量误差有界，不会像显式欧拉那样持续积累能量导致爆炸。
     *
     * @param dt 时间步长（秒），建议 1/60 ~ 1/120
     */
    public void step(float dt) {
        // 位移：当前位置与目标的偏差
        float displacement = mPosition - mTarget;

        /*
         * 加速度 = -k * displacement - c * velocity
         * 其中 k = stiffness, c = 2 * dampingRatio * √stiffness
         * 第一项为弹性回复力，第二项为阻尼力（与速度方向相反）
         */
        float acceleration = -mStiffness * displacement - mDamping * mVelocity;

        // 步骤 1：先更新速度（使用当前加速度）
        mVelocity += acceleration * dt;

        // 步骤 2：再用新速度更新位置（半隐式欧拉的核心）
        mPosition += mVelocity * dt;
    }

    /**
     * 判断弹簧是否已趋于静止
     *
     * 当位移偏差和速度均低于阈值时认为弹簧已收敛，
     * 可用于停止不必要的动画更新以节省资源。
     *
     * @param threshold 静止判定阈值，位移和速度的绝对值均需低于此值
     * @return true 表示弹簧已趋于静止
     */
    public boolean isAtRest(float threshold) {
        return Math.abs(mPosition - mTarget) < threshold
                && Math.abs(mVelocity) < threshold;
    }
}

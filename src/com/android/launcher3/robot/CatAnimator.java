package com.android.launcher3.robot;

import java.util.Random;

/**
 * 赛博朋克霓虹猫动画状态机
 *
 * 每帧更新 RobotState 中的所有动画参数，包括：
 * 1. 发光呼吸效果：更新 glowPulsePhase，计算 glowIntensity 和 bodyScale
 * 2. 眨眼循环（仅 IDLE）：3-6 秒随机间隔，150ms 完整眨眼，20% 概率双眨
 * 3. 耳朵抽动（仅 IDLE）：4-8 秒随机间隔，单耳(70%)或双耳(30%)
 * 4. 表情驱动的耳朵角度、瞳孔大小、呼噜振幅
 * 5. 表情过渡：expressionBlend 在 200ms 内从 0 过渡到 1，插值所有参数
 * 6. SURPRISED 进入时的身体震动和粒子爆发请求
 * 7. 尾巴静息角度（通过 TailPhysics.setExpression 设置）
 *
 * 不依赖外部库，仅使用 java.util.Random 生成随机间隔。
 */
public class CatAnimator {

    // ---- 发光呼吸参数 ----
    /** 呼吸发光频率（弧度/秒），约 0.8Hz */
    private static final float GLOW_PULSE_SPEED = 5.0f;

    // ---- 眨眼参数 ----
    /** 眨眼最小间隔（秒） */
    private static final float BLINK_INTERVAL_MIN = 3.0f;
    /** 眨眼最大间隔（秒） */
    private static final float BLINK_INTERVAL_MAX = 6.0f;
    /** 完整眨眼持续时间（秒） */
    private static final float BLINK_DURATION = 0.150f;
    /** 闭眼下降阶段结束时间（秒） */
    private static final float BLINK_DOWN_END = 0.060f;
    /** 闭眼保持阶段结束时间（秒） */
    private static final float BLINK_HOLD_END = 0.080f;
    /** 双眨概率 */
    private static final float DOUBLE_BLINK_CHANCE = 0.20f;
    /** 双眨间隔（秒） */
    private static final float DOUBLE_BLINK_GAP = 0.100f;

    // ---- 耳朵抽动参数 ----
    /** 耳朵抽动最小间隔（秒） */
    private static final float EAR_TWITCH_INTERVAL_MIN = 4.0f;
    /** 耳朵抽动最大间隔（秒） */
    private static final float EAR_TWITCH_INTERVAL_MAX = 8.0f;
    /** 耳朵抽动完整持续时间（秒） */
    private static final float EAR_TWITCH_DURATION = 0.300f;
    /** 双耳抽动概率 */
    private static final float BOTH_EARS_CHANCE = 0.30f;

    // ---- 表情过渡参数 ----
    /** 表情过渡持续时间（秒） */
    private static final float EXPRESSION_TRANSITION_DURATION = 0.200f;

    // ---- SURPRISED 震动参数 ----
    /** SURPRISED 进入时身体震动幅度（dp） */
    private static final float SURPRISED_SHAKE_AMPLITUDE = 2.0f;
    /** SURPRISED 震动衰减持续时间（秒） */
    private static final float SURPRISED_SHAKE_DURATION = 0.100f;
    /** SURPRISED 进入时粒子爆发数量 */
    private static final int SURPRISED_BURST_COUNT = 20;

    // ---- 各表情目标参数 ----
    /** IDLE 耳朵角度 */
    private static final float EAR_ANGLE_IDLE = 0f;
    /** EXCITED 耳朵角度（前倾） */
    private static final float EAR_ANGLE_EXCITED = -10f;
    /** SURPRISED 耳朵角度（后仰） */
    private static final float EAR_ANGLE_SURPRISED = 25f;

    /** IDLE 瞳孔扩张系数 */
    private static final float PUPIL_DILATION_IDLE = 1.0f;
    /** EXCITED 瞳孔扩张系数 */
    private static final float PUPIL_DILATION_EXCITED = 1.3f;
    /** SURPRISED 瞳孔扩张系数（收缩） */
    private static final float PUPIL_DILATION_SURPRISED = 0.6f;

    /** EXCITED 呼噜振幅 */
    private static final float PURRING_EXCITED = 0.6f;

    /** 随机数生成器，用于眨眼/抽动间隔的随机化 */
    private final Random mRandom;

    // ---- 眨眼状态 ----
    /** 距离下次眨眼的剩余时间（秒） */
    private float mNextBlinkCountdown;
    /** 当前眨眼进度计时器（秒），-1 表示未在眨眼 */
    private float mBlinkProgress;
    /** 当前是否处于双眨的第二次眨眼 */
    private boolean mIsDoubleBlink;
    /** 双眨间隔倒计时（秒） */
    private float mDoubleBlinkGapTimer;
    /** 是否需要执行第二次眨眼 */
    private boolean mPendingSecondBlink;

    // ---- 耳朵抽动状态 ----
    /** 距离下次耳朵抽动的剩余时间（秒） */
    private float mNextEarTwitchCountdown;
    /** 当前耳朵抽动进度计时器（秒），-1 表示未在抽动 */
    private float mEarTwitchProgress;
    /** 当前抽动是否影响左耳 */
    private boolean mTwitchLeftEar;
    /** 当前抽动是否影响右耳 */
    private boolean mTwitchRightEar;

    // ---- 表情过渡状态 ----
    /** 上一帧的表情，用于检测表情变化 */
    private RobotState.Expression mPrevExpression;
    /** 表情过渡进度（0~1） */
    private float mExpressionBlendProgress;

    // ---- 各参数的当前平滑值（用于过渡插值） ----
    /** 当前平滑耳朵角度（左） */
    private float mSmoothedLeftEarAngle;
    /** 当前平滑耳朵角度（右） */
    private float mSmoothedRightEarAngle;
    /** 当前平滑瞳孔扩张系数 */
    private float mSmoothedPupilDilation;
    /** 当前平滑呼噜振幅 */
    private float mSmoothedPurring;

    // ---- SURPRISED 震动状态 ----
    /** SURPRISED 震动已经经过的时间（秒） */
    private float mShakeTimer;
    /** 是否正在执行 SURPRISED 震动 */
    private boolean mShaking;

    /**
     * 构造动画状态机
     *
     * 初始化随机数生成器和所有内部计时器。
     * 眨眼和耳朵抽动的首次触发设为各自区间内的随机值。
     */
    public CatAnimator() {
        mRandom = new Random();

        // 初始化眨眼倒计时
        mNextBlinkCountdown = randomRange(BLINK_INTERVAL_MIN, BLINK_INTERVAL_MAX);
        mBlinkProgress = -1f;
        mIsDoubleBlink = false;
        mDoubleBlinkGapTimer = 0f;
        mPendingSecondBlink = false;

        // 初始化耳朵抽动倒计时
        mNextEarTwitchCountdown = randomRange(EAR_TWITCH_INTERVAL_MIN, EAR_TWITCH_INTERVAL_MAX);
        mEarTwitchProgress = -1f;
        mTwitchLeftEar = false;
        mTwitchRightEar = false;

        // 表情过渡初始状态
        mPrevExpression = RobotState.Expression.IDLE;
        mExpressionBlendProgress = 1.0f; // 初始已完成过渡

        // 平滑参数初始值
        mSmoothedLeftEarAngle = EAR_ANGLE_IDLE;
        mSmoothedRightEarAngle = EAR_ANGLE_IDLE;
        mSmoothedPupilDilation = PUPIL_DILATION_IDLE;
        mSmoothedPurring = 0f;

        // 震动状态
        mShakeTimer = 0f;
        mShaking = false;
    }

    /**
     * 每帧更新动画状态
     *
     * 按以下顺序更新所有动画参数：
     * 1. 发光呼吸（相位递增 + 强度/缩放计算）
     * 2. 表情过渡检测与 blend 值更新
     * 3. 表情驱动参数（耳朵角度、瞳孔大小、呼噜）平滑过渡
     * 4. 眨眼循环（仅 IDLE 时触发）
     * 5. 耳朵抽动（仅 IDLE 时触发，叠加在表情角度之上）
     * 6. SURPRISED 震动效果
     * 7. 写入所有结果到 RobotState
     *
     * @param dt    帧间隔时间（秒），通常为 1/60
     * @param state 当前机器人状态，方法将修改其动画参数字段
     */
    public void update(float dt, RobotState state) {
        // ---- 1. 发光呼吸 ----
        state.glowPulsePhase += GLOW_PULSE_SPEED * dt;
        // 保持相位在 [0, 2π) 范围内，防止浮点精度问题
        if (state.glowPulsePhase > (float)(Math.PI * 2.0)) {
            state.glowPulsePhase -= (float)(Math.PI * 2.0);
        }

        // 根据表情计算发光强度和身体缩放的基础值
        float baseGlowIntensity;
        float baseBodyScale;
        float glowAmplitude;
        float scaleAmplitude;

        switch (state.expression) {
            case EXCITED:
                baseGlowIntensity = 0.75f;
                glowAmplitude = 0.25f;     // 0.5 ~ 1.0
                baseBodyScale = 1.0f;
                scaleAmplitude = 0.02f;    // 0.98 ~ 1.02
                break;
            case SURPRISED:
                baseGlowIntensity = 0.9f;
                glowAmplitude = 0.1f;      // 0.8 ~ 1.0
                baseBodyScale = 1.0f;
                scaleAmplitude = 0.01f;    // 0.99 ~ 1.01
                break;
            default: // IDLE
                baseGlowIntensity = 0.5f;
                glowAmplitude = 0.3f;      // 0.2 ~ 0.8
                baseBodyScale = 1.0f;
                scaleAmplitude = 0.015f;   // 0.985 ~ 1.015
                break;
        }

        float sinPhase = (float) Math.sin(state.glowPulsePhase);
        state.glowIntensity = baseGlowIntensity + glowAmplitude * sinPhase;
        state.glowIntensity = clamp(state.glowIntensity, 0.2f, 1.0f);
        state.bodyScale = baseBodyScale + scaleAmplitude * sinPhase;
        state.bodyScale = clamp(state.bodyScale, 0.98f, 1.02f);

        // ---- 2. 表情过渡检测 ----
        if (state.expression != mPrevExpression) {
            // 表情发生变化，开始过渡
            mExpressionBlendProgress = 0f;

            // SURPRISED 进入效果
            if (state.expression == RobotState.Expression.SURPRISED) {
                mShaking = true;
                mShakeTimer = 0f;
                state.particleBurstRequest = SURPRISED_BURST_COUNT;
            }

            mPrevExpression = state.expression;
        }

        // 更新过渡进度
        if (mExpressionBlendProgress < 1.0f) {
            mExpressionBlendProgress += dt / EXPRESSION_TRANSITION_DURATION;
            if (mExpressionBlendProgress > 1.0f) {
                mExpressionBlendProgress = 1.0f;
            }
        }
        state.expressionBlend = mExpressionBlendProgress;

        // ---- 3. 表情驱动参数平滑过渡 ----
        float targetEarAngle;
        float targetPupilDilation;
        float targetPurring;

        switch (state.expression) {
            case EXCITED:
                targetEarAngle = EAR_ANGLE_EXCITED;
                targetPupilDilation = PUPIL_DILATION_EXCITED;
                targetPurring = PURRING_EXCITED;
                break;
            case SURPRISED:
                targetEarAngle = EAR_ANGLE_SURPRISED;
                targetPupilDilation = PUPIL_DILATION_SURPRISED;
                targetPurring = 0f;
                break;
            default: // IDLE
                targetEarAngle = EAR_ANGLE_IDLE;
                targetPupilDilation = PUPIL_DILATION_IDLE;
                targetPurring = 0f;
                break;
        }

        // 使用 expressionBlend 进行线性插值
        float blendT = mExpressionBlendProgress;
        mSmoothedLeftEarAngle = lerp(mSmoothedLeftEarAngle, targetEarAngle, blendT);
        mSmoothedRightEarAngle = lerp(mSmoothedRightEarAngle, targetEarAngle, blendT);
        mSmoothedPupilDilation = lerp(mSmoothedPupilDilation, targetPupilDilation, blendT);
        mSmoothedPurring = lerp(mSmoothedPurring, targetPurring, blendT);

        // 初始化耳朵角度为表情驱动值（后续可能叠加抽动偏移）
        float leftEarAngle = mSmoothedLeftEarAngle;
        float rightEarAngle = mSmoothedRightEarAngle;

        // ---- 4. 眨眼循环（仅 IDLE） ----
        float eyeOpenness = 1.0f;

        if (state.expression == RobotState.Expression.IDLE) {
            // 处理双眨间隔
            if (mPendingSecondBlink) {
                mDoubleBlinkGapTimer -= dt;
                if (mDoubleBlinkGapTimer <= 0f) {
                    // 开始第二次眨眼
                    mBlinkProgress = 0f;
                    mPendingSecondBlink = false;
                    mIsDoubleBlink = true;
                }
            }

            // 眨眼倒计时
            if (mBlinkProgress < 0f && !mPendingSecondBlink) {
                mNextBlinkCountdown -= dt;
                if (mNextBlinkCountdown <= 0f) {
                    // 触发眨眼
                    mBlinkProgress = 0f;
                    mIsDoubleBlink = false;
                    mNextBlinkCountdown = randomRange(BLINK_INTERVAL_MIN, BLINK_INTERVAL_MAX);
                }
            }

            // 执行眨眼动画
            if (mBlinkProgress >= 0f) {
                eyeOpenness = computeBlinkOpenness(mBlinkProgress);
                mBlinkProgress += dt;

                if (mBlinkProgress >= BLINK_DURATION) {
                    // 眨眼完成
                    mBlinkProgress = -1f;

                    // 首次眨眼完成后，20% 概率触发双眨
                    if (!mIsDoubleBlink && mRandom.nextFloat() < DOUBLE_BLINK_CHANCE) {
                        mPendingSecondBlink = true;
                        mDoubleBlinkGapTimer = DOUBLE_BLINK_GAP;
                    }
                }
            }
        } else {
            // 非 IDLE 表情：根据表情设置眼睛张开度
            switch (state.expression) {
                case EXCITED:
                    eyeOpenness = 1.2f;
                    break;
                case SURPRISED:
                    eyeOpenness = 1.4f;
                    break;
                default:
                    eyeOpenness = 1.0f;
                    break;
            }
            // 重置眨眼状态，切回 IDLE 时重新开始计时
            mBlinkProgress = -1f;
            mPendingSecondBlink = false;
        }

        // ---- 5. 耳朵抽动（仅 IDLE） ----
        if (state.expression == RobotState.Expression.IDLE) {
            // 抽动倒计时
            if (mEarTwitchProgress < 0f) {
                mNextEarTwitchCountdown -= dt;
                if (mNextEarTwitchCountdown <= 0f) {
                    // 触发抽动
                    mEarTwitchProgress = 0f;
                    mNextEarTwitchCountdown = randomRange(
                            EAR_TWITCH_INTERVAL_MIN, EAR_TWITCH_INTERVAL_MAX);

                    // 决定单耳还是双耳
                    if (mRandom.nextFloat() < BOTH_EARS_CHANCE) {
                        mTwitchLeftEar = true;
                        mTwitchRightEar = true;
                    } else {
                        // 随机选择单耳
                        mTwitchLeftEar = mRandom.nextBoolean();
                        mTwitchRightEar = !mTwitchLeftEar;
                    }
                }
            }

            // 执行抽动动画
            if (mEarTwitchProgress >= 0f) {
                float twitchOffset = computeEarTwitchOffset(mEarTwitchProgress);
                if (mTwitchLeftEar) {
                    leftEarAngle += twitchOffset;
                }
                if (mTwitchRightEar) {
                    rightEarAngle += twitchOffset;
                }

                mEarTwitchProgress += dt;
                if (mEarTwitchProgress >= EAR_TWITCH_DURATION) {
                    mEarTwitchProgress = -1f;
                }
            }
        } else {
            // 非 IDLE：重置抽动状态
            mEarTwitchProgress = -1f;
        }

        // ---- 6. SURPRISED 震动 ----
        float shakeOffsetX = 0f;
        if (mShaking) {
            mShakeTimer += dt;
            if (mShakeTimer < SURPRISED_SHAKE_DURATION) {
                // 衰减震动：高频正弦 × 线性衰减包络
                float decay = 1.0f - (mShakeTimer / SURPRISED_SHAKE_DURATION);
                float freq = 40f; // 高频震动
                shakeOffsetX = SURPRISED_SHAKE_AMPLITUDE * decay
                        * (float) Math.sin(mShakeTimer * freq * Math.PI * 2.0);
            } else {
                mShaking = false;
                shakeOffsetX = 0f;
            }
        }

        // ---- 7. 写入结果到 RobotState ----
        // 耳朵角度（限制在 [-30, 30] 范围内）
        state.leftEarAngle = clamp(leftEarAngle, -30f, 30f);
        state.rightEarAngle = clamp(rightEarAngle, -30f, 30f);

        // 眼睛
        state.eyeOpenness = clamp(eyeOpenness, 0f, 1.4f);
        state.pupilDilation = clamp(mSmoothedPupilDilation, 0.6f, 1.3f);

        // 呼噜
        state.purringAmplitude = clamp(mSmoothedPurring, 0f, 1f);

        // 震动偏移叠加到 bodyX（不修改弹簧位置，仅视觉偏移）
        // 注意：这里直接叠加到 bodyX，物理引擎每帧会重新设置基础值
        // 所以震动是"叠加"效果——物理引擎 update 先执行，然后 CatAnimator 叠加震动
        state.bodyX += shakeOffsetX;

        // 累加各计时器
        state.idleTimer += dt;
        state.blinkTimer += dt;
        state.earTwitchTimer += dt;
    }

    /**
     * 计算眨眼过程中的眼睛张开度
     *
     * 三阶段动画：
     * - 0~60ms：下降阶段，easeInQuad (1.0 → 0.0)
     * - 60~80ms：保持阶段 (0.0)
     * - 80~150ms：上升阶段，easeOutQuad (0.0 → 1.0)
     *
     * @param progress 当前眨眼进度（秒）
     * @return 眼睛张开度（0.0=完全闭合，1.0=完全张开）
     */
    private float computeBlinkOpenness(float progress) {
        if (progress < BLINK_DOWN_END) {
            // 下降阶段：easeInQuad
            float t = progress / BLINK_DOWN_END;
            float eased = t * t; // easeInQuad
            return 1.0f - eased;
        } else if (progress < BLINK_HOLD_END) {
            // 保持闭合
            return 0f;
        } else if (progress < BLINK_DURATION) {
            // 上升阶段：easeOutQuad
            float t = (progress - BLINK_HOLD_END) / (BLINK_DURATION - BLINK_HOLD_END);
            float eased = 1.0f - (1.0f - t) * (1.0f - t); // easeOutQuad
            return eased;
        }
        return 1.0f;
    }

    /**
     * 计算耳朵抽动的角度偏移
     *
     * 使用关键帧插值：
     * - 0ms = 0°
     * - 80ms = -12°（向前快速抖）
     * - 180ms = +5°（回弹超调）
     * - 300ms = 0°（恢复）
     *
     * @param progress 当前抽动进度（秒）
     * @return 角度偏移（度）
     */
    private float computeEarTwitchOffset(float progress) {
        // 关键帧时间点（秒）
        final float t1 = 0.080f;
        final float t2 = 0.180f;
        final float t3 = 0.300f;

        // 关键帧角度值（度）
        final float v0 = 0f;
        final float v1 = -12f;
        final float v2 = 5f;
        final float v3 = 0f;

        if (progress < t1) {
            // 0 → -12°
            float t = progress / t1;
            return lerp(v0, v1, t);
        } else if (progress < t2) {
            // -12° → +5°
            float t = (progress - t1) / (t2 - t1);
            return lerp(v1, v2, t);
        } else if (progress < t3) {
            // +5° → 0°
            float t = (progress - t2) / (t3 - t2);
            return lerp(v2, v3, t);
        }
        return 0f;
    }

    /**
     * 线性插值
     *
     * @param a 起始值
     * @param b 目标值
     * @param t 插值参数（0~1）
     * @return 插值结果
     */
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * 数值钳位
     *
     * @param value 输入值
     * @param min   最小值
     * @param max   最大值
     * @return 钳位后的值
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 在指定范围内生成随机浮点数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return [min, max] 范围内的随机浮点数
     */
    private float randomRange(float min, float max) {
        return min + mRandom.nextFloat() * (max - min);
    }
}

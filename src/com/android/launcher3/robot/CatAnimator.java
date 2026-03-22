package com.android.launcher3.robot;

import java.util.Random;

/**
 * 赛博朋克霓虹猫动画状态机（完整版）
 *
 * 每帧在 RobotPhysicsEngine 之后执行，负责所有动画子系统的更新：
 *
 *  1. 发光呼吸效果 — glowPulsePhase / glowIntensity / bodyScale
 *  2. 表情过渡 — expressionBlend 及驱动参数平滑
 *  3. 眨眼循环 — 支持困倦度（sleepiness）影响频率和最大张开度
 *  4. 耳朵抽动 — 仅 IDLE 时随机触发单/双耳抽动
 *  5. P5 空闲手臂姿态循环 — 无高优先级时随机切换空闲姿势
 *  6. 嘴巴形状动画 — 形状混合过渡 + 张开度 + TTS 振幅同步
 *  7. 眉毛角度动画 — 状态目标角度线性插值
 *  8. 身体动作执行 — 点头/摇头/弹跳/颤抖/侧倾/打哈欠/伸展 运动曲线
 *  9. 眼睛特效推进 — 闪烁/晕眩/爱心/瞌睡 特效强度驱动
 * 10. 思维气泡动画 — 淡入/淡出/持续推进
 * 11. 天线发光相位 — 连续旋转推进
 * 12. 指示灯动画 — 警告/错误闪烁计时、AI 活跃脉冲
 * 13. 困倦度整合 — sleepiness 影响眨眼频率和眼睛张开度上限
 * 14. TTS 嘴巴同步 — ttsAmplitude → mouthOpenness
 * 15. 开机/唤醒序列 — 前 3 秒：STRETCH → WAVE → IDLE
 *
 * 不依赖外部库，仅使用 java.util.Random。
 * 不访问 AnimationPriorityManager，仅读取/写入 RobotState 字段。
 */
public class CatAnimator {

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                       常 量 定 义                            ║
    // ╚══════════════════════════════════════════════════════════════╝

    // ---- 发光呼吸 ----
    /** 呼吸发光频率（弧度/秒），约 0.8Hz */
    private static final float GLOW_PULSE_SPEED = 5.0f;

    // ---- 眨眼基准参数 ----
    /** 眨眼最小间隔（秒）— 清醒时 */
    private static final float BLINK_INTERVAL_MIN = 3.0f;
    /** 眨眼最大间隔（秒）— 清醒时 */
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

    // ---- 困倦眨眼参数 ----
    /** 困倦时眨眼最小间隔（秒） */
    private static final float BLINK_INTERVAL_MIN_DROWSY = 1.5f;
    /** 困倦时眨眼最大间隔（秒） */
    private static final float BLINK_INTERVAL_MAX_DROWSY = 3.0f;
    /** 困倦时最大眼睛张开度 */
    private static final float EYE_OPENNESS_MIN_DROWSY = 0.4f;
    /** 困倦度阈值：超过此值开始影响眨眼 */
    private static final float SLEEPINESS_THRESHOLD = 0.3f;

    // ---- 耳朵抽动 ----
    /** 耳朵抽动最小间隔（秒） */
    private static final float EAR_TWITCH_INTERVAL_MIN = 4.0f;
    /** 耳朵抽动最大间隔（秒） */
    private static final float EAR_TWITCH_INTERVAL_MAX = 8.0f;
    /** 耳朵抽动持续时间（秒） */
    private static final float EAR_TWITCH_DURATION = 0.300f;
    /** 双耳抽动概率 */
    private static final float BOTH_EARS_CHANCE = 0.30f;

    // ---- 表情过渡 ----
    /** 表情过渡持续时间（秒） */
    private static final float EXPRESSION_TRANSITION_DURATION = 0.200f;

    // ---- SURPRISED 震动 ----
    /** SURPRISED 进入时身体震动幅度 */
    private static final float SURPRISED_SHAKE_AMPLITUDE = 2.0f;
    /** SURPRISED 震动衰减持续时间（秒） */
    private static final float SURPRISED_SHAKE_DURATION = 0.100f;
    /** SURPRISED 进入时粒子爆发数量 */
    private static final int SURPRISED_BURST_COUNT = 20;

    // ---- 表情驱动参数 ----
    private static final float EAR_ANGLE_IDLE = 0f;
    private static final float EAR_ANGLE_EXCITED = -10f;
    private static final float EAR_ANGLE_SURPRISED = 25f;
    private static final float PUPIL_DILATION_IDLE = 1.0f;
    private static final float PUPIL_DILATION_EXCITED = 1.3f;
    private static final float PUPIL_DILATION_SURPRISED = 0.6f;
    private static final float PURRING_EXCITED = 0.6f;

    // ---- P5 空闲手臂循环 ----
    /** 空闲手臂切换最小间隔（秒） */
    private static final float IDLE_ARM_INTERVAL_MIN = 8.0f;
    /** 空闲手臂切换最大间隔（秒） */
    private static final float IDLE_ARM_INTERVAL_MAX = 15.0f;
    /** WAVE 自动恢复时长（秒） */
    private static final float IDLE_ARM_WAVE_REVERT = 3.0f;
    /** THINKING 自动恢复时长（秒） */
    private static final float IDLE_ARM_THINKING_REVERT = 8.0f;
    /** 可选的空闲手臂姿势池 */
    private static final RobotState.ArmPose[] IDLE_ARM_POSES = {
            RobotState.ArmPose.IDLE_SIDE,
            RobotState.ArmPose.WAVE,
            RobotState.ArmPose.THINKING
    };

    // ---- 嘴巴混合过渡 ----
    /** 嘴巴形状过渡时长（秒） */
    private static final float MOUTH_BLEND_DURATION = 0.200f;

    // ---- 眉毛混合过渡 ----
    /** 眉毛状态过渡时长（秒） */
    private static final float EYEBROW_BLEND_DURATION = 0.200f;

    // ---- 身体动作时长 ----
    private static final float DURATION_NOD = 0.4f;
    private static final float DURATION_SHAKE_HEAD = 0.6f;
    private static final float DURATION_BOUNCE = 0.5f;
    private static final float DURATION_SHIVER = 0.3f;
    private static final float DURATION_YAWN = 2.0f;
    private static final float DURATION_STRETCH = 1.5f;
    // TILT 无固定时长，持续激活

    // ---- 思维气泡 ----
    /** 思维气泡淡入时长（秒） */
    private static final float THOUGHT_FADE_IN = 0.300f;
    /** 思维气泡淡出时长（秒） */
    private static final float THOUGHT_FADE_OUT = 0.200f;

    // ---- 天线发光速度 ----
    /** 天线发光相位推进速度（弧度/秒） */
    private static final float ANTENNA_GLOW_SPEED = 3.0f;

    // ---- 指示灯闪烁间隔 ----
    /** WARNING 闪烁半周期（秒） */
    private static final float INDICATOR_WARNING_PERIOD = 0.300f;
    /** ERROR 闪烁半周期（秒） */
    private static final float INDICATOR_ERROR_PERIOD = 0.150f;

    // ---- 开机序列 ----
    /** 开机序列总时长（秒） */
    private static final float BOOT_TOTAL_DURATION = 3.0f;

    /** 2π 常量 */
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                     实 例 字 段                               ║
    // ╚══════════════════════════════════════════════════════════════╝

    /** 随机数生成器 */
    private final Random mRandom;

    // ---- 眨眼状态 ----
    /** 距离下次眨眼的剩余时间（秒） */
    private float mNextBlinkCountdown;
    /** 当前眨眼进度计时器（秒），-1 = 未在眨眼 */
    private float mBlinkProgress;
    /** 当前是否处于双眨的第二次 */
    private boolean mIsDoubleBlink;
    /** 双眨间隔倒计时（秒） */
    private float mDoubleBlinkGapTimer;
    /** 是否需要执行第二次眨眼 */
    private boolean mPendingSecondBlink;

    // ---- 耳朵抽动状态 ----
    /** 距离下次耳朵抽动的剩余时间（秒） */
    private float mNextEarTwitchCountdown;
    /** 当前耳朵抽动进度（秒），-1 = 未在抽动 */
    private float mEarTwitchProgress;
    /** 当前抽动影响左耳 */
    private boolean mTwitchLeftEar;
    /** 当前抽动影响右耳 */
    private boolean mTwitchRightEar;

    // ---- 表情过渡状态 ----
    /** 上一帧的表情 */
    private RobotState.Expression mPrevExpression;
    /** 表情过渡进度（0~1） */
    private float mExpressionBlendProgress;

    // ---- 表情参数平滑值 ----
    private float mSmoothedLeftEarAngle;
    private float mSmoothedRightEarAngle;
    private float mSmoothedPupilDilation;
    private float mSmoothedPurring;

    // ---- SURPRISED 震动状态 ----
    private float mShakeTimer;
    private boolean mShaking;

    // ---- P5 空闲手臂循环 ----
    /** 空闲手臂切换倒计时（秒） */
    private float mIdleArmCountdown;
    /** 空闲手臂自动恢复倒计时（秒），≤0 表示无需恢复 */
    private float mIdleArmRevertCountdown;
    /** 当前空闲手臂姿势（由本模块管理） */
    private RobotState.ArmPose mCurrentIdleArmPose;

    // ---- 嘴巴动画状态 ----
    /** 当前已完成混合的嘴巴形状 */
    private RobotState.MouthShape mCurrentMouthShape;
    /** 混合目标嘴巴形状 */
    private RobotState.MouthShape mTargetMouthShape;
    /** 嘴巴混合计时器（秒），-1 = 未在过渡 */
    private float mMouthBlendTimer;

    // ---- 眉毛动画状态 ----
    /** 当前已完成混合的眉毛状态 */
    private RobotState.EyebrowState mCurrentEyebrowState;
    /** 混合目标眉毛状态 */
    private RobotState.EyebrowState mTargetEyebrowState;
    /** 眉毛混合计时器（秒），-1 = 未在过渡 */
    private float mEyebrowBlendTimer;
    /** 当前平滑左眉角度 */
    private float mSmoothedLeftEyebrowAngle;
    /** 当前平滑右眉角度 */
    private float mSmoothedRightEyebrowAngle;

    // ---- 身体动作状态 ----
    /** 当前正在执行的身体动作 */
    private RobotState.BodyAction mActiveBodyAction;
    /** 身体动作已经过的时间（秒） */
    private float mBodyActionTimer;
    /** 当前身体动作的总时长（秒），TILT 为 -1 表示持续 */
    private float mBodyActionDuration;

    // ---- 思维气泡状态 ----
    /** 上一帧的思维气泡类型，用于检测变化 */
    private RobotState.ThoughtBubbleType mPrevThoughtBubbleType;
    /** 气泡正在淡入（true）还是淡出（false） */
    private boolean mThoughtFadingIn;

    // ---- 指示灯闪烁状态 ----
    /** 指示灯闪烁计时器（秒） */
    private float mIndicatorFlashTimer;
    /** 指示灯当前是否亮起（用于方波闪烁） */
    private boolean mIndicatorOn;

    // ---- 开机序列 ----
    /** 开机计时器（秒），从 0 递增到 BOOT_TOTAL_DURATION */
    private float mBootTimer;
    /** 开机序列是否已完成 */
    private boolean mBootComplete;

    // ---- 全局累计时间（用于持续型动画计算） ----
    private float mGlobalTime;

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                        构 造 函 数                            ║
    // ╚══════════════════════════════════════════════════════════════╝

    /**
     * 构造动画状态机
     *
     * 初始化随机数生成器和所有内部计时器/状态追踪器。
     * 眨眼和耳朵抽动的首次触发设为各自区间内的随机值。
     */
    public CatAnimator() {
        mRandom = new Random();

        // 眨眼
        mNextBlinkCountdown = randomRange(BLINK_INTERVAL_MIN, BLINK_INTERVAL_MAX);
        mBlinkProgress = -1f;
        mIsDoubleBlink = false;
        mDoubleBlinkGapTimer = 0f;
        mPendingSecondBlink = false;

        // 耳朵抽动
        mNextEarTwitchCountdown = randomRange(EAR_TWITCH_INTERVAL_MIN, EAR_TWITCH_INTERVAL_MAX);
        mEarTwitchProgress = -1f;
        mTwitchLeftEar = false;
        mTwitchRightEar = false;

        // 表情过渡
        mPrevExpression = RobotState.Expression.IDLE;
        mExpressionBlendProgress = 1.0f;

        // 平滑参数
        mSmoothedLeftEarAngle = EAR_ANGLE_IDLE;
        mSmoothedRightEarAngle = EAR_ANGLE_IDLE;
        mSmoothedPupilDilation = PUPIL_DILATION_IDLE;
        mSmoothedPurring = 0f;

        // SURPRISED 震动
        mShakeTimer = 0f;
        mShaking = false;

        // P5 空闲手臂
        mIdleArmCountdown = randomRange(IDLE_ARM_INTERVAL_MIN, IDLE_ARM_INTERVAL_MAX);
        mIdleArmRevertCountdown = 0f;
        mCurrentIdleArmPose = RobotState.ArmPose.IDLE_SIDE;

        // 嘴巴
        mCurrentMouthShape = RobotState.MouthShape.NEUTRAL;
        mTargetMouthShape = RobotState.MouthShape.NEUTRAL;
        mMouthBlendTimer = -1f;

        // 眉毛
        mCurrentEyebrowState = RobotState.EyebrowState.NEUTRAL;
        mTargetEyebrowState = RobotState.EyebrowState.NEUTRAL;
        mEyebrowBlendTimer = -1f;
        mSmoothedLeftEyebrowAngle = 0f;
        mSmoothedRightEyebrowAngle = 0f;

        // 身体动作
        mActiveBodyAction = RobotState.BodyAction.NONE;
        mBodyActionTimer = 0f;
        mBodyActionDuration = 0f;

        // 思维气泡
        mPrevThoughtBubbleType = RobotState.ThoughtBubbleType.NONE;
        mThoughtFadingIn = false;

        // 指示灯
        mIndicatorFlashTimer = 0f;
        mIndicatorOn = true;

        // 开机序列
        mBootTimer = 0f;
        mBootComplete = false;

        // 全局时间
        mGlobalTime = 0f;
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                    主 更 新 方 法                             ║
    // ╚══════════════════════════════════════════════════════════════╝

    /**
     * 每帧更新所有动画子系统
     *
     * 在 RobotPhysicsEngine.update() 之后调用，读取 state 中由物理引擎/
     * 优先级系统设置的字段，执行动画平滑、空闲行为叠加，并将结果写回 state。
     *
     * @param dt    帧间隔时间（秒），通常为 1/60
     * @param state 当前机器人状态，方法将修改其动画参数字段
     */
    public void update(float dt, RobotState state) {
        // 推进全局时间
        mGlobalTime += dt;

        // 1. 开机序列（前 3 秒覆盖部分状态）
        updateBootSequence(dt, state);

        // 2. 发光呼吸
        updateGlowBreathing(dt, state);

        // 3. 表情过渡检测与 blend
        updateExpressionTransition(dt, state);

        // 4. 眨眼循环（含困倦度影响）
        float eyeOpenness = updateBlinkCycle(dt, state);

        // 5. 耳朵抽动
        updateEarTwitch(dt, state);

        // 6. P5 空闲手臂循环
        updateIdleArmCycle(dt, state);

        // 7. 嘴巴形状动画
        updateMouthAnimation(dt, state);

        // 8. 眉毛角度动画
        updateEyebrowAnimation(dt, state);

        // 9. 身体动作执行
        updateBodyAction(dt, state);

        // 10. 眼睛特效推进
        updateEyeSpecialEffects(dt, state);

        // 11. 思维气泡
        updateThoughtBubble(dt, state);

        // 12. 天线发光相位
        updateAntennaGlow(dt, state);

        // 13. 指示灯动画
        updateIndicator(dt, state);

        // 14. 写入眼睛张开度（综合眨眼 + 困倦度 + 开机序列）
        state.eyeOpenness = clamp(eyeOpenness, 0f, 1.4f);
        state.pupilDilation = clamp(mSmoothedPupilDilation, 0.6f, 1.3f);
        state.purringAmplitude = clamp(mSmoothedPurring, 0f, 1f);

        // 15. SURPRISED 震动叠加
        updateSurprisedShake(dt, state);

        // 16. 累加通用计时器
        state.idleTimer += dt;
        state.blinkTimer += dt;
        state.earTwitchTimer += dt;
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                      子 系 统 方 法                           ║
    // ╚══════════════════════════════════════════════════════════════╝

    // ==================== 1. 发光呼吸 ====================

    /**
     * 更新发光呼吸效果
     *
     * 根据当前表情计算发光强度和身体缩放的基础值与振幅，
     * 通过正弦波产生周期性的"呼吸"感觉。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateGlowBreathing(float dt, RobotState state) {
        state.glowPulsePhase += GLOW_PULSE_SPEED * dt;
        if (state.glowPulsePhase > TWO_PI) {
            state.glowPulsePhase -= TWO_PI;
        }

        float baseGlowIntensity;
        float glowAmplitude;
        float baseBodyScale;
        float scaleAmplitude;

        switch (state.expression) {
            case EXCITED:
                baseGlowIntensity = 0.75f;
                glowAmplitude = 0.25f;
                baseBodyScale = 1.0f;
                scaleAmplitude = 0.02f;
                break;
            case SURPRISED:
                baseGlowIntensity = 0.9f;
                glowAmplitude = 0.1f;
                baseBodyScale = 1.0f;
                scaleAmplitude = 0.01f;
                break;
            default:
                baseGlowIntensity = 0.5f;
                glowAmplitude = 0.3f;
                baseBodyScale = 1.0f;
                scaleAmplitude = 0.015f;
                break;
        }

        float sinPhase = (float) Math.sin(state.glowPulsePhase);
        state.glowIntensity = clamp(baseGlowIntensity + glowAmplitude * sinPhase, 0.2f, 1.0f);
        state.bodyScale = clamp(baseBodyScale + scaleAmplitude * sinPhase, 0.98f, 1.02f);
    }

    // ==================== 2. 表情过渡 ====================

    /**
     * 检测表情变化并更新过渡混合因子
     *
     * 当表情发生切换时重置 blend 进度，并触发 SURPRISED 的震动和粒子。
     * 同时平滑过渡耳朵角度、瞳孔大小、呼噜振幅等表情驱动参数。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateExpressionTransition(float dt, RobotState state) {
        // 检测表情变化
        if (state.expression != mPrevExpression) {
            mExpressionBlendProgress = 0f;

            // SURPRISED 进入效果
            if (state.expression == RobotState.Expression.SURPRISED) {
                mShaking = true;
                mShakeTimer = 0f;
                state.particleBurstRequest = SURPRISED_BURST_COUNT;
            }

            mPrevExpression = state.expression;
        }

        // 推进过渡进度
        if (mExpressionBlendProgress < 1.0f) {
            mExpressionBlendProgress += dt / EXPRESSION_TRANSITION_DURATION;
            if (mExpressionBlendProgress > 1.0f) {
                mExpressionBlendProgress = 1.0f;
            }
        }
        state.expressionBlend = mExpressionBlendProgress;

        // 表情驱动参数目标值
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
            default:
                targetEarAngle = EAR_ANGLE_IDLE;
                targetPupilDilation = PUPIL_DILATION_IDLE;
                targetPurring = 0f;
                break;
        }

        // 使用 expressionBlend 平滑插值
        float blendT = mExpressionBlendProgress;
        mSmoothedLeftEarAngle = lerp(mSmoothedLeftEarAngle, targetEarAngle, blendT);
        mSmoothedRightEarAngle = lerp(mSmoothedRightEarAngle, targetEarAngle, blendT);
        mSmoothedPupilDilation = lerp(mSmoothedPupilDilation, targetPupilDilation, blendT);
        mSmoothedPurring = lerp(mSmoothedPurring, targetPurring, blendT);

        // 写入耳朵基准角度（后续耳朵抽动会叠加偏移）
        state.leftEarAngle = clamp(mSmoothedLeftEarAngle, -30f, 30f);
        state.rightEarAngle = clamp(mSmoothedRightEarAngle, -30f, 30f);
    }

    // ==================== 3. 眨眼循环（含困倦度） ====================

    /**
     * 更新眨眼循环
     *
     * 仅在 IDLE 表情时触发自动眨眼。困倦度（sleepiness > 0.3）会使眨眼更频繁、
     * 睁眼更慢，且最大张开度下降。非 IDLE 表情时根据表情类型直接设置张开度。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     * @return 当前帧的眼睛张开度
     */
    private float updateBlinkCycle(float dt, RobotState state) {
        float eyeOpenness = 1.0f;

        if (state.expression == RobotState.Expression.IDLE) {
            // 根据困倦度调整眨眼参数
            float sleepFactor = 0f;
            if (state.sleepiness > SLEEPINESS_THRESHOLD) {
                // 将 sleepiness 从 [0.3, 1.0] 映射到 [0, 1]
                sleepFactor = (state.sleepiness - SLEEPINESS_THRESHOLD)
                        / (1.0f - SLEEPINESS_THRESHOLD);
                sleepFactor = clamp(sleepFactor, 0f, 1f);
            }

            // 困倦时眨眼间隔缩短
            float blinkMin = lerp(BLINK_INTERVAL_MIN, BLINK_INTERVAL_MIN_DROWSY, sleepFactor);
            float blinkMax = lerp(BLINK_INTERVAL_MAX, BLINK_INTERVAL_MAX_DROWSY, sleepFactor);

            // 困倦时最大张开度下降
            float maxOpenness = lerp(1.0f, EYE_OPENNESS_MIN_DROWSY, sleepFactor);

            // 处理双眨间隔
            if (mPendingSecondBlink) {
                mDoubleBlinkGapTimer -= dt;
                if (mDoubleBlinkGapTimer <= 0f) {
                    mBlinkProgress = 0f;
                    mPendingSecondBlink = false;
                    mIsDoubleBlink = true;
                }
            }

            // 眨眼倒计时
            if (mBlinkProgress < 0f && !mPendingSecondBlink) {
                mNextBlinkCountdown -= dt;
                if (mNextBlinkCountdown <= 0f) {
                    mBlinkProgress = 0f;
                    mIsDoubleBlink = false;
                    mNextBlinkCountdown = randomRange(blinkMin, blinkMax);
                }
            }

            // 执行眨眼动画
            if (mBlinkProgress >= 0f) {
                // 困倦时上升阶段放慢：实际 duration 增大
                float effectiveDuration = BLINK_DURATION;
                if (sleepFactor > 0f) {
                    // 上升阶段放慢最多 2 倍
                    effectiveDuration = BLINK_DURATION + BLINK_DURATION * 0.5f * sleepFactor;
                }

                eyeOpenness = computeBlinkOpenness(mBlinkProgress, effectiveDuration) * maxOpenness;
                mBlinkProgress += dt;

                if (mBlinkProgress >= effectiveDuration) {
                    mBlinkProgress = -1f;

                    if (!mIsDoubleBlink && mRandom.nextFloat() < DOUBLE_BLINK_CHANCE) {
                        mPendingSecondBlink = true;
                        mDoubleBlinkGapTimer = DOUBLE_BLINK_GAP;
                    }
                }
            } else {
                // 非眨眼期间：应用困倦导致的张开度上限
                eyeOpenness = maxOpenness;
            }
        } else {
            // 非 IDLE 表情：根据表情类型设置张开度
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
            // 重置眨眼状态
            mBlinkProgress = -1f;
            mPendingSecondBlink = false;
        }

        return eyeOpenness;
    }

    // ==================== 4. 耳朵抽动 ====================

    /**
     * 更新耳朵抽动
     *
     * 仅在 IDLE 表情时触发。随机间隔后选择单耳或双耳进行快速抽动，
     * 角度偏移叠加在表情驱动的基准角度之上。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateEarTwitch(float dt, RobotState state) {
        if (state.expression == RobotState.Expression.IDLE) {
            // 抽动倒计时
            if (mEarTwitchProgress < 0f) {
                mNextEarTwitchCountdown -= dt;
                if (mNextEarTwitchCountdown <= 0f) {
                    mEarTwitchProgress = 0f;
                    mNextEarTwitchCountdown = randomRange(
                            EAR_TWITCH_INTERVAL_MIN, EAR_TWITCH_INTERVAL_MAX);

                    if (mRandom.nextFloat() < BOTH_EARS_CHANCE) {
                        mTwitchLeftEar = true;
                        mTwitchRightEar = true;
                    } else {
                        mTwitchLeftEar = mRandom.nextBoolean();
                        mTwitchRightEar = !mTwitchLeftEar;
                    }
                }
            }

            // 执行抽动动画
            if (mEarTwitchProgress >= 0f) {
                float twitchOffset = computeEarTwitchOffset(mEarTwitchProgress);
                if (mTwitchLeftEar) {
                    state.leftEarAngle = clamp(state.leftEarAngle + twitchOffset, -30f, 30f);
                }
                if (mTwitchRightEar) {
                    state.rightEarAngle = clamp(state.rightEarAngle + twitchOffset, -30f, 30f);
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
    }

    // ==================== 5. P5 空闲手臂循环 ====================

    /**
     * 更新 P5 空闲手臂姿态循环
     *
     * 当没有更高优先级动作占用手臂（即 state.armPose 为 IDLE_SIDE）时，
     * 按随机间隔（8~15 秒）从安全姿势池中选取一个空闲动作。
     * WAVE 持续 3 秒后自动恢复，THINKING 持续 8 秒后自动恢复。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateIdleArmCycle(float dt, RobotState state) {
        // 仅在手臂无高优先级占用时运作
        if (state.armPose != RobotState.ArmPose.IDLE_SIDE
                && state.armPose != mCurrentIdleArmPose) {
            // 高优先级正在使用手臂，重置空闲系统
            mIdleArmRevertCountdown = 0f;
            mCurrentIdleArmPose = RobotState.ArmPose.IDLE_SIDE;
            mIdleArmCountdown = randomRange(IDLE_ARM_INTERVAL_MIN, IDLE_ARM_INTERVAL_MAX);
            return;
        }

        // 处理自动恢复倒计时
        if (mIdleArmRevertCountdown > 0f) {
            mIdleArmRevertCountdown -= dt;
            if (mIdleArmRevertCountdown <= 0f) {
                // 恢复到 IDLE_SIDE
                mCurrentIdleArmPose = RobotState.ArmPose.IDLE_SIDE;
                state.armPose = RobotState.ArmPose.IDLE_SIDE;
                mIdleArmCountdown = randomRange(IDLE_ARM_INTERVAL_MIN, IDLE_ARM_INTERVAL_MAX);
            }
            return;
        }

        // 切换倒计时
        mIdleArmCountdown -= dt;
        if (mIdleArmCountdown <= 0f) {
            // 仅在 IDLE_SIDE 时才切换（不打断其它空闲动作的恢复）
            if (state.armPose == RobotState.ArmPose.IDLE_SIDE) {
                RobotState.ArmPose picked = IDLE_ARM_POSES[mRandom.nextInt(IDLE_ARM_POSES.length)];
                mCurrentIdleArmPose = picked;
                state.armPose = picked;

                // 设置自动恢复时间
                if (picked == RobotState.ArmPose.WAVE) {
                    mIdleArmRevertCountdown = IDLE_ARM_WAVE_REVERT;
                } else if (picked == RobotState.ArmPose.THINKING) {
                    mIdleArmRevertCountdown = IDLE_ARM_THINKING_REVERT;
                } else {
                    // IDLE_SIDE 被选中：立即重置计时器，等待下次
                    mIdleArmCountdown = randomRange(IDLE_ARM_INTERVAL_MIN, IDLE_ARM_INTERVAL_MAX);
                }
            } else {
                // 当前不在 IDLE_SIDE，延后重试
                mIdleArmCountdown = randomRange(IDLE_ARM_INTERVAL_MIN, IDLE_ARM_INTERVAL_MAX);
            }
        }
    }

    // ==================== 6. 嘴巴形状动画 ====================

    /**
     * 更新嘴巴形状动画
     *
     * 检测 state.mouthShape 变化并启动混合过渡（200ms）。
     * 根据当前形状计算 mouthOpenness，并优先使用 TTS 振幅驱动张开度。
     * 打哈欠时 mouthOpenness 由身体动作系统驱动。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateMouthAnimation(float dt, RobotState state) {
        // 检测形状变化
        if (state.mouthShape != mTargetMouthShape) {
            mTargetMouthShape = state.mouthShape;
            mMouthBlendTimer = 0f;
        }

        // 推进混合过渡
        if (mMouthBlendTimer >= 0f) {
            mMouthBlendTimer += dt;
            float blend = clamp(mMouthBlendTimer / MOUTH_BLEND_DURATION, 0f, 1f);
            state.mouthBlend = blend;

            if (blend >= 1.0f) {
                // 过渡完成
                mCurrentMouthShape = mTargetMouthShape;
                mMouthBlendTimer = -1f;
                state.mouthBlend = 1.0f;
            }
        }

        // 计算嘴巴张开度
        float openness = 0f;

        // TTS 振幅优先级最高
        if (state.ttsAmplitude > 0f) {
            openness = state.ttsAmplitude * 0.6f;
        } else if (mActiveBodyAction == RobotState.BodyAction.YAWN) {
            // 打哈欠时的张开度由 body action 系统驱动，这里不覆盖
            // （在 updateBodyAction 中已设置 mouthOpenness）
            return;
        } else {
            // 根据嘴巴形状计算基础张开度
            switch (mTargetMouthShape) {
                case OPEN_O:
                    // 微微张开并有轻微抖动
                    openness = 0.7f + 0.1f * (float) Math.sin(mGlobalTime * 3.0f);
                    break;
                case OPEN_D:
                    openness = 0.9f;
                    break;
                case WIDE_SMILE:
                    openness = 0.3f;
                    break;
                default:
                    openness = 0f;
                    break;
            }
        }

        state.mouthOpenness = clamp(openness, 0f, 1f);
    }

    // ==================== 7. 眉毛角度动画 ====================

    /**
     * 更新眉毛角度动画
     *
     * 检测 state.eyebrowState 变化并启动过渡（200ms）。
     * 根据目标状态计算左右眉毛的目标角度，通过线性插值平滑过渡。
     *
     * 各状态目标角度（正值 = 下压/内收，负值 = 上扬）：
     *   NEUTRAL:  左=0°, 右=0°
     *   RAISED:   左=-12°, 右=-12°
     *   FURROWED: 左=10°, 右=10°
     *   ONE_UP:   左=-12°, 右=3°
     *   SAD:      左=8°, 右=8°
     *   ANGRY:    左=15°, 右=15°
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateEyebrowAnimation(float dt, RobotState state) {
        // 检测状态变化
        if (state.eyebrowState != mTargetEyebrowState) {
            mTargetEyebrowState = state.eyebrowState;
            mEyebrowBlendTimer = 0f;
        }

        // 查表获取目标角度
        float targetLeft;
        float targetRight;
        switch (mTargetEyebrowState) {
            case RAISED:
                targetLeft = -12f;
                targetRight = -12f;
                break;
            case FURROWED:
                targetLeft = 10f;
                targetRight = 10f;
                break;
            case ONE_UP:
                targetLeft = -12f;
                targetRight = 3f;
                break;
            case SAD:
                targetLeft = 8f;
                targetRight = 8f;
                break;
            case ANGRY:
                targetLeft = 15f;
                targetRight = 15f;
                break;
            default: // NEUTRAL
                targetLeft = 0f;
                targetRight = 0f;
                break;
        }

        // 推进混合过渡
        float blendFactor = 1.0f;
        if (mEyebrowBlendTimer >= 0f) {
            mEyebrowBlendTimer += dt;
            blendFactor = clamp(mEyebrowBlendTimer / EYEBROW_BLEND_DURATION, 0f, 1f);

            if (blendFactor >= 1.0f) {
                mCurrentEyebrowState = mTargetEyebrowState;
                mEyebrowBlendTimer = -1f;
            }
        }

        // 平滑插值
        mSmoothedLeftEyebrowAngle = lerp(mSmoothedLeftEyebrowAngle, targetLeft, blendFactor);
        mSmoothedRightEyebrowAngle = lerp(mSmoothedRightEyebrowAngle, targetRight, blendFactor);

        // 写入 state
        state.leftEyebrowAngle = mSmoothedLeftEyebrowAngle;
        state.rightEyebrowAngle = mSmoothedRightEyebrowAngle;
        state.eyebrowBlend = blendFactor;
    }

    // ==================== 8. 身体动作执行 ====================

    /**
     * 更新身体动作执行
     *
     * 检测 state.bodyAction 变化来启动新动作。每种动作有固定时长和运动曲线，
     * 通过 progress [0,1] 映射到位移/旋转/缩放偏移。TILT 是持续型动作，
     * 无固定时长。动作完成后自动清除 state.bodyAction。
     *
     * 运动曲线输出叠加到 bodyX/bodyY/rotation 上（增量式）。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateBodyAction(float dt, RobotState state) {
        // 检测新动作
        if (state.bodyAction != mActiveBodyAction) {
            mActiveBodyAction = state.bodyAction;
            mBodyActionTimer = 0f;

            // 设置动作时长
            switch (mActiveBodyAction) {
                case NOD:
                    mBodyActionDuration = DURATION_NOD;
                    break;
                case SHAKE_HEAD:
                    mBodyActionDuration = DURATION_SHAKE_HEAD;
                    break;
                case BOUNCE:
                    mBodyActionDuration = DURATION_BOUNCE;
                    break;
                case SHIVER:
                    mBodyActionDuration = DURATION_SHIVER;
                    break;
                case YAWN:
                    mBodyActionDuration = DURATION_YAWN;
                    break;
                case STRETCH:
                    mBodyActionDuration = DURATION_STRETCH;
                    break;
                case TILT:
                    mBodyActionDuration = -1f; // 持续型
                    break;
                default:
                    mBodyActionDuration = 0f;
                    break;
            }
        }

        // 无动作时清除进度
        if (mActiveBodyAction == RobotState.BodyAction.NONE) {
            state.bodyActionProgress = 0f;
            state.bodyActionIntensity = 0f;
            return;
        }

        // 推进计时器
        mBodyActionTimer += dt;

        // 计算 progress
        float progress;
        if (mBodyActionDuration > 0f) {
            progress = clamp(mBodyActionTimer / mBodyActionDuration, 0f, 1f);
        } else {
            // 持续型动作（TILT），progress 无意义，使用 globalTime
            progress = 0f;
        }

        // 执行运动曲线
        float offsetX = 0f;
        float offsetY = 0f;
        float rotOffset = 0f;
        float scaleBoost = 0f;
        float intensity = 1.0f;

        switch (mActiveBodyAction) {
            case NOD:
                rotOffset = computeNod(progress);
                break;
            case SHAKE_HEAD:
                rotOffset = computeShakeHead(progress);
                break;
            case BOUNCE:
                offsetY = computeBounceY(progress);
                scaleBoost = 0.03f * (float) Math.sin(progress * (float) Math.PI);
                break;
            case SHIVER:
                offsetX = computeShiver(progress);
                break;
            case TILT:
                rotOffset = 8.0f * (float) Math.sin(mGlobalTime * 0.5f);
                intensity = 1.0f;
                break;
            case YAWN:
                computeYawn(progress, state);
                break;
            case STRETCH:
                computeStretch(progress, state);
                break;
            default:
                break;
        }

        // 叠加偏移到 state（增量式，不覆盖物理引擎设置的基础值）
        state.bodyX += offsetX * state.bodyActionIntensity;
        state.bodyY += offsetY * state.bodyActionIntensity;
        state.rotation += rotOffset * state.bodyActionIntensity;
        state.bodyScale += scaleBoost;

        // 写入进度
        state.bodyActionProgress = progress;
        if (mActiveBodyAction != RobotState.BodyAction.TILT) {
            state.bodyActionIntensity = intensity;
        }

        // 有限时长动作完成后清除
        if (mBodyActionDuration > 0f && mBodyActionTimer >= mBodyActionDuration) {
            mActiveBodyAction = RobotState.BodyAction.NONE;
            state.bodyAction = RobotState.BodyAction.NONE;
            state.bodyActionProgress = 0f;
            state.bodyActionIntensity = 0f;
        }
    }

    /**
     * 点头运动曲线
     *
     * 0~30%: 头向下倾 8°（easeIn）
     * 30~50%: 保持
     * 50~100%: 回正（easeOut）
     *
     * @param progress 动作进度 [0,1]
     * @return 旋转偏移（度）
     */
    private float computeNod(float progress) {
        if (progress < 0.3f) {
            float t = progress / 0.3f;
            return 8.0f * easeInQuad(t);
        } else if (progress < 0.5f) {
            return 8.0f;
        } else {
            float t = (progress - 0.5f) / 0.5f;
            return 8.0f * (1.0f - easeOutQuad(t));
        }
    }

    /**
     * 摇头运动曲线
     *
     * 阻尼正弦振荡：rotation = 6° × sin(progress × 3π) × (1 - progress)
     *
     * @param progress 动作进度 [0,1]
     * @return 旋转偏移（度）
     */
    private float computeShakeHead(float progress) {
        return 6.0f * (float) Math.sin(progress * 3.0f * Math.PI) * (1.0f - progress);
    }

    /**
     * 弹跳 Y 偏移曲线
     *
     * 正弦半波：bodyYOffset = -30 × sin(progress × π)
     * 负值表示向上移动（无单位，渲染器转换为 dp）
     *
     * @param progress 动作进度 [0,1]
     * @return Y 偏移量
     */
    private float computeBounceY(float progress) {
        return -30.0f * (float) Math.sin(progress * (float) Math.PI);
    }

    /**
     * 颤抖 X 偏移曲线
     *
     * 高频衰减震动：bodyXOffset = 2 × sin(progress × 40π) × (1 - progress)
     *
     * @param progress 动作进度 [0,1]
     * @return X 偏移量
     */
    private float computeShiver(float progress) {
        return 2.0f * (float) Math.sin(progress * 40.0f * (float) Math.PI) * (1.0f - progress);
    }

    /**
     * 打哈欠运动曲线
     *
     * 分三阶段：
     *   0~30%: 嘴巴张开（OPEN_O），眼睛眯起
     *   30~60%: 保持张开，身体微微后仰
     *   60~100%: 嘴巴闭合，眼睛恢复，轻微弹跳
     *
     * 直接修改 state 中的嘴巴和眼睛参数。
     *
     * @param progress 动作进度 [0,1]
     * @param state    机器人状态
     */
    private void computeYawn(float progress, RobotState state) {
        if (progress < 0.3f) {
            // 嘴巴逐渐张开，眼睛眯起
            float t = progress / 0.3f;
            state.mouthOpenness = t;
            // 眼睛从当前张开度过渡到 0.3（眯眼）
            state.eyeOpenness = lerp(state.eyeOpenness, 0.3f, t);
        } else if (progress < 0.6f) {
            // 保持张开
            state.mouthOpenness = 1.0f;
            state.eyeOpenness = 0.3f;
            // 身体微微后仰
            float leanProgress = (progress - 0.3f) / 0.3f;
            state.rotation += -3.0f * (float) Math.sin(leanProgress * (float) Math.PI);
        } else {
            // 嘴巴闭合，眼睛恢复
            float t = (progress - 0.6f) / 0.4f;
            state.mouthOpenness = 1.0f - t;
            state.eyeOpenness = lerp(0.3f, 1.0f, easeOutQuad(t));
            // 轻微弹跳
            state.bodyY += -5.0f * (float) Math.sin(t * (float) Math.PI);
        }
    }

    /**
     * 伸展运动曲线
     *
     * 分三阶段：
     *   0~40%: 手臂上举（BOTH_UP），身体向上延伸
     *   40~70%: 保持，轻微摇晃
     *   70~100%: 手臂放下，身体回落
     *
     * @param progress 动作进度 [0,1]
     * @param state    机器人状态
     */
    private void computeStretch(float progress, RobotState state) {
        if (progress < 0.4f) {
            float t = progress / 0.4f;
            state.armPose = RobotState.ArmPose.BOTH_UP;
            // 身体向上延伸
            state.bodyY += -15.0f * easeOutQuad(t);
            state.bodyScale += 0.02f * t;
        } else if (progress < 0.7f) {
            state.armPose = RobotState.ArmPose.BOTH_UP;
            float wobbleT = (progress - 0.4f) / 0.3f;
            // 轻微摇晃
            state.bodyX += 2.0f * (float) Math.sin(wobbleT * TWO_PI);
            state.bodyY += -15.0f;
            state.bodyScale += 0.02f;
        } else {
            float t = (progress - 0.7f) / 0.3f;
            state.armPose = RobotState.ArmPose.IDLE_SIDE;
            // 身体回落
            state.bodyY += -15.0f * (1.0f - easeInQuad(t));
            state.bodyScale += 0.02f * (1.0f - t);
        }
    }

    // ==================== 9. 眼睛特效 ====================

    /**
     * 更新眼睛特效强度
     *
     * 根据特效类型驱动 eyeSpecialIntensity：
     *   SPARKLE: 0.5~1.0 正弦脉冲
     *   DIZZY: 旋转角度，每秒 360°
     *   HEART: 静态 1.0
     *   SLEEPY: 等于 sleepiness
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateEyeSpecialEffects(float dt, RobotState state) {
        switch (state.eyeSpecial) {
            case SPARKLE:
                // 闪烁脉冲：0.5 ~ 1.0
                state.eyeSpecialIntensity = 0.75f + 0.25f
                        * (float) Math.sin(mGlobalTime * 6.0f);
                break;
            case DIZZY:
                // 旋转角度：每秒 360°（渲染器用这个值做螺旋旋转）
                state.eyeSpecialIntensity = (mGlobalTime * 360.0f) % 360.0f;
                break;
            case HEART:
                state.eyeSpecialIntensity = 1.0f;
                break;
            case SLEEPY:
                state.eyeSpecialIntensity = state.sleepiness;
                break;
            default:
                state.eyeSpecialIntensity = 0f;
                break;
        }
    }

    // ==================== 10. 思维气泡 ====================

    /**
     * 更新思维气泡动画
     *
     * 当 thoughtBubbleType 从 NONE 变为其它类型时，执行 300ms 淡入；
     * 当变为 NONE 时，执行 200ms 淡出。存在期间持续推进 progress。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateThoughtBubble(float dt, RobotState state) {
        // 检测类型变化
        if (state.thoughtBubbleType != mPrevThoughtBubbleType) {
            if (state.thoughtBubbleType != RobotState.ThoughtBubbleType.NONE) {
                // 开始淡入
                mThoughtFadingIn = true;
            } else {
                // 开始淡出
                mThoughtFadingIn = false;
            }
            mPrevThoughtBubbleType = state.thoughtBubbleType;
        }

        // 淡入/淡出
        if (mThoughtFadingIn) {
            if (state.thoughtBubbleAlpha < 1.0f) {
                state.thoughtBubbleAlpha += dt / THOUGHT_FADE_IN;
                if (state.thoughtBubbleAlpha > 1.0f) {
                    state.thoughtBubbleAlpha = 1.0f;
                }
            }
            // 持续推进 progress（渲染器用于气泡内容动画）
            state.thoughtBubbleProgress += dt;
            // 保持在 [0, 循环周期) 内，用 1 秒循环
            if (state.thoughtBubbleProgress > 1.0f) {
                state.thoughtBubbleProgress -= 1.0f;
            }
        } else {
            if (state.thoughtBubbleAlpha > 0f) {
                state.thoughtBubbleAlpha -= dt / THOUGHT_FADE_OUT;
                if (state.thoughtBubbleAlpha <= 0f) {
                    state.thoughtBubbleAlpha = 0f;
                    state.thoughtBubbleProgress = 0f;
                }
            }
        }
    }

    // ==================== 11. 天线发光 ====================

    /**
     * 更新天线发光相位
     *
     * 连续推进 antennaGlowPhase，保持在 [0, 2π) 范围内。
     * 渲染器根据此相位和 AI 连接状态来绘制天线光球效果。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateAntennaGlow(float dt, RobotState state) {
        state.antennaGlowPhase += ANTENNA_GLOW_SPEED * dt;
        if (state.antennaGlowPhase > TWO_PI) {
            state.antennaGlowPhase -= TWO_PI;
        }
    }

    // ==================== 12. 指示灯动画 ====================

    /**
     * 更新指示灯闪烁计时
     *
     * NORMAL/BREATHING 由发光呼吸系统驱动，此处不额外处理。
     * WARNING: 每 0.3 秒翻转一次亮灭。
     * ERROR: 每 0.15 秒翻转一次（更快闪烁）。
     * AI_ACTIVE: 1Hz 正弦平滑脉冲。
     *
     * 结果写入 antennaFlashing 字段供渲染器判断当前亮灭状态。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateIndicator(float dt, RobotState state) {
        switch (state.indicatorState) {
            case WARNING:
                mIndicatorFlashTimer += dt;
                if (mIndicatorFlashTimer >= INDICATOR_WARNING_PERIOD) {
                    mIndicatorFlashTimer -= INDICATOR_WARNING_PERIOD;
                    mIndicatorOn = !mIndicatorOn;
                }
                state.antennaFlashing = mIndicatorOn;
                break;
            case ERROR:
                mIndicatorFlashTimer += dt;
                if (mIndicatorFlashTimer >= INDICATOR_ERROR_PERIOD) {
                    mIndicatorFlashTimer -= INDICATOR_ERROR_PERIOD;
                    mIndicatorOn = !mIndicatorOn;
                }
                state.antennaFlashing = mIndicatorOn;
                break;
            case AI_ACTIVE:
                // 平滑正弦脉冲，渲染器可用 antennaFlashing 做亮度调制
                // 频率 1Hz：sin(time * 2π) > 0 时亮
                state.antennaFlashing =
                        (float) Math.sin(mGlobalTime * TWO_PI) > 0f;
                break;
            default:
                // NORMAL / BREATHING：重置闪烁状态
                mIndicatorFlashTimer = 0f;
                mIndicatorOn = true;
                state.antennaFlashing = false;
                break;
        }
    }

    // ==================== 13. SURPRISED 震动 ====================

    /**
     * 更新 SURPRISED 震动效果
     *
     * 高频正弦乘以线性衰减包络，产生短暂的身体水平抖动。
     * 震动偏移叠加到 bodyX（物理引擎先设置基础值，这里做增量叠加）。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateSurprisedShake(float dt, RobotState state) {
        if (!mShaking) return;

        mShakeTimer += dt;
        if (mShakeTimer < SURPRISED_SHAKE_DURATION) {
            float decay = 1.0f - (mShakeTimer / SURPRISED_SHAKE_DURATION);
            float freq = 40f;
            float shakeOffsetX = SURPRISED_SHAKE_AMPLITUDE * decay
                    * (float) Math.sin(mShakeTimer * freq * TWO_PI);
            state.bodyX += shakeOffsetX;
        } else {
            mShaking = false;
        }
    }

    // ==================== 14. 开机/唤醒序列 ====================

    /**
     * 更新开机/唤醒序列
     *
     * 前 3 秒内按时间段覆盖部分状态：
     *   0~0.5s: 眼睛闭合，身体静止（唤醒中）
     *   0.5~1.5s: 眼睛缓慢睁开，触发 STRETCH 动作
     *   1.5~2.5s: WAVE 手臂姿态（打招呼）
     *   2.5~3.0s: 过渡回正常 IDLE
     *
     * 3 秒后 mBootComplete=true，不再执行开机逻辑。
     *
     * @param dt    帧间隔（秒）
     * @param state 机器人状态
     */
    private void updateBootSequence(float dt, RobotState state) {
        if (mBootComplete) return;

        mBootTimer += dt;

        if (mBootTimer < 0.5f) {
            // 阶段 1: 眼睛闭合，身体静止
            state.eyeOpenness = 0f;
            state.bodyAction = RobotState.BodyAction.NONE;
            state.armPose = RobotState.ArmPose.IDLE_SIDE;
        } else if (mBootTimer < 1.5f) {
            // 阶段 2: 眼睛逐渐睁开 + STRETCH
            float t = (mBootTimer - 0.5f) / 1.0f;
            state.eyeOpenness = easeOutQuad(t);
            if (state.bodyAction != RobotState.BodyAction.STRETCH) {
                state.bodyAction = RobotState.BodyAction.STRETCH;
            }
        } else if (mBootTimer < 2.5f) {
            // 阶段 3: WAVE 打招呼
            state.eyeOpenness = 1.0f;
            state.armPose = RobotState.ArmPose.WAVE;
        } else if (mBootTimer < BOOT_TOTAL_DURATION) {
            // 阶段 4: 过渡回 IDLE
            float t = (mBootTimer - 2.5f) / 0.5f;
            state.eyeOpenness = 1.0f;
            // 平滑切回 IDLE_SIDE（通过混合因子）
            if (t > 0.5f) {
                state.armPose = RobotState.ArmPose.IDLE_SIDE;
            }
        } else {
            // 开机完成
            mBootComplete = true;
        }
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                      工 具 方 法                              ║
    // ╚══════════════════════════════════════════════════════════════╝

    /**
     * 计算眨眼过程中的眼睛张开度
     *
     * 三阶段动画：
     *   0~60ms: 下降（easeInQuad 1.0→0.0）
     *   60~80ms: 保持闭合（0.0）
     *   80~duration: 上升（easeOutQuad 0.0→1.0）
     *
     * @param progress 当前眨眼进度（秒）
     * @param duration 完整眨眼持续时间（秒），困倦时可能延长
     * @return 眼睛张开度（0.0~1.0）
     */
    private float computeBlinkOpenness(float progress, float duration) {
        if (progress < BLINK_DOWN_END) {
            float t = progress / BLINK_DOWN_END;
            return 1.0f - easeInQuad(t);
        } else if (progress < BLINK_HOLD_END) {
            return 0f;
        } else if (progress < duration) {
            float t = (progress - BLINK_HOLD_END) / (duration - BLINK_HOLD_END);
            return easeOutQuad(t);
        }
        return 1.0f;
    }

    /**
     * 计算耳朵抽动的角度偏移
     *
     * 关键帧插值：
     *   0ms → 0°
     *   80ms → -12°（向前快速抖）
     *   180ms → +5°（回弹超调）
     *   300ms → 0°（恢复）
     *
     * @param progress 当前抽动进度（秒）
     * @return 角度偏移（度）
     */
    private float computeEarTwitchOffset(float progress) {
        final float t1 = 0.080f;
        final float t2 = 0.180f;
        final float t3 = 0.300f;

        final float v0 = 0f;
        final float v1 = -12f;
        final float v2 = 5f;
        final float v3 = 0f;

        if (progress < t1) {
            return lerp(v0, v1, progress / t1);
        } else if (progress < t2) {
            return lerp(v1, v2, (progress - t1) / (t2 - t1));
        } else if (progress < t3) {
            return lerp(v2, v3, (progress - t2) / (t3 - t2));
        }
        return 0f;
    }

    /**
     * 线性插值
     *
     * @param a 起始值
     * @param b 目标值
     * @param t 插值参数 [0,1]
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
     * easeInQuad 缓动函数
     *
     * @param t 归一化时间 [0,1]
     * @return 缓动后的值
     */
    private static float easeInQuad(float t) {
        return t * t;
    }

    /**
     * easeOutQuad 缓动函数
     *
     * @param t 归一化时间 [0,1]
     * @return 缓动后的值
     */
    private static float easeOutQuad(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t);
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

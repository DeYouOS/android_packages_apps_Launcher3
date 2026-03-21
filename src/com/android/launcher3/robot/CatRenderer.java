package com.android.launcher3.robot;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * 赛博朋克霓虹猫 Canvas 2D 渲染器
 *
 * 使用纯 Canvas Path/drawArc/drawCircle/drawLine 绘制赛博朋克风格的猫咪形象。
 * 所有形状采用「双层霓虹发光」技术：
 * 1. 底层发光（Glow）：加粗描边 + setShadowLayer 大半径 + 主色半透明
 * 2. 顶层核心（Core）：正常描边 + setShadowLayer 小半径 + 白色实线
 *
 * 视觉风格：
 * - 深空星云渐变背景（深紫 #0A0618 → 深蓝 #0C1428）
 * - 主色青 #00D4FF + 强调品红 #FF006B + 核心白 #FFFFFF
 * - 60~80 颗星星粒子 + 流星效果
 * - 表情系统：IDLE（竖瞳眨眼）、EXCITED（绿圆瞳）、SURPRISED（黄大圆瞳）
 *
 * 所有尺寸使用 dp 定义（已按 1.45x 缩放），运行时根据 Canvas 密度转 px。
 * 绘制坐标以猫咪 bodyX/bodyY 为原点，各部件相对于原点定位。
 *
 * 绘制顺序（后→前）：
 * 背景星空 → 尾巴 → 后腿 → 身体 → 前腿 → 头部 → 耳朵 → 眼睛 →
 * 鼻嘴 → 胡须 → 爪尖 → 前景粒子
 */
public class CatRenderer {

    // ==================== 颜色常量 ====================

    /** 主色调：赛博青（身体轮廓、头部、腿、胡须发光层） */
    private static final int COLOR_PRIMARY = 0xFF00D4FF;

    /** 强调色：品红（耳朵内侧、瞳孔、鼻子、爪尖、兴奋粒子） */
    private static final int COLOR_ACCENT = 0xFFFF006B;

    /** 核心白：所有线条的核心层颜色 */
    private static final int COLOR_CORE = 0xFFFFFFFF;

    /** 眼睛绿：EXCITED 状态的瞳孔发光色 */
    private static final int COLOR_EYE_GREEN = 0xFF00FF88;

    /** 惊讶黄：SURPRISED 状态的强调色 */
    private static final int COLOR_SURPRISED_YELLOW = 0xFFFFE814;

    /** 深空背景左下角色：深紫 */
    private static final int COLOR_BG_DEEP_PURPLE = 0xFF0A0618;

    /** 深空背景右上角色：深蓝 */
    private static final int COLOR_BG_DEEP_BLUE = 0xFF0C1428;

    // ==================== 尺寸常量（dp，已含 1.45x 缩放） ====================

    // ---- 头部 ----
    /** 头部宽度 */
    private static final float HEAD_W = 93f;
    /** 头部高度 */
    private static final float HEAD_H = 81f;
    /** 头部圆角 */
    private static final float HEAD_R = 29f;
    /** 头部中心 Y 偏移（相对于猫身体原点） */
    private static final float HEAD_CY = -75f;

    // ---- 耳朵 ----
    /** 左耳顶点 A（三角形尖端） */
    private static final float EAR_L_AX = -41f, EAR_L_AY = -145f;
    /** 左耳顶点 B（底边左） */
    private static final float EAR_L_BX = -46f, EAR_L_BY = -87f;
    /** 左耳顶点 C（底边右） */
    private static final float EAR_L_CX = -17f, EAR_L_CY = -93f;
    /** 耳朵描边宽度 */
    private static final float EAR_STROKE = 3f;
    /** 耳朵内侧三角缩放比例（相对于质心缩小） */
    private static final float EAR_INNER_SCALE = 0.7f;

    // ---- 眼眶 ----
    /** 左眼眶中心 X */
    private static final float EYE_L_CX = -20f;
    /** 右眼眶中心 X */
    private static final float EYE_R_CX = 20f;
    /** 眼眶中心 Y */
    private static final float EYE_CY = -81f;
    /** 眼眶水平半径 */
    private static final float EYE_RX = 15f;
    /** 眼眶垂直半径 */
    private static final float EYE_RY = 17f;

    // ---- 瞳孔 ----
    /** 瞳孔水平半径（正常态） */
    private static final float PUPIL_RX = 6f;
    /** 瞳孔垂直半径（正常态，竖瞳比横向大） */
    private static final float PUPIL_RY = 10f;

    // ---- 鼻子 ----
    /** 鼻子中心 Y */
    private static final float NOSE_CY = -64f;
    /** 鼻子宽度的一半 */
    private static final float NOSE_HW = 4.5f;
    /** 鼻子高度 */
    private static final float NOSE_H = 6f;

    // ---- 嘴巴 ----
    /** 嘴巴贝塞尔弧线每侧宽度 */
    private static final float MOUTH_SIDE_W = 12f;

    // ---- 胡须 ----
    /** 左侧胡须起点 X */
    private static final float WHISKER_L_X = -26f;
    /** 右侧胡须起点 X */
    private static final float WHISKER_R_X = 26f;
    /** 胡须起点 Y */
    private static final float WHISKER_Y = -67f;
    /** 胡须长度 */
    private static final float WHISKER_LEN = 35f;
    /** 胡须描边宽度 */
    private static final float WHISKER_STROKE = 2f;

    // ---- 身体（梯形） ----
    /** 身体顶部宽度 */
    private static final float BODY_TOP_W = 70f;
    /** 身体底部宽度 */
    private static final float BODY_BOT_W = 81f;
    /** 身体顶部 Y */
    private static final float BODY_TOP_Y = -35f;
    /** 身体底部 Y */
    private static final float BODY_BOT_Y = 58f;
    /** 身体圆角半径 */
    private static final float BODY_CORNER_R = 17f;

    // ---- 胸部高光 ----
    /** 胸部高光中心 Y */
    private static final float CHEST_CY = -14f;
    /** 胸部高光水平半径 */
    private static final float CHEST_RX = 23f;
    /** 胸部高光垂直半径 */
    private static final float CHEST_RY = 29f;
    /** 胸部高光扫过角度 */
    private static final float CHEST_SWEEP = 160f;

    // ---- 前腿 ----
    /** 前腿枢轴 Y */
    private static final float FLEG_PIVOT_Y = 44f;
    /** 前腿枢轴 X（左/右对称） */
    private static final float FLEG_PIVOT_X = 26f;
    /** 前腿上部宽度 */
    private static final float FLEG_UPPER_W = 15f;
    /** 前腿下部宽度 */
    private static final float FLEG_LOWER_W = 10f;
    /** 前腿长度 */
    private static final float FLEG_LEN = 52f;

    // ---- 后腿 ----
    /** 后腿枢轴 Y */
    private static final float BLEG_PIVOT_Y = 55f;
    /** 后腿枢轴 X（左/右对称） */
    private static final float BLEG_PIVOT_X = 32f;
    /** 后腿上部宽度 */
    private static final float BLEG_UPPER_W = 17f;
    /** 后腿下部宽度 */
    private static final float BLEG_LOWER_W = 12f;
    /** 后腿长度 */
    private static final float BLEG_LEN = 46f;
    /** 后腿膝盖弯曲角度 */
    private static final float BLEG_KNEE_BEND = 15f;

    // ---- 脚掌 ----
    /** 脚掌水平半径 */
    private static final float PAW_RX = 9f;
    /** 脚掌垂直半径 */
    private static final float PAW_RY = 6f;

    // ---- 爪尖 ----
    /** 爪尖弧线长度 */
    private static final float CLAW_LEN = 4f;

    // ---- 尾巴连接点 ----
    /** 尾巴连接 X（相对身体中心） */
    private static final float TAIL_CONN_X = 6f;
    /** 尾巴连接 Y（相对身体中心） */
    private static final float TAIL_CONN_Y = 61f;

    // ---- 尾巴段长度（根→尖） ----
    private static final float[] TAIL_LENGTHS = {20f, 19f, 17f, 15f, 12f, 9f};
    /** 尾巴段描边宽度（根→尖递减） */
    private static final float[] TAIL_WIDTHS = {7f, 6.5f, 5.8f, 4.4f, 3.6f, 2.2f};

    // ---- 发光半径（dp，乘以 glowIntensity） ----
    private static final float GLOW_HEAD = 17f, CORE_HEAD = 6f;
    private static final float GLOW_EAR = 15f, CORE_EAR = 4f;
    private static final float GLOW_BODY = 20f, CORE_BODY = 7f;
    private static final float GLOW_LEG = 15f, CORE_LEG = 4f;
    private static final float GLOW_TAIL = 17f, CORE_TAIL = 6f;
    private static final float GLOW_WHISKER = 12f, CORE_WHISKER = 3f;
    private static final float GLOW_EYE = 15f, CORE_EYE = 6f;

    /** 霓虹发光额外描边宽度增量 */
    private static final float GLOW_EXTRA_STROKE = 6f;

    // ==================== 星空背景参数 ====================

    /** 星星数量 */
    private static final int STAR_COUNT = 70;

    /** 流星最大数量 */
    private static final int SHOOTING_STAR_MAX = 2;

    // ==================== 复用绘制对象 ====================

    /** 主色发光层画笔（青色大 shadowLayer，加粗描边） */
    private final Paint mPrimaryGlow;

    /** 主色核心层画笔（白色线条，小 shadowLayer） */
    private final Paint mPrimaryCore;

    /** 强调色发光层画笔（品红大 shadowLayer） */
    private final Paint mAccentGlow;

    /** 强调色核心层画笔（白色线条，品红小 shadowLayer） */
    private final Paint mAccentCore;

    /** 眼睛专用画笔（颜色随表情变化） */
    private final Paint mEyePaint;

    /** 粒子/星空画笔 */
    private final Paint mParticlePaint;

    /** 复用 Path 对象：身体梯形 */
    private final Path mBodyPath;
    /** 复用 Path 对象：头部 */
    private final Path mHeadPath;
    /** 复用 Path 对象：耳朵 */
    private final Path mEarPath;
    /** 复用 Path 对象：耳朵内侧 */
    private final Path mEarInnerPath;
    /** 复用 Path 对象：腿部 */
    private final Path mLegPath;
    /** 复用 Path 对象：嘴巴 */
    private final Path mMouthPath;
    /** 复用 Path 对象：通用临时 */
    private final Path mTempPath;

    /** 复用矩形对象 */
    private final RectF mTempRect;

    /** 屏幕密度（dp→px 转换因子） */
    private float mDensity = 2.75f;

    /** 星空背景渐变 Shader，首次绘制时根据屏幕尺寸创建 */
    private LinearGradient mBgGradient;
    /** 上次创建渐变时的屏幕宽度，用于检测尺寸变化 */
    private float mLastGradientW;
    /** 上次创建渐变时的屏幕高度 */
    private float mLastGradientH;

    // ---- 星空粒子状态 ----
    /** 星星 X 坐标数组（px） */
    private final float[] mStarX = new float[STAR_COUNT];
    /** 星星 Y 坐标数组（px） */
    private final float[] mStarY = new float[STAR_COUNT];
    /** 星星半径（dp） */
    private final float[] mStarRadius = new float[STAR_COUNT];
    /** 星星基础透明度 [0,1] */
    private final float[] mStarAlpha = new float[STAR_COUNT];
    /** 星星脉冲相位（弧度） */
    private final float[] mStarPhase = new float[STAR_COUNT];
    /** 星星漂移速度 X（px/s） */
    private final float[] mStarDriftX = new float[STAR_COUNT];
    /** 星星漂移速度 Y（px/s） */
    private final float[] mStarDriftY = new float[STAR_COUNT];
    /** 星星是否已初始化 */
    private boolean mStarsInitialized = false;

    // ---- 流星状态 ----
    /** 流星 X 坐标 */
    private final float[] mShootX = new float[SHOOTING_STAR_MAX];
    /** 流星 Y 坐标 */
    private final float[] mShootY = new float[SHOOTING_STAR_MAX];
    /** 流星速度 X */
    private final float[] mShootVX = new float[SHOOTING_STAR_MAX];
    /** 流星速度 Y */
    private final float[] mShootVY = new float[SHOOTING_STAR_MAX];
    /** 流星剩余生命（秒） */
    private final float[] mShootLife = new float[SHOOTING_STAR_MAX];
    /** 流星最大生命（秒） */
    private final float[] mShootMaxLife = new float[SHOOTING_STAR_MAX];
    /** 流星是否存活 */
    private final boolean[] mShootAlive = new boolean[SHOOTING_STAR_MAX];

    /** 随机数生成器 */
    private final Random mRandom = new Random();

    /** 上一帧时间，用于计算星空动画 dt */
    private float mLastIdleTimer = 0f;

    /**
     * 构造霓虹猫渲染器
     *
     * 预创建 6 个 Paint 对象和 7 个 Path 对象，避免每帧 new。
     * Paint 参数在每帧 draw() 中按需设置。
     */
    public CatRenderer() {
        mPrimaryGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPrimaryCore = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAccentGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAccentCore = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEyePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mParticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mBodyPath = new Path();
        mHeadPath = new Path();
        mEarPath = new Path();
        mEarInnerPath = new Path();
        mLegPath = new Path();
        mMouthPath = new Path();
        mTempPath = new Path();
        mTempRect = new RectF();
    }

    /**
     * dp 转 px
     *
     * @param dpVal 密度无关像素值
     * @return 对应的物理像素值
     */
    private float dp(float dpVal) {
        return dpVal * mDensity;
    }

    /**
     * 主绘制入口
     *
     * 按照从后到前的层次绘制所有部件，实现完整的霓虹猫视觉效果。
     * 每帧由 RobotRenderThread 调用。
     *
     * @param canvas 绘制目标画布（来自 TextureView.lockCanvas，软件渲染模式）
     * @param state  当前猫咪动画状态（位置、表情、耳朵、尾巴等参数）
     */
    public void draw(Canvas canvas, RobotState state) {
        // 从 Canvas 获取密度（TextureView lockCanvas 可能返回特殊值）
        int canvasDensity = canvas.getDensity();
        if (canvasDensity > 0 && canvasDensity != 0xFFFF) {
            mDensity = canvasDensity / 160f;
        }

        float sw = state.screenWidth > 0 ? state.screenWidth : canvas.getWidth();
        float sh = state.screenHeight > 0 ? state.screenHeight : canvas.getHeight();

        // 绘制深空星云渐变背景（替代透明清屏，让背景有氛围感）
        drawBackground(canvas, sw, sh, state);

        canvas.save();

        // 平移到猫咪身体中心
        canvas.translate(state.bodyX, state.bodyY);

        // 呼吸浮动 + 旋转
        float bobOffset = (float) Math.sin(state.idleTimer * 1.8) * dp(4f);
        canvas.translate(0, bobOffset);
        canvas.rotate(state.rotation);

        // 呼吸缩放
        float scale = state.bodyScale;
        if (scale > 0.01f) {
            canvas.scale(scale, scale);
        }

        float glow = state.glowIntensity;

        // ---- 绘制顺序：后→前 ----
        drawTail(canvas, state, glow, true);   // 尾巴发光层
        drawTail(canvas, state, glow, false);  // 尾巴核心层
        drawBackLegs(canvas, state, glow);
        drawBody(canvas, state, glow);
        drawFrontLegs(canvas, state, glow);
        drawHead(canvas, state, glow);
        drawEars(canvas, state, glow);
        drawEyes(canvas, state, glow);
        drawNoseMouth(canvas, state, glow);
        drawWhiskers(canvas, state, glow);
        drawClawTips(canvas, state, glow);

        canvas.restore();
    }

    // ==================== 背景：深空星云 + 星星 + 流星 ====================

    /**
     * 绘制深空星云背景
     *
     * 渐变从左下角深紫到右上角深蓝，叠加 60~80 颗呼吸脉冲的星星
     * 和 1~2 颗对角线流星。
     *
     * @param canvas 画布
     * @param sw     屏幕宽度（px）
     * @param sh     屏幕高度（px）
     * @param state  状态（用于时间驱动动画）
     */
    private void drawBackground(Canvas canvas, float sw, float sh, RobotState state) {
        // 清屏为透明再叠加渐变
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // 创建或更新背景渐变（屏幕尺寸变化时重建）
        if (mBgGradient == null || sw != mLastGradientW || sh != mLastGradientH) {
            mBgGradient = new LinearGradient(0, sh, sw, 0,
                    COLOR_BG_DEEP_PURPLE, COLOR_BG_DEEP_BLUE, Shader.TileMode.CLAMP);
            mLastGradientW = sw;
            mLastGradientH = sh;
        }
        mParticlePaint.setShader(mBgGradient);
        mParticlePaint.setStyle(Paint.Style.FILL);
        mParticlePaint.clearShadowLayer();
        canvas.drawRect(0, 0, sw, sh, mParticlePaint);
        mParticlePaint.setShader(null);

        // 初始化星星（仅首次或屏幕尺寸变化时）
        if (!mStarsInitialized || sw != mLastGradientW || sh != mLastGradientH) {
            initStars(sw, sh);
        }

        // 计算 dt（基于 idleTimer 差值）
        float dt = state.idleTimer - mLastIdleTimer;
        if (dt < 0 || dt > 0.1f) dt = 1f / 60f;
        mLastIdleTimer = state.idleTimer;

        // 更新和绘制星星
        drawStars(canvas, sw, sh, state.idleTimer, dt);

        // 更新和绘制流星
        drawShootingStars(canvas, sw, sh, dt);
    }

    /**
     * 初始化星星位置和参数
     *
     * @param sw 屏幕宽度
     * @param sh 屏幕高度
     */
    private void initStars(float sw, float sh) {
        for (int i = 0; i < STAR_COUNT; i++) {
            mStarX[i] = mRandom.nextFloat() * sw;
            mStarY[i] = mRandom.nextFloat() * sh;
            mStarRadius[i] = 1f + mRandom.nextFloat() * 2f; // 1~3 dp
            mStarAlpha[i] = 0.3f + mRandom.nextFloat() * 0.5f;
            mStarPhase[i] = mRandom.nextFloat() * (float) (Math.PI * 2);
            mStarDriftX[i] = (mRandom.nextFloat() - 0.5f) * dp(3f); // 缓慢漂移
            mStarDriftY[i] = (mRandom.nextFloat() - 0.5f) * dp(2f);
        }
        mStarsInitialized = true;
    }

    /**
     * 更新和绘制星星
     *
     * 每颗星星以正弦波呼吸脉冲透明度，缓慢漂移，超出屏幕边界时环绕重置。
     *
     * @param canvas 画布
     * @param sw     屏幕宽度
     * @param sh     屏幕高度
     * @param time   当前时间（秒）
     * @param dt     帧间隔（秒）
     */
    private void drawStars(Canvas canvas, float sw, float sh, float time, float dt) {
        mParticlePaint.setStyle(Paint.Style.FILL);
        mParticlePaint.clearShadowLayer();

        for (int i = 0; i < STAR_COUNT; i++) {
            // 位置漂移
            mStarX[i] += mStarDriftX[i] * dt;
            mStarY[i] += mStarDriftY[i] * dt;

            // 屏幕环绕
            if (mStarX[i] < 0) mStarX[i] += sw;
            if (mStarX[i] > sw) mStarX[i] -= sw;
            if (mStarY[i] < 0) mStarY[i] += sh;
            if (mStarY[i] > sh) mStarY[i] -= sh;

            // 呼吸脉冲透明度
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.5f + mStarPhase[i]);
            float alpha = mStarAlpha[i] * pulse;
            int alphaInt = Math.min(255, (int) (alpha * 255));

            mParticlePaint.setColor(COLOR_CORE);
            mParticlePaint.setAlpha(alphaInt);
            canvas.drawCircle(mStarX[i], mStarY[i], dp(mStarRadius[i]), mParticlePaint);
        }
    }

    /**
     * 更新和绘制流星
     *
     * 每颗流星以对角线方向飞行，尾迹用半透明渐细线段表示。
     * 死亡的流星有一定概率重新生成。
     *
     * @param canvas 画布
     * @param sw     屏幕宽度
     * @param sh     屏幕高度
     * @param dt     帧间隔（秒）
     */
    private void drawShootingStars(Canvas canvas, float sw, float sh, float dt) {
        for (int i = 0; i < SHOOTING_STAR_MAX; i++) {
            if (mShootAlive[i]) {
                // 更新位置和生命
                mShootX[i] += mShootVX[i] * dt;
                mShootY[i] += mShootVY[i] * dt;
                mShootLife[i] -= dt;

                if (mShootLife[i] <= 0) {
                    mShootAlive[i] = false;
                    continue;
                }

                // 绘制流星线段（头部亮，尾部暗）
                float lifeRatio = mShootLife[i] / mShootMaxLife[i];
                float headAlpha = lifeRatio * 0.9f;
                float tailLen = dp(30f) * lifeRatio;
                // 尾巴方向为速度反方向的单位向量
                float speed = (float) Math.sqrt(mShootVX[i] * mShootVX[i]
                        + mShootVY[i] * mShootVY[i]);
                float nx = -mShootVX[i] / speed;
                float ny = -mShootVY[i] / speed;

                mParticlePaint.setStyle(Paint.Style.STROKE);
                mParticlePaint.setStrokeWidth(dp(1.5f));
                mParticlePaint.setStrokeCap(Paint.Cap.ROUND);
                mParticlePaint.setColor(COLOR_CORE);
                mParticlePaint.setAlpha((int) (headAlpha * 255));
                mParticlePaint.clearShadowLayer();
                canvas.drawLine(mShootX[i], mShootY[i],
                        mShootX[i] + nx * tailLen, mShootY[i] + ny * tailLen,
                        mParticlePaint);
            } else {
                // 死亡后小概率重生（约每 3 秒一颗）
                if (mRandom.nextFloat() < dt * 0.33f) {
                    mShootAlive[i] = true;
                    // 从屏幕上部随机位置出现，向右下飞行
                    mShootX[i] = mRandom.nextFloat() * sw;
                    mShootY[i] = mRandom.nextFloat() * sh * 0.3f;
                    float angle = 0.5f + mRandom.nextFloat() * 0.7f; // 约 30~70 度
                    float spd = dp(200f + mRandom.nextFloat() * 150f);
                    mShootVX[i] = (float) Math.cos(angle) * spd;
                    mShootVY[i] = (float) Math.sin(angle) * spd;
                    mShootMaxLife[i] = 0.4f + mRandom.nextFloat() * 0.6f;
                    mShootLife[i] = mShootMaxLife[i];
                }
            }
        }
    }

    // ==================== 尾巴 ====================

    /**
     * 绘制尾巴（6 段弹簧链）
     *
     * 从身体连接点开始，依次绘制 6 段逐渐变细的线段。
     * 每段角度由 TailPhysics 驱动的 state.tailAngles 控制。
     *
     * @param canvas   画布
     * @param state    猫咪状态
     * @param glow     当前发光强度
     * @param isGlow   true=绘制发光层，false=绘制核心层
     */
    private void drawTail(Canvas canvas, RobotState state, float glow, boolean isGlow) {
        float connX = dp(TAIL_CONN_X);
        float connY = dp(TAIL_CONN_Y);

        canvas.save();
        canvas.translate(connX, connY);

        // 累积角度计算每段的绝对方向
        float cumAngle = 0f;
        float curX = 0f;
        float curY = 0f;

        for (int i = 0; i < 6; i++) {
            cumAngle += state.tailAngles[i];
            float rad = (float) Math.toRadians(cumAngle);
            float segLen = dp(TAIL_LENGTHS[i]);
            float nextX = curX + (float) Math.sin(rad) * segLen;
            float nextY = curY + (float) Math.cos(rad) * segLen;

            Paint p;
            if (isGlow) {
                // 发光层：加粗 + 大 shadowLayer + 半透明主色
                p = mPrimaryGlow;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(TAIL_WIDTHS[i]) + dp(GLOW_EXTRA_STROKE));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(setAlpha(COLOR_PRIMARY, 0xCC));
                p.setShadowLayer(dp(GLOW_TAIL) * glow, 0, 0, COLOR_PRIMARY);
            } else {
                // 核心层：正常宽度 + 小 shadowLayer + 白色
                p = mPrimaryCore;
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(TAIL_WIDTHS[i]));
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(COLOR_CORE);
                p.setShadowLayer(dp(CORE_TAIL) * glow, 0, 0, COLOR_PRIMARY);
            }

            canvas.drawLine(curX, curY, nextX, nextY, p);
            curX = nextX;
            curY = nextY;
        }

        canvas.restore();
    }

    // ==================== 后腿 ====================

    /**
     * 绘制后腿（左右对称，带膝盖弯曲）
     *
     * 后腿从身体底部两侧延伸，有 15° 的膝盖弯曲角度。
     * 由锥形 Path（上宽下窄）绘制，底部有椭圆脚掌。
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawBackLegs(Canvas canvas, RobotState state, float glow) {
        drawLeg(canvas, -dp(BLEG_PIVOT_X), dp(BLEG_PIVOT_Y),
                dp(BLEG_UPPER_W), dp(BLEG_LOWER_W), dp(BLEG_LEN),
                BLEG_KNEE_BEND, glow, true);
        drawLeg(canvas, dp(BLEG_PIVOT_X), dp(BLEG_PIVOT_Y),
                dp(BLEG_UPPER_W), dp(BLEG_LOWER_W), dp(BLEG_LEN),
                -BLEG_KNEE_BEND, glow, true);
    }

    // ==================== 身体 ====================

    /**
     * 绘制身体梯形（上窄下宽，带圆角）
     *
     * 身体是一个上部 70dp 宽、下部 81dp 宽的圆角梯形。
     * 叠加胸部半透明弧形高光增强层次感。
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawBody(Canvas canvas, RobotState state, float glow) {
        float topHW = dp(BODY_TOP_W) / 2f;
        float botHW = dp(BODY_BOT_W) / 2f;
        float topY = dp(BODY_TOP_Y);
        float botY = dp(BODY_BOT_Y);
        float cr = dp(BODY_CORNER_R);

        // 构建梯形 Path（使用圆角近似）
        mBodyPath.reset();
        mBodyPath.moveTo(-topHW + cr, topY);
        mBodyPath.lineTo(topHW - cr, topY);
        mBodyPath.quadTo(topHW, topY, topHW, topY + cr);
        mBodyPath.lineTo(botHW, botY - cr);
        mBodyPath.quadTo(botHW, botY, botHW - cr, botY);
        mBodyPath.lineTo(-botHW + cr, botY);
        mBodyPath.quadTo(-botHW, botY, -botHW, botY - cr);
        mBodyPath.lineTo(-topHW, topY + cr);
        mBodyPath.quadTo(-topHW, topY, -topHW + cr, topY);
        mBodyPath.close();

        // 发光层
        setupGlowPaint(mPrimaryGlow, COLOR_PRIMARY, dp(2f) + dp(GLOW_EXTRA_STROKE),
                dp(GLOW_BODY) * glow);
        canvas.drawPath(mBodyPath, mPrimaryGlow);

        // 核心层
        setupCorePaint(mPrimaryCore, COLOR_PRIMARY, dp(2f), dp(CORE_BODY) * glow);
        canvas.drawPath(mBodyPath, mPrimaryCore);

        // 胸部高光弧线（半透明强调色）
        mTempRect.set(-dp(CHEST_RX), dp(CHEST_CY) - dp(CHEST_RY),
                dp(CHEST_RX), dp(CHEST_CY) + dp(CHEST_RY));
        mAccentGlow.setStyle(Paint.Style.STROKE);
        mAccentGlow.setStrokeWidth(dp(2f));
        mAccentGlow.setStrokeCap(Paint.Cap.ROUND);
        mAccentGlow.setColor(setAlpha(COLOR_ACCENT, 0x66));
        mAccentGlow.setShadowLayer(dp(8f) * glow, 0, 0, setAlpha(COLOR_ACCENT, 0x44));
        canvas.drawArc(mTempRect, 190f, CHEST_SWEEP, false, mAccentGlow);
    }

    // ==================== 前腿 ====================

    /**
     * 绘制前腿（左右对称，直线无膝盖弯曲）
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawFrontLegs(Canvas canvas, RobotState state, float glow) {
        drawLeg(canvas, -dp(FLEG_PIVOT_X), dp(FLEG_PIVOT_Y),
                dp(FLEG_UPPER_W), dp(FLEG_LOWER_W), dp(FLEG_LEN),
                0, glow, false);
        drawLeg(canvas, dp(FLEG_PIVOT_X), dp(FLEG_PIVOT_Y),
                dp(FLEG_UPPER_W), dp(FLEG_LOWER_W), dp(FLEG_LEN),
                0, glow, false);
    }

    /**
     * 绘制单条腿（锥形渐细 + 脚掌椭圆）
     *
     * 腿部使用两条线段模拟锥形（上宽下窄），底部绘制椭圆脚掌。
     * 支持膝盖弯曲角度（用于后腿）。
     *
     * @param canvas   画布
     * @param pivotX   枢轴 X（px）
     * @param pivotY   枢轴 Y（px）
     * @param upperW   上部宽度（px）
     * @param lowerW   下部宽度（px）
     * @param length   腿长（px）
     * @param kneeBend 膝盖弯曲角度（度），0 为直腿
     * @param glow     发光强度
     * @param isBack   是否为后腿（影响绘制层级细节）
     */
    private void drawLeg(Canvas canvas, float pivotX, float pivotY,
                         float upperW, float lowerW, float length,
                         float kneeBend, float glow, boolean isBack) {
        canvas.save();
        canvas.translate(pivotX, pivotY);

        float halfLen = length / 2f;
        float kneeRad = (float) Math.toRadians(kneeBend);
        // 上半段终点
        float midX = (float) Math.sin(kneeRad) * halfLen;
        float midY = (float) Math.cos(kneeRad) * halfLen;
        // 下半段终点（从膝盖继续向下）
        float endX = midX;
        float endY = midY + halfLen;

        // 构建锥形 Path
        mLegPath.reset();
        mLegPath.moveTo(-upperW / 2f, 0);
        mLegPath.lineTo(-lowerW / 2f, midY);
        mLegPath.lineTo(-lowerW / 2f + 1, endY);
        mLegPath.lineTo(lowerW / 2f - 1, endY);
        mLegPath.lineTo(lowerW / 2f, midY);
        mLegPath.lineTo(upperW / 2f, 0);
        mLegPath.close();

        // 发光层
        setupGlowPaint(mPrimaryGlow, COLOR_PRIMARY, dp(2f) + dp(GLOW_EXTRA_STROKE),
                dp(GLOW_LEG) * glow);
        canvas.drawPath(mLegPath, mPrimaryGlow);

        // 核心层
        setupCorePaint(mPrimaryCore, COLOR_PRIMARY, dp(2f), dp(CORE_LEG) * glow);
        canvas.drawPath(mLegPath, mPrimaryCore);

        // 脚掌椭圆
        float pawCX = (midX + endX) / 2f;
        mTempRect.set(pawCX - dp(PAW_RX), endY - dp(PAW_RY),
                pawCX + dp(PAW_RX), endY + dp(PAW_RY));
        // 发光层
        mPrimaryGlow.setStyle(Paint.Style.STROKE);
        mPrimaryGlow.setStrokeWidth(dp(2f) + dp(4f));
        mPrimaryGlow.setColor(setAlpha(COLOR_PRIMARY, 0xCC));
        mPrimaryGlow.setShadowLayer(dp(GLOW_LEG) * glow, 0, 0, COLOR_PRIMARY);
        canvas.drawOval(mTempRect, mPrimaryGlow);
        // 核心层
        mPrimaryCore.setStyle(Paint.Style.STROKE);
        mPrimaryCore.setStrokeWidth(dp(2f));
        mPrimaryCore.setColor(COLOR_CORE);
        mPrimaryCore.setShadowLayer(dp(CORE_LEG) * glow, 0, 0, COLOR_PRIMARY);
        canvas.drawOval(mTempRect, mPrimaryCore);

        canvas.restore();
    }

    // ==================== 头部 ====================

    /**
     * 绘制头部圆角矩形
     *
     * 头部中心位于 (0, HEAD_CY)，大小 HEAD_W × HEAD_H，圆角 HEAD_R。
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawHead(Canvas canvas, RobotState state, float glow) {
        float hw = dp(HEAD_W) / 2f;
        float hh = dp(HEAD_H) / 2f;
        float cy = dp(HEAD_CY);
        float r = dp(HEAD_R);

        mTempRect.set(-hw, cy - hh, hw, cy + hh);

        // 发光层
        setupGlowPaint(mPrimaryGlow, COLOR_PRIMARY, dp(2.5f) + dp(GLOW_EXTRA_STROKE),
                dp(GLOW_HEAD) * glow);
        canvas.drawRoundRect(mTempRect, r, r, mPrimaryGlow);

        // 核心层
        setupCorePaint(mPrimaryCore, COLOR_PRIMARY, dp(2.5f), dp(CORE_HEAD) * glow);
        canvas.drawRoundRect(mTempRect, r, r, mPrimaryCore);
    }

    // ==================== 耳朵 ====================

    /**
     * 绘制双耳（三角形外廓 + 缩小内侧三角）
     *
     * 左耳根据 state.leftEarAngle 旋转，右耳镜像处理。
     * 外廓为青色双层霓虹，内侧为品红色。
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawEars(Canvas canvas, RobotState state, float glow) {
        // 左耳
        drawSingleEar(canvas, EAR_L_AX, EAR_L_AY, EAR_L_BX, EAR_L_BY,
                EAR_L_CX, EAR_L_CY, state.leftEarAngle, glow);
        // 右耳（镜像 X 坐标）
        drawSingleEar(canvas, -EAR_L_AX, EAR_L_AY, -EAR_L_BX, EAR_L_BY,
                -EAR_L_CX, EAR_L_CY, -state.rightEarAngle, glow);
    }

    /**
     * 绘制单个耳朵
     *
     * @param canvas 画布
     * @param ax     顶点 A 的 X（dp）
     * @param ay     顶点 A 的 Y（dp）
     * @param bx     顶点 B 的 X（dp）
     * @param by     顶点 B 的 Y（dp）
     * @param cx     顶点 C 的 X（dp）
     * @param cy     顶点 C 的 Y（dp）
     * @param angle  旋转角度（度）
     * @param glow   发光强度
     */
    private void drawSingleEar(Canvas canvas, float ax, float ay,
                               float bx, float by, float cx, float cy,
                               float angle, float glow) {
        // 计算三角形质心（用于旋转和内侧缩放的锚点）
        float centroidX = dp((ax + bx + cx) / 3f);
        float centroidY = dp((ay + by + cy) / 3f);

        canvas.save();
        canvas.rotate(angle, centroidX, centroidY);

        // 外廓三角形
        mEarPath.reset();
        mEarPath.moveTo(dp(ax), dp(ay));
        mEarPath.lineTo(dp(bx), dp(by));
        mEarPath.lineTo(dp(cx), dp(cy));
        mEarPath.close();

        // 外廓发光层
        setupGlowPaint(mPrimaryGlow, COLOR_PRIMARY, dp(EAR_STROKE) + dp(GLOW_EXTRA_STROKE),
                dp(GLOW_EAR) * glow);
        canvas.drawPath(mEarPath, mPrimaryGlow);

        // 外廓核心层
        setupCorePaint(mPrimaryCore, COLOR_PRIMARY, dp(EAR_STROKE), dp(CORE_EAR) * glow);
        canvas.drawPath(mEarPath, mPrimaryCore);

        // 内侧三角形（缩小 0.7x，从质心向内）
        float s = EAR_INNER_SCALE;
        float iax = centroidX + (dp(ax) - centroidX) * s;
        float iay = centroidY + (dp(ay) - centroidY) * s;
        float ibx = centroidX + (dp(bx) - centroidX) * s;
        float iby = centroidY + (dp(by) - centroidY) * s;
        float icx = centroidX + (dp(cx) - centroidX) * s;
        float icy = centroidY + (dp(cy) - centroidY) * s;

        mEarInnerPath.reset();
        mEarInnerPath.moveTo(iax, iay);
        mEarInnerPath.lineTo(ibx, iby);
        mEarInnerPath.lineTo(icx, icy);
        mEarInnerPath.close();

        // 内侧发光层（品红色）
        setupGlowPaint(mAccentGlow, COLOR_ACCENT, dp(2f) + dp(4f), dp(10f) * glow);
        canvas.drawPath(mEarInnerPath, mAccentGlow);

        // 内侧核心层
        setupCorePaint(mAccentCore, COLOR_ACCENT, dp(2f), dp(3f) * glow);
        canvas.drawPath(mEarInnerPath, mAccentCore);

        canvas.restore();
    }

    // ==================== 眼睛 ====================

    /**
     * 绘制双眼（眼眶 + 瞳孔，形态随表情变化）
     *
     * - IDLE：竖椭圆瞳孔，品红色，周期性眨眼
     * - EXCITED：较圆的瞳孔，绿色发光
     * - SURPRISED：完全圆形瞳孔，黄色发光，眼眶放大 1.4x
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawEyes(Canvas canvas, RobotState state, float glow) {
        float openness = state.eyeOpenness;
        float dilation = state.pupilDilation;

        // 根据表情确定瞳孔颜色和形状参数
        int pupilColor;
        float pupilRxMul = 1.0f; // 瞳孔水平半径乘数
        float socketScale = 1.0f; // 眼眶缩放
        switch (state.expression) {
            case EXCITED:
                pupilColor = COLOR_EYE_GREEN;
                pupilRxMul = 1.5f; // 更圆的瞳孔
                break;
            case SURPRISED:
                pupilColor = COLOR_SURPRISED_YELLOW;
                pupilRxMul = 1.8f; // 几乎圆形
                socketScale = 1.4f;
                break;
            default:
                pupilColor = COLOR_ACCENT;
                break;
        }

        // 左眼
        drawSingleEye(canvas, dp(EYE_L_CX), dp(EYE_CY),
                dp(EYE_RX) * socketScale, dp(EYE_RY) * socketScale * openness,
                dp(state.eyePupilOffsetX), dp(state.eyePupilOffsetY),
                dp(PUPIL_RX) * dilation * pupilRxMul, dp(PUPIL_RY) * dilation * openness,
                pupilColor, glow, openness);

        // 右眼
        drawSingleEye(canvas, dp(EYE_R_CX), dp(EYE_CY),
                dp(EYE_RX) * socketScale, dp(EYE_RY) * socketScale * openness,
                dp(state.eyePupilOffsetX), dp(state.eyePupilOffsetY),
                dp(PUPIL_RX) * dilation * pupilRxMul, dp(PUPIL_RY) * dilation * openness,
                pupilColor, glow, openness);
    }

    /**
     * 绘制单个眼睛（眼眶描边 + 瞳孔填充）
     *
     * @param canvas     画布
     * @param cx         眼眶中心 X（px）
     * @param cy         眼眶中心 Y（px）
     * @param socketRX   眼眶水平半径（px）
     * @param socketRY   眼眶垂直半径（px，受 openness 缩放）
     * @param pupilOffX  瞳孔水平偏移（px）
     * @param pupilOffY  瞳孔垂直偏移（px）
     * @param pupilRX    瞳孔水平半径（px）
     * @param pupilRY    瞳孔垂直半径（px）
     * @param pupilColor 瞳孔颜色
     * @param glow       发光强度
     * @param openness   眼睛睁开度（0=闭合，1=正常，1.4=惊讶）
     */
    private void drawSingleEye(Canvas canvas, float cx, float cy,
                               float socketRX, float socketRY,
                               float pupilOffX, float pupilOffY,
                               float pupilRX, float pupilRY,
                               int pupilColor, float glow, float openness) {
        // 眼睛几乎闭合时只画一条线
        if (openness < 0.1f) {
            mPrimaryGlow.setStyle(Paint.Style.STROKE);
            mPrimaryGlow.setStrokeWidth(dp(2f));
            mPrimaryGlow.setStrokeCap(Paint.Cap.ROUND);
            mPrimaryGlow.setColor(COLOR_CORE);
            mPrimaryGlow.setShadowLayer(dp(CORE_EYE) * glow, 0, 0, COLOR_PRIMARY);
            canvas.drawLine(cx - socketRX * 0.7f, cy, cx + socketRX * 0.7f, cy, mPrimaryGlow);
            return;
        }

        // 眼眶描边
        mTempRect.set(cx - socketRX, cy - socketRY, cx + socketRX, cy + socketRY);
        // 发光层
        mPrimaryGlow.setStyle(Paint.Style.STROKE);
        mPrimaryGlow.setStrokeWidth(dp(2f) + dp(4f));
        mPrimaryGlow.setStrokeCap(Paint.Cap.ROUND);
        mPrimaryGlow.setColor(setAlpha(COLOR_PRIMARY, 0xAA));
        mPrimaryGlow.setShadowLayer(dp(GLOW_EYE) * glow, 0, 0, COLOR_PRIMARY);
        canvas.drawOval(mTempRect, mPrimaryGlow);
        // 核心层
        mPrimaryCore.setStyle(Paint.Style.STROKE);
        mPrimaryCore.setStrokeWidth(dp(1.5f));
        mPrimaryCore.setColor(COLOR_CORE);
        mPrimaryCore.setShadowLayer(dp(CORE_EYE) * glow, 0, 0, COLOR_PRIMARY);
        canvas.drawOval(mTempRect, mPrimaryCore);

        // 瞳孔填充
        float pcx = cx + pupilOffX;
        float pcy = cy + pupilOffY;
        mTempRect.set(pcx - pupilRX, pcy - pupilRY, pcx + pupilRX, pcy + pupilRY);
        // 发光层
        mEyePaint.setStyle(Paint.Style.FILL);
        mEyePaint.setColor(setAlpha(pupilColor, 0xCC));
        mEyePaint.setShadowLayer(dp(GLOW_EYE) * glow, 0, 0, pupilColor);
        canvas.drawOval(mTempRect, mEyePaint);
        // 核心层（更亮的中心）
        float innerScale = 0.6f;
        mTempRect.set(pcx - pupilRX * innerScale, pcy - pupilRY * innerScale,
                pcx + pupilRX * innerScale, pcy + pupilRY * innerScale);
        mEyePaint.setColor(COLOR_CORE);
        mEyePaint.setShadowLayer(dp(CORE_EYE) * glow * 0.5f, 0, 0, pupilColor);
        canvas.drawOval(mTempRect, mEyePaint);
    }

    // ==================== 鼻子和嘴巴 ====================

    /**
     * 绘制鼻子（倒三角形）和嘴巴（两条贝塞尔弧线）
     *
     * 鼻子为品红色小倒三角，嘴巴从鼻子底部向两侧延伸的曲线。
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawNoseMouth(Canvas canvas, RobotState state, float glow) {
        float noseCY = dp(NOSE_CY);
        float noseHW = dp(NOSE_HW);
        float noseH = dp(NOSE_H);
        float noseTop = noseCY - noseH / 2f;
        float noseBot = noseCY + noseH / 2f;

        // 鼻子倒三角 Path
        mTempPath.reset();
        mTempPath.moveTo(-noseHW, noseTop);
        mTempPath.lineTo(noseHW, noseTop);
        mTempPath.lineTo(0, noseBot);
        mTempPath.close();

        // 鼻子发光层（品红）
        mAccentGlow.setStyle(Paint.Style.FILL);
        mAccentGlow.setColor(setAlpha(COLOR_ACCENT, 0xCC));
        mAccentGlow.setShadowLayer(dp(10f) * glow, 0, 0, COLOR_ACCENT);
        canvas.drawPath(mTempPath, mAccentGlow);

        // 鼻子核心层
        mAccentCore.setStyle(Paint.Style.FILL);
        mAccentCore.setColor(COLOR_CORE);
        mAccentCore.setShadowLayer(dp(3f) * glow, 0, 0, COLOR_ACCENT);
        float noseInnerScale = 0.6f;
        mTempPath.reset();
        mTempPath.moveTo(-noseHW * noseInnerScale, noseTop + noseH * 0.15f);
        mTempPath.lineTo(noseHW * noseInnerScale, noseTop + noseH * 0.15f);
        mTempPath.lineTo(0, noseBot - noseH * 0.1f);
        mTempPath.close();
        canvas.drawPath(mTempPath, mAccentCore);

        // 嘴巴：从鼻底分成两条贝塞尔弧线
        float mouthStartY = noseBot;
        float mouthSideW = dp(MOUTH_SIDE_W);

        mMouthPath.reset();
        // 左侧弧线
        mMouthPath.moveTo(0, mouthStartY);
        mMouthPath.quadTo(-mouthSideW * 0.5f, mouthStartY + dp(5f),
                -mouthSideW, mouthStartY + dp(2f));
        // 右侧弧线
        mMouthPath.moveTo(0, mouthStartY);
        mMouthPath.quadTo(mouthSideW * 0.5f, mouthStartY + dp(5f),
                mouthSideW, mouthStartY + dp(2f));

        // 嘴巴发光层
        mPrimaryGlow.setStyle(Paint.Style.STROKE);
        mPrimaryGlow.setStrokeWidth(dp(1.5f) + dp(4f));
        mPrimaryGlow.setStrokeCap(Paint.Cap.ROUND);
        mPrimaryGlow.setColor(setAlpha(COLOR_PRIMARY, 0xAA));
        mPrimaryGlow.setShadowLayer(dp(8f) * glow, 0, 0, COLOR_PRIMARY);
        canvas.drawPath(mMouthPath, mPrimaryGlow);

        // 嘴巴核心层
        mPrimaryCore.setStyle(Paint.Style.STROKE);
        mPrimaryCore.setStrokeWidth(dp(1.5f));
        mPrimaryCore.setStrokeCap(Paint.Cap.ROUND);
        mPrimaryCore.setColor(COLOR_CORE);
        mPrimaryCore.setShadowLayer(dp(3f) * glow, 0, 0, COLOR_PRIMARY);
        canvas.drawPath(mMouthPath, mPrimaryCore);
    }

    // ==================== 胡须 ====================

    /**
     * 绘制胡须（左右各 3 根）
     *
     * 从脸颊两侧向外延伸，轻微上中下分散角度。
     * 双层霓虹效果（青色发光 + 白色核心）。
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawWhiskers(Canvas canvas, RobotState state, float glow) {
        // 三根胡须的角度偏移（度），从上到下
        float[] angles = {-15f, 0f, 15f};

        float len = dp(WHISKER_LEN);
        float startLX = dp(WHISKER_L_X);
        float startRX = dp(WHISKER_R_X);
        float startY = dp(WHISKER_Y);

        for (int i = 0; i < 3; i++) {
            float rad = (float) Math.toRadians(angles[i]);
            float endLX = startLX - len * (float) Math.cos(rad);
            float endLY = startY + len * (float) Math.sin(rad);
            float endRX = startRX + len * (float) Math.cos(rad);
            float endRY = startY + len * (float) Math.sin(rad);

            // 左侧胡须 - 发光层
            mPrimaryGlow.setStyle(Paint.Style.STROKE);
            mPrimaryGlow.setStrokeWidth(dp(WHISKER_STROKE) + dp(GLOW_EXTRA_STROKE));
            mPrimaryGlow.setStrokeCap(Paint.Cap.ROUND);
            mPrimaryGlow.setColor(setAlpha(COLOR_PRIMARY, 0xCC));
            mPrimaryGlow.setShadowLayer(dp(GLOW_WHISKER) * glow, 0, 0, COLOR_PRIMARY);
            canvas.drawLine(startLX, startY, endLX, endLY, mPrimaryGlow);

            // 左侧胡须 - 核心层
            mPrimaryCore.setStyle(Paint.Style.STROKE);
            mPrimaryCore.setStrokeWidth(dp(WHISKER_STROKE));
            mPrimaryCore.setStrokeCap(Paint.Cap.ROUND);
            mPrimaryCore.setColor(COLOR_CORE);
            mPrimaryCore.setShadowLayer(dp(CORE_WHISKER) * glow, 0, 0, COLOR_PRIMARY);
            canvas.drawLine(startLX, startY, endLX, endLY, mPrimaryCore);

            // 右侧胡须 - 发光层
            canvas.drawLine(startRX, startY, endRX, endRY, mPrimaryGlow);
            // 右侧胡须 - 核心层
            canvas.drawLine(startRX, startY, endRX, endRY, mPrimaryCore);
        }
    }

    // ==================== 爪尖 ====================

    /**
     * 绘制爪尖（每只脚掌 3 个小弧形）
     *
     * 品红色的小弧线，从脚掌底部伸出，增强猫爪的可爱度和细节。
     *
     * @param canvas 画布
     * @param state  猫咪状态
     * @param glow   发光强度
     */
    private void drawClawTips(Canvas canvas, RobotState state, float glow) {
        // 4 只脚的 X 位置（前左、前右、后左、后右）
        float[] pawXs = {-dp(FLEG_PIVOT_X), dp(FLEG_PIVOT_X),
                -dp(BLEG_PIVOT_X), dp(BLEG_PIVOT_X)};
        // 对应的 Y 位置（枢轴 Y + 腿长）
        float[] pawYs = {dp(FLEG_PIVOT_Y) + dp(FLEG_LEN),
                dp(FLEG_PIVOT_Y) + dp(FLEG_LEN),
                dp(BLEG_PIVOT_Y) + dp(BLEG_LEN),
                dp(BLEG_PIVOT_Y) + dp(BLEG_LEN)};

        float clawLen = dp(CLAW_LEN);

        mAccentGlow.setStyle(Paint.Style.STROKE);
        mAccentGlow.setStrokeWidth(dp(1.5f) + dp(3f));
        mAccentGlow.setStrokeCap(Paint.Cap.ROUND);
        mAccentGlow.setColor(setAlpha(COLOR_ACCENT, 0xBB));
        mAccentGlow.setShadowLayer(dp(6f) * glow, 0, 0, COLOR_ACCENT);

        mAccentCore.setStyle(Paint.Style.STROKE);
        mAccentCore.setStrokeWidth(dp(1f));
        mAccentCore.setStrokeCap(Paint.Cap.ROUND);
        mAccentCore.setColor(COLOR_CORE);
        mAccentCore.setShadowLayer(dp(2f) * glow, 0, 0, COLOR_ACCENT);

        for (int p = 0; p < 4; p++) {
            float px = pawXs[p];
            float py = pawYs[p];
            // 3 个爪尖，左中右分布
            float[] offsets = {-dp(4f), 0f, dp(4f)};
            for (int c = 0; c < 3; c++) {
                float cx = px + offsets[c];
                // 发光层
                canvas.drawLine(cx, py, cx, py + clawLen, mAccentGlow);
                // 核心层
                canvas.drawLine(cx, py, cx, py + clawLen, mAccentCore);
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 配置发光层 Paint（描边模式 + 加粗 + 大 shadowLayer + 半透明主色）
     *
     * @param paint       目标 Paint
     * @param glowColor   发光色
     * @param strokeWidth 描边宽度（px，已含 GLOW_EXTRA_STROKE）
     * @param shadowRadius Shadow 半径（px）
     */
    private void setupGlowPaint(Paint paint, int glowColor, float strokeWidth,
                                float shadowRadius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(setAlpha(glowColor, 0xCC));
        paint.setShadowLayer(shadowRadius, 0, 0, glowColor);
    }

    /**
     * 配置核心层 Paint（描边模式 + 正常宽度 + 小 shadowLayer + 白色）
     *
     * @param paint       目标 Paint
     * @param shadowColor Shadow 颜色（主色调）
     * @param strokeWidth 描边宽度（px）
     * @param shadowRadius Shadow 半径（px）
     */
    private void setupCorePaint(Paint paint, int shadowColor, float strokeWidth,
                                float shadowRadius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(COLOR_CORE);
        paint.setShadowLayer(shadowRadius, 0, 0, shadowColor);
    }

    /**
     * 设置颜色的 alpha 通道值
     *
     * @param color 原始 ARGB 颜色
     * @param alpha 新的 alpha 值（0x00~0xFF）
     * @return 带新 alpha 的 ARGB 颜色
     */
    private static int setAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}

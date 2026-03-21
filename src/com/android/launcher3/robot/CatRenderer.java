package com.android.launcher3.robot;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * 扁平风格 AI 机器人 Canvas 2D 渲染器（增强版）
 *
 * 使用纯 Canvas Path/drawArc/drawCircle/drawRoundRect 绘制可爱的白色卡通机器人。
 * 所有形状采用「白色填充 + 深灰描边」的扁平设计风格，眼睛带有青色发光效果。
 *
 * 视觉风格：
 * - 深空星云渐变背景（深紫 #0A0618 → 深蓝 #0C1428）
 * - 白色圆头机器人（#F0F0F0 填充 + #3A3A3A 描边）
 * - 青色发光圆眼（#00D4FF 外环 + #1A1A1A 黑瞳）
 * - 深色面板/关节（#2D2D2D / #4A4A4A）
 * - 60~80 颗星星粒子 + 流星效果 + 环境辉光
 * - 天线、胸部徽章、面板接缝线、肩甲等丰富视觉细节
 * - 表情系统：IDLE（正常）、EXCITED（眼睛更亮、轻微倾斜）、SURPRISED（眼睛圆睁）
 *
 * 所有尺寸使用 dp 定义，运行时根据 Canvas 密度转 px。
 * 绘制坐标以机器人 bodyX/bodyY 为原点，各部件相对定位。
 *
 * 绘制顺序（后→前）：
 * 背景星空 → 环境辉光 → 腿/脚 → 身体 → 手臂 → 脖子 → 头部 → 天线 → 侧耳 → 面板 → 眼睛 → 额头点 → 指示灯
 */
public class CatRenderer {

    // ==================== 颜色常量 ====================

    /** 机器人身体白色填充 */
    private static final int COLOR_BODY_WHITE = 0xFFF0F0F0;

    /** 深灰描边色（轮廓线） */
    private static final int COLOR_OUTLINE = 0xFF3A3A3A;

    /** 关节深灰色（肩/肘/腕/膝关节球） */
    private static final int COLOR_JOINT = 0xFF4A4A4A;

    /** 眼睛青色发光环 */
    private static final int COLOR_EYE_CYAN = 0xFF00D4FF;

    /** 眼睛黑色瞳孔 */
    private static final int COLOR_PUPIL = 0xFF1A1A1A;

    /** 面部深色面板（面罩/显示屏区域） */
    private static final int COLOR_FACE_PANEL = 0xFF2D2D2D;

    /** 核心白：高光和粒子白色 */
    private static final int COLOR_CORE = 0xFFFFFFFF;

    /** 深空背景左下角色：深紫 */
    private static final int COLOR_BG_DEEP_PURPLE = 0xFF0A0618;

    /** 深空背景右上角色：深蓝 */
    private static final int COLOR_BG_DEEP_BLUE = 0xFF0C1428;

    /** 绿色指示灯色 */
    private static final int COLOR_INDICATOR_GREEN = 0xFF00FF88;

    /** 琥珀色指示灯色 */
    private static final int COLOR_INDICATOR_AMBER = 0xFFFFB800;

    /** 面板内高亮边框色（增加深度感） */
    private static final int COLOR_PANEL_HIGHLIGHT = 0xFF555555;

    // ==================== 机器人比例尺寸（dp） ====================
    // 目标：机器人占屏幕高度 ~85%，约 720dp (@2.75x 密度)
    // 头部 ~45%, 身体 ~25%, 腿+脚 ~30%，在原 596dp 基础上按 1.21x 放大

    // ---- 头部 ----
    /** 头部宽度（dp） */
    private static final float HEAD_W = 220f;
    /** 头部高度（dp） */
    private static final float HEAD_H = 205f;
    /** 头部圆角半径（dp，接近圆形） */
    private static final float HEAD_R = 90f;
    /** 头部中心 Y 偏移（相对于机器人原点） */
    private static final float HEAD_CY = -195f;

    // ---- 面部面板（面罩/显示屏） ----
    /** 面板宽度（dp） */
    private static final float PANEL_W = 170f;
    /** 面板高度（dp） */
    private static final float PANEL_H = 75f;
    /** 面板圆角（dp） */
    private static final float PANEL_R = 22f;
    /** 面板中心 Y 偏移（相对于头部中心） */
    private static final float PANEL_OFFSET_Y = 10f;

    // ---- 眼睛 ----
    /** 左眼中心 X（相对于头部中心，负=左） */
    private static final float EYE_L_CX = -36f;
    /** 右眼中心 X（相对于头部中心） */
    private static final float EYE_R_CX = 36f;
    /** 眼睛中心 Y（相对于面板中心） */
    private static final float EYE_CY_OFFSET = 0f;
    /** 眼睛外环半径（dp） */
    private static final float EYE_OUTER_R = 22f;
    /** 眼睛瞳孔半径（dp） */
    private static final float EYE_PUPIL_R = 10f;
    /** 眼睛发光环宽度（dp） */
    private static final float EYE_GLOW_RING_W = 5f;

    // ---- 额头小点 ----
    /** 额头小点半径（dp） */
    private static final float FOREHEAD_DOT_R = 4f;
    /** 额头小点 Y 偏移（相对于头部顶部） */
    private static final float FOREHEAD_DOT_Y = 36f;
    /** 两个额头小点的 X 间距（各一侧，dp） */
    private static final float FOREHEAD_DOT_SPACING = 15f;

    // ---- 侧耳（耳机垫块） ----
    /** 侧耳块宽度（dp） */
    private static final float EAR_BLOCK_W = 27f;
    /** 侧耳块高度（dp） */
    private static final float EAR_BLOCK_H = 67f;
    /** 侧耳块圆角（dp） */
    private static final float EAR_BLOCK_R = 10f;
    /** 侧耳块 X 偏移（从头部边缘向外，dp） */
    private static final float EAR_BLOCK_OFFSET_X = 2f;
    /** 侧耳块青色点缀线宽度（dp） */
    private static final float EAR_ACCENT_LINE_W = 2f;

    // ---- 脖子 ----
    /** 脖子宽度（dp） */
    private static final float NECK_W = 34f;
    /** 脖子高度（dp） */
    private static final float NECK_H = 22f;

    // ---- 身体（盾形/心形躯干） ----
    /** 身体顶部宽度（dp） */
    private static final float BODY_TOP_W = 145f;
    /** 身体最大宽度（dp，中部肩膀处） */
    private static final float BODY_MID_W = 157f;
    /** 身体高度（dp） */
    private static final float BODY_H = 145f;
    /** 身体顶部 Y 偏移（相对于原点，脖子下方） */
    private static final float BODY_TOP_Y = -70f;
    /** 身体圆角（dp） */
    private static final float BODY_R = 30f;

    // ---- 手臂 ----
    /** 上臂长度（dp） */
    private static final float ARM_UPPER_LEN = 67f;
    /** 前臂长度（dp） */
    private static final float ARM_FOREARM_LEN = 60f;
    /** 手臂管宽度（dp） */
    private static final float ARM_TUBE_W = 20f;
    /** 关节球半径（dp） */
    private static final float JOINT_R = 9f;
    /** 肩膀关节 Y（相对于身体顶部，dp） */
    private static final float SHOULDER_Y_OFFSET = 18f;
    /** 手指数量 */
    private static final int FINGER_COUNT = 4;
    /** 手指长度（dp） */
    private static final float FINGER_LEN = 12f;
    /** 手指宽度（dp） */
    private static final float FINGER_W = 5f;

    // ---- 臀部连接器 ----
    /** 臀部连接块高度（dp） */
    private static final float HIP_H = 15f;
    /** 臀部连接块宽度（dp） */
    private static final float HIP_W = 60f;

    // ---- 腿 ----
    /** 腿长度（dp） */
    private static final float LEG_LEN = 67f;
    /** 腿管宽度（dp） */
    private static final float LEG_TUBE_W = 22f;
    /** 左腿 X 偏移（dp） */
    private static final float LEG_X_OFFSET = 27f;

    // ---- 脚/靴子 ----
    /** 靴子宽度（dp） */
    private static final float BOOT_W = 46f;
    /** 靴子高度（dp） */
    private static final float BOOT_H = 34f;
    /** 靴子圆角（dp） */
    private static final float BOOT_R = 12f;
    /** 靴子侧面圆形点缀半径（dp） */
    private static final float BOOT_CIRCLE_R = 6f;

    // ---- 天线参数 ----
    /** 天线杆高度（dp） */
    private static final float ANTENNA_HEIGHT = 15f;
    /** 天线顶球半径（dp） */
    private static final float ANTENNA_BALL_R = 5f;

    // ---- 胸部徽章参数 ----
    /** 胸部徽章半径（dp） */
    private static final float CHEST_EMBLEM_R = 15f;

    // ---- 描边宽度常量 ----
    /** 主轮廓描边宽度（dp） */
    private static final float STROKE_W = 2f;
    /** 细节描边宽度（dp） */
    private static final float STROKE_THIN = 1.5f;

    // ==================== 星空背景参数 ====================

    /** 星星数量 */
    private static final int STAR_COUNT = 70;

    /** 流星最大数量 */
    private static final int SHOOTING_STAR_MAX = 2;

    // ==================== 复用绘制对象 ====================

    /** 白色填充画笔（机器人主体填充） */
    private final Paint mFillPaint;

    /** 深灰描边画笔（轮廓线） */
    private final Paint mStrokePaint;

    /** 关节/面板深色填充画笔 */
    private final Paint mDarkFillPaint;

    /** 眼睛发光画笔（青色环 + shadowLayer 辉光） */
    private final Paint mEyeGlowPaint;

    /** 眼睛核心画笔（瞳孔黑色填充） */
    private final Paint mEyeCorePaint;

    /** 粒子/星空/背景通用画笔 */
    private final Paint mParticlePaint;

    /** 青色点缀线画笔（耳朵、靴子装饰） */
    private final Paint mAccentPaint;

    /** 细节装饰画笔（高光弧、指示灯、面板线等新增视觉元素） */
    private final Paint mDetailPaint;

    /** 复用 Path：身体轮廓 */
    private final Path mBodyPath;

    /** 复用 Path：头部轮廓 */
    private final Path mHeadPath;

    /** 复用 Path：手臂 */
    private final Path mArmPath;

    /** 复用 Path：腿部 */
    private final Path mLegPath;

    /** 复用 Path：通用临时 */
    private final Path mTempPath;

    /** 复用矩形对象 */
    private final RectF mTempRect;

    /** 复用矩形对象 2（避免嵌套时冲突） */
    private final RectF mTempRect2;

    /** 复用矩形对象 3（三层嵌套绘制用） */
    private final RectF mTempRect3;

    /** 屏幕密度（dp→px 转换因子） */
    private float mDensity = 2.75f;

    /** 星空背景渐变 Shader，首次绘制时根据屏幕尺寸创建 */
    private LinearGradient mBgGradient;
    /** 上次创建渐变时的屏幕宽度，用于检测尺寸变化 */
    private float mLastGradientW;
    /** 上次创建渐变时的屏幕高度 */
    private float mLastGradientH;

    /** 头部区域环境辉光径向渐变 */
    private RadialGradient mAmbientGlowGradient;
    /** 上次环境辉光创建时的屏幕宽度 */
    private float mLastAmbientW;
    /** 上次环境辉光创建时的屏幕高度 */
    private float mLastAmbientH;

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
    /** 星星类型标记：0=普通白色，1=大号青色调 */
    private final int[] mStarType = new int[STAR_COUNT];
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
     * 构造 AI 机器人渲染器
     *
     * 预创建所有 Paint 和 Path 对象，避免每帧 new 导致 GC 压力。
     * Paint 参数在各绘制方法中按需设置。
     */
    public CatRenderer() {
        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDarkFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEyeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mEyeCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mParticlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAccentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDetailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        mBodyPath = new Path();
        mHeadPath = new Path();
        mArmPath = new Path();
        mLegPath = new Path();
        mTempPath = new Path();
        mTempRect = new RectF();
        mTempRect2 = new RectF();
        mTempRect3 = new RectF();
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
     * 按照从后到前的层次绘制所有部件，实现完整的 AI 机器人视觉效果。
     * 每帧由 RobotRenderThread 调用。
     *
     * 绘制顺序：背景 → 环境辉光 → 腿/脚 → 身体 → 手臂 → 脖子 → 头部 → 天线 → 侧耳 → 面板 → 眼睛 → 额头点 → 指示灯
     *
     * @param canvas 绘制目标画布（来自 TextureView.lockCanvas，软件渲染模式）
     * @param state  当前机器人动画状态（位置、表情、眼睛参数等）
     */
    public void draw(Canvas canvas, RobotState state) {
        // 从 Canvas 获取密度（TextureView lockCanvas 可能返回特殊值）
        int canvasDensity = canvas.getDensity();
        if (canvasDensity > 0 && canvasDensity != 0xFFFF) {
            mDensity = canvasDensity / 160f;
        }

        float sw = state.screenWidth > 0 ? state.screenWidth : canvas.getWidth();
        float sh = state.screenHeight > 0 ? state.screenHeight : canvas.getHeight();

        // 绘制深空星云渐变背景
        drawBackground(canvas, sw, sh, state);

        canvas.save();

        // 平移到机器人身体中心（bodyY 默认 sh/2，机器人放大后居中即可）
        canvas.translate(state.bodyX, state.bodyY);

        // 呼吸浮动 + 旋转（利用 idleTimer 产生缓慢上下浮动效果）
        float bobOffset = (float) Math.sin(state.idleTimer * 1.2) * dp(5f);
        canvas.translate(0, bobOffset);
        canvas.rotate(state.rotation);

        // 呼吸缩放（bodyScale 在 0.98~1.02 范围，保持机器人自然呼吸感）
        float scale = state.bodyScale;
        if (scale > 0.01f) {
            canvas.scale(scale, scale);
        }

        // 表情影响：EXCITED 时轻微前倾
        if (state.expression == RobotState.Expression.EXCITED) {
            canvas.rotate(2f * state.expressionBlend);
        }

        float glow = state.glowIntensity;

        // ---- 绘制顺序：后→前 ----
        drawLegsAndFeet(canvas, state, glow);
        drawBody(canvas, state, glow);
        drawArms(canvas, state, glow);
        drawNeck(canvas, state, glow);
        drawHead(canvas, state, glow);
        drawAntenna(canvas, state, glow);
        drawEarBlocks(canvas, state, glow);
        drawFacePanel(canvas, state, glow);
        drawEyes(canvas, state, glow);
        drawForeheadDots(canvas, state, glow);
        drawIndicatorLights(canvas, state, glow);

        canvas.restore();
    }

    // ==================== 背景：深空星云 + 星星 + 流星 + 环境辉光 ====================

    /**
     * 绘制深空星云背景
     *
     * 渐变从左下角深紫到右上角深蓝，叠加 60~80 颗呼吸脉冲的星星
     * 和 1~2 颗对角线流星。最后绘制一个柔和的头部区域环境辉光。
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

        // 绘制机器人头部后方的环境辉光（柔和青色光晕）
        drawAmbientGlow(canvas, sw, sh, state);
    }

    /**
     * 绘制机器人头部后方的柔和环境辉光
     *
     * 在机器人头部区域绘制一个大的低透明度径向渐变，
     * 营造出机器人是画面焦点的光晕效果。
     *
     * @param canvas 画布
     * @param sw     屏幕宽度
     * @param sh     屏幕高度
     * @param state  机器人状态（获取 bodyX/bodyY）
     */
    private void drawAmbientGlow(Canvas canvas, float sw, float sh, RobotState state) {
        float cx = state.bodyX;
        // 辉光中心偏向头部位置（bodyY 上方约 HEAD_CY 处）
        float cy = state.bodyY + dp(HEAD_CY);
        float radius = dp(200f);

        // 屏幕尺寸变化时重建径向渐变
        if (mAmbientGlowGradient == null || sw != mLastAmbientW || sh != mLastAmbientH) {
            mAmbientGlowGradient = new RadialGradient(cx, cy, radius,
                    new int[]{0x1800D4FF, 0x0800D4FF, 0x00000000},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP);
            mLastAmbientW = sw;
            mLastAmbientH = sh;
        }

        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setShader(mAmbientGlowGradient);
        mDetailPaint.clearShadowLayer();
        canvas.drawCircle(cx, cy, radius, mDetailPaint);
        mDetailPaint.setShader(null);
    }

    /**
     * 初始化星星位置和参数
     *
     * 10% 的星星为大号青色调变体，其余为普通白色小星星。
     *
     * @param sw 屏幕宽度
     * @param sh 屏幕高度
     */
    private void initStars(float sw, float sh) {
        for (int i = 0; i < STAR_COUNT; i++) {
            mStarX[i] = mRandom.nextFloat() * sw;
            mStarY[i] = mRandom.nextFloat() * sh;
            mStarPhase[i] = mRandom.nextFloat() * (float) (Math.PI * 2);
            mStarDriftX[i] = (mRandom.nextFloat() - 0.5f) * dp(3f); // 缓慢漂移
            mStarDriftY[i] = (mRandom.nextFloat() - 0.5f) * dp(2f);

            // 10% 概率为大号青色调星星
            if (mRandom.nextFloat() < 0.1f) {
                mStarType[i] = 1;
                mStarRadius[i] = 2.5f + mRandom.nextFloat() * 1.5f; // 2.5~4 dp（更大）
                mStarAlpha[i] = 0.4f + mRandom.nextFloat() * 0.4f;
            } else {
                mStarType[i] = 0;
                mStarRadius[i] = 1f + mRandom.nextFloat() * 2f; // 1~3 dp
                mStarAlpha[i] = 0.3f + mRandom.nextFloat() * 0.5f;
            }
        }
        mStarsInitialized = true;
    }

    /**
     * 更新和绘制星星
     *
     * 每颗星星以正弦波呼吸脉冲透明度，缓慢漂移，超出屏幕边界时环绕重置。
     * 青色调星星带有微弱的青色着色。
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

            if (mStarType[i] == 1) {
                // 青色调大星星，带微弱辉光
                mParticlePaint.setColor(0xFF88DDFF);
                mParticlePaint.setAlpha(alphaInt);
                mParticlePaint.setShadowLayer(dp(3f), 0, 0, 0x4400D4FF);
            } else {
                mParticlePaint.setColor(COLOR_CORE);
                mParticlePaint.setAlpha(alphaInt);
            }
            canvas.drawCircle(mStarX[i], mStarY[i], dp(mStarRadius[i]), mParticlePaint);
            mParticlePaint.clearShadowLayer();
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

    // ==================== 腿和脚 ====================

    /**
     * 绘制双腿和靴子
     *
     * 两条短腿从臀部连接器向下延伸，底部是大块状靴子。
     * 腿部为白色管状 + 暗色膝关节球 + 白色靴子配青色圆形装饰。
     * 新增：膝盖青色点缀、小腿面板、靴底/鞋口线/双圆装饰。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawLegsAndFeet(Canvas canvas, RobotState state, float glow) {
        // 臀部连接器（深色矩形）
        float hipY = dp(BODY_TOP_Y) + dp(BODY_H);
        mTempRect.set(-dp(HIP_W) / 2f, hipY, dp(HIP_W) / 2f, hipY + dp(HIP_H));
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, dp(4f), dp(4f), mDarkFillPaint);

        // 腿起始 Y（臀部底部）
        float legTopY = hipY + dp(HIP_H);

        // 左腿
        drawSingleLeg(canvas, -dp(LEG_X_OFFSET), legTopY, glow);
        // 右腿
        drawSingleLeg(canvas, dp(LEG_X_OFFSET), legTopY, glow);
    }

    /**
     * 绘制单条腿（膝关节球 + 白色管 + 靴子 + 膝盖点缀 + 小腿面板 + 靴子细节）
     *
     * @param canvas 画布
     * @param cx     腿中心 X（px）
     * @param topY   腿顶部 Y（px）
     * @param glow   发光强度
     */
    private void drawSingleLeg(Canvas canvas, float cx, float topY, float glow) {
        float tubeW = dp(LEG_TUBE_W);
        float legLen = dp(LEG_LEN);
        float halfW = tubeW / 2f;

        // 膝关节球（在腿中段）
        float kneeY = topY + legLen * 0.45f;
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawCircle(cx, kneeY, dp(JOINT_R), mDarkFillPaint);

        // 腿管（白色填充 + 深灰描边）
        mTempRect.set(cx - halfW, topY, cx + halfW, topY + legLen);
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(COLOR_BODY_WHITE);
        mFillPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, dp(6f), dp(6f), mFillPaint);

        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_W));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, dp(6f), dp(6f), mStrokePaint);

        // ---- 小腿面板：半透明白色矩形，模拟正面板件 ----
        float shinTop = kneeY + dp(JOINT_R) + dp(2f);
        float shinBot = topY + legLen - dp(4f);
        float shinHalfW = halfW * 0.5f;
        mTempRect3.set(cx - shinHalfW, shinTop, cx + shinHalfW, shinBot);
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(0x33FFFFFF); // 20% 透明白色
        mDetailPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect3, dp(2f), dp(2f), mDetailPaint);

        // 再绘制膝关节球（覆盖在管上方以形成层次）
        mDarkFillPaint.setColor(COLOR_JOINT);
        canvas.drawCircle(cx, kneeY, dp(JOINT_R), mDarkFillPaint);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        canvas.drawCircle(cx, kneeY, dp(JOINT_R), mStrokePaint);

        // ---- 膝盖青色点缀：关节球上的小青色圆点 ----
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_EYE_CYAN);
        mDetailPaint.setShadowLayer(dp(3f), 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(cx, kneeY, dp(2f), mDetailPaint);
        mDetailPaint.clearShadowLayer();

        // ---- 靴子 ----
        float bootY = topY + legLen;
        float bootW = dp(BOOT_W);
        float bootH = dp(BOOT_H);
        mTempRect.set(cx - bootW / 2f, bootY, cx + bootW / 2f, bootY + bootH);

        // 靴子白色填充
        mFillPaint.setColor(COLOR_BODY_WHITE);
        canvas.drawRoundRect(mTempRect, dp(BOOT_R), dp(BOOT_R), mFillPaint);

        // 靴子深灰描边
        mStrokePaint.setStrokeWidth(dp(STROKE_W));
        canvas.drawRoundRect(mTempRect, dp(BOOT_R), dp(BOOT_R), mStrokePaint);

        // ---- 靴底线：靴子最底部的深色细线，模拟鞋底 ----
        float soleY = bootY + bootH - dp(3f);
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(1.5f));
        mDetailPaint.setColor(COLOR_OUTLINE);
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.clearShadowLayer();
        canvas.drawLine(cx - bootW * 0.35f, soleY, cx + bootW * 0.35f, soleY, mDetailPaint);

        // ---- 靴口横线：靴子上部约 30% 处的深灰横线，模拟鞋口 ----
        float trimY = bootY + bootH * 0.3f;
        mDetailPaint.setStrokeWidth(dp(1f));
        mDetailPaint.setColor(0xFF505050);
        canvas.drawLine(cx - bootW * 0.35f, trimY, cx + bootW * 0.35f, trimY, mDetailPaint);

        // 靴子侧面青色圆形装饰（主圆）
        float circleX = cx + bootW * 0.2f;
        float circleY = bootY + bootH * 0.55f;
        mAccentPaint.setStyle(Paint.Style.STROKE);
        mAccentPaint.setStrokeWidth(dp(EAR_ACCENT_LINE_W));
        mAccentPaint.setColor(COLOR_EYE_CYAN);
        mAccentPaint.clearShadowLayer();
        canvas.drawCircle(circleX, circleY, dp(BOOT_CIRCLE_R), mAccentPaint);

        // ---- 第二个靴子圆形装饰：更小，位于主圆上方 ----
        float circle2Y = bootY + bootH * 0.3f;
        canvas.drawCircle(circleX, circle2Y, dp(BOOT_CIRCLE_R * 0.6f), mAccentPaint);
    }

    // ==================== 身体 ====================

    /**
     * 绘制机器人躯干（盾形/心形白色体）
     *
     * 上部宽圆肩，向下收窄成圆弧底部，形成可爱的盾牌/心形造型。
     * 白色填充 + 深灰轮廓描边。
     * 新增：胸部徽章、面板接缝线、肩甲。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawBody(Canvas canvas, RobotState state, float glow) {
        float topY = dp(BODY_TOP_Y);
        float topHW = dp(BODY_TOP_W) / 2f;
        float midHW = dp(BODY_MID_W) / 2f;
        float h = dp(BODY_H);
        float r = dp(BODY_R);
        float botY = topY + h;

        // 构建盾形 Path：顶部圆角矩形 → 中部最宽处 → 底部圆弧收窄
        mBodyPath.reset();
        // 从左上角开始
        mBodyPath.moveTo(-topHW + r, topY);
        mBodyPath.lineTo(topHW - r, topY);
        // 右上圆角
        mBodyPath.quadTo(topHW, topY, topHW, topY + r);
        // 右侧向外扩展到中部最宽处
        float midY = topY + h * 0.35f;
        mBodyPath.lineTo(midHW, midY);
        // 右侧向下收窄到底部
        mBodyPath.quadTo(midHW, botY - dp(12f), dp(18f), botY);
        // 底部圆弧
        mBodyPath.quadTo(0, botY + dp(12f), -dp(18f), botY);
        // 左侧向上
        mBodyPath.quadTo(-midHW, botY - dp(12f), -midHW, midY);
        // 左侧回到顶部
        mBodyPath.lineTo(-topHW, topY + r);
        // 左上圆角
        mBodyPath.quadTo(-topHW, topY, -topHW + r, topY);
        mBodyPath.close();

        // 白色填充
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(COLOR_BODY_WHITE);
        mFillPaint.clearShadowLayer();
        canvas.drawPath(mBodyPath, mFillPaint);

        // 深灰描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_W));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawPath(mBodyPath, mStrokePaint);

        // ---- 身体面板接缝线：两条水平半透明白线，模拟面板拼接 ----
        float seam1Y = topY + h * 0.30f;
        float seam2Y = topY + h * 0.60f;
        // 在这些 Y 高度上，计算身体的大致宽度
        float seam1HW = topHW + (midHW - topHW) * (0.30f / 0.35f);
        float seam2HW = midHW * 0.7f; // 下半部分已收窄
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(0.8f));
        mDetailPaint.setColor(0x66FFFFFF); // 40% 透明白色
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.clearShadowLayer();
        canvas.drawLine(-seam1HW * 0.8f, seam1Y, seam1HW * 0.8f, seam1Y, mDetailPaint);
        canvas.drawLine(-seam2HW * 0.8f, seam2Y, seam2HW * 0.8f, seam2Y, mDetailPaint);

        // ---- 肩甲：身体上方两侧略深色的小三角区域，定义肩膀 ----
        drawShoulderPad(canvas, -midHW, topY, midY, true);
        drawShoulderPad(canvas, midHW, topY, midY, false);

        // ---- 胸部徽章：身体中心的青色圆环（核心/心脏图标） ----
        float emblemCY = topY + h * 0.42f;
        float emblemR = dp(CHEST_EMBLEM_R);
        // 外圈青色发光
        mAccentPaint.setStyle(Paint.Style.STROKE);
        mAccentPaint.setStrokeWidth(dp(2f));
        mAccentPaint.setColor(COLOR_EYE_CYAN);
        mAccentPaint.setShadowLayer(dp(8f) * glow, 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(0, emblemCY, emblemR, mAccentPaint);
        mAccentPaint.clearShadowLayer();
        // 内圈更小的实心青色圆
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(0x4400D4FF); // 半透明青色填充
        mDetailPaint.clearShadowLayer();
        canvas.drawCircle(0, emblemCY, emblemR * 0.55f, mDetailPaint);
        // 中心白色小点
        mDetailPaint.setColor(0xAAFFFFFF);
        canvas.drawCircle(0, emblemCY, dp(2.5f), mDetailPaint);
    }

    /**
     * 绘制肩甲三角区域
     *
     * 在身体上方侧边绘制小的深色三角形区域，营造肩膀的厚度感。
     *
     * @param canvas 画布
     * @param sideX  身体侧边 X 坐标（px）
     * @param topY   身体顶部 Y（px）
     * @param midY   身体中部 Y（px）
     * @param isLeft 是否为左侧肩甲
     */
    private void drawShoulderPad(Canvas canvas, float sideX, float topY,
                                  float midY, boolean isLeft) {
        mTempPath.reset();
        float padW = dp(12f);
        float padH = dp(25f);
        float baseX = isLeft ? sideX : sideX;
        float dirMul = isLeft ? -1f : 1f;

        // 从肩膀连接点向外延伸的小梯形
        mTempPath.moveTo(baseX, topY + dp(10f));
        mTempPath.lineTo(baseX + dirMul * padW * 0.3f, topY + dp(5f));
        mTempPath.lineTo(baseX + dirMul * padW * 0.5f, topY + padH * 0.5f);
        mTempPath.lineTo(baseX, topY + padH);
        mTempPath.close();

        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(0x22000000); // 非常轻微的深色覆盖
        mDetailPaint.clearShadowLayer();
        canvas.drawPath(mTempPath, mDetailPaint);
    }

    // ==================== 手臂 ====================

    /**
     * 绘制双臂（左臂自然下垂 + 右臂举起挥手）
     *
     * 每条手臂由上臂 + 前臂 + 手组成，各段之间用深色关节球连接。
     * 右臂有基于 idleTimer 的挥手摆动动画。
     * 新增：肘部和腕部青色点缀、腕部护腕环。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawArms(Canvas canvas, RobotState state, float glow) {
        float shoulderY = dp(BODY_TOP_Y) + dp(SHOULDER_Y_OFFSET);
        float bodyMidHW = dp(BODY_MID_W) / 2f;

        // 左臂（自然下垂）：肩膀在身体左侧
        drawLeftArm(canvas, -bodyMidHW, shoulderY, state, glow);

        // 右臂（举起挥手）：肩膀在身体右侧
        drawRightArm(canvas, bodyMidHW, shoulderY, state, glow);
    }

    /**
     * 绘制左臂（自然下垂姿态，含肘部点缀和腕部护腕）
     *
     * 上臂向下偏左约 15°，前臂继续向下略向内弯曲。
     *
     * @param canvas    画布
     * @param shoulderX 肩膀关节 X（px）
     * @param shoulderY 肩膀关节 Y（px）
     * @param state     机器人状态
     * @param glow      发光强度
     */
    private void drawLeftArm(Canvas canvas, float shoulderX, float shoulderY,
                             RobotState state, float glow) {
        canvas.save();
        canvas.translate(shoulderX, shoulderY);

        // 上臂方向：向下偏左 15°
        float upperAngle = (float) Math.toRadians(-105f); // -90(下) -15(左偏)
        float upperLen = dp(ARM_UPPER_LEN);
        float elbowX = (float) Math.cos(upperAngle) * upperLen;
        float elbowY = (float) Math.sin(upperAngle) * upperLen;

        // 绘制上臂管
        drawArmTube(canvas, 0, 0, elbowX, elbowY, dp(ARM_TUBE_W));

        // 肘关节球
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawCircle(elbowX, elbowY, dp(JOINT_R), mDarkFillPaint);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawCircle(elbowX, elbowY, dp(JOINT_R), mStrokePaint);

        // ---- 肘部青色点缀 ----
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_EYE_CYAN);
        mDetailPaint.setShadowLayer(dp(3f), 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(elbowX, elbowY, dp(2f), mDetailPaint);
        mDetailPaint.clearShadowLayer();

        // 前臂方向：继续向下偏内 10°
        float forearmAngle = (float) Math.toRadians(-80f);
        float forearmLen = dp(ARM_FOREARM_LEN);
        float wristX = elbowX + (float) Math.cos(forearmAngle) * forearmLen;
        float wristY = elbowY + (float) Math.sin(forearmAngle) * forearmLen;

        // 绘制前臂管
        drawArmTube(canvas, elbowX, elbowY, wristX, wristY, dp(ARM_TUBE_W) * 0.85f);

        // 腕关节球
        canvas.drawCircle(wristX, wristY, dp(JOINT_R) * 0.85f, mDarkFillPaint);
        canvas.drawCircle(wristX, wristY, dp(JOINT_R) * 0.85f, mStrokePaint);

        // ---- 腕部护腕环：比手臂管略宽的深色描边圆环 ----
        drawWristCuff(canvas, wristX, wristY);

        // 绘制手（手指散开）
        drawHand(canvas, wristX, wristY, forearmAngle, false);

        // 肩关节球（最后画以覆盖在管上方）
        mDarkFillPaint.setColor(COLOR_JOINT);
        canvas.drawCircle(0, 0, dp(JOINT_R), mDarkFillPaint);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        canvas.drawCircle(0, 0, dp(JOINT_R), mStrokePaint);

        canvas.restore();
    }

    /**
     * 绘制右臂（举起挥手姿态，带摆动动画，含肘部点缀和腕部护腕）
     *
     * 上臂向上偏右约 45°，前臂向上再弯曲，手在最高点挥动。
     * 利用 state.idleTimer 驱动细微的挥手摆动。
     *
     * @param canvas    画布
     * @param shoulderX 肩膀关节 X（px）
     * @param shoulderY 肩膀关节 Y（px）
     * @param state     机器人状态
     * @param glow      发光强度
     */
    private void drawRightArm(Canvas canvas, float shoulderX, float shoulderY,
                              RobotState state, float glow) {
        canvas.save();
        canvas.translate(shoulderX, shoulderY);

        // 挥手摆动动画：上臂角度随 idleTimer 微幅变化
        float waveOsc = (float) Math.sin(state.idleTimer * 3.0) * 8f; // ±8° 摆动

        // 上臂方向：向上偏右（约 -45° + 摆动）
        float upperAngleDeg = -45f + waveOsc;
        float upperAngle = (float) Math.toRadians(upperAngleDeg);
        float upperLen = dp(ARM_UPPER_LEN);
        float elbowX = (float) Math.cos(upperAngle) * upperLen;
        float elbowY = (float) Math.sin(upperAngle) * upperLen;

        // 绘制上臂管
        drawArmTube(canvas, 0, 0, elbowX, elbowY, dp(ARM_TUBE_W));

        // 肘关节球
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawCircle(elbowX, elbowY, dp(JOINT_R), mDarkFillPaint);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawCircle(elbowX, elbowY, dp(JOINT_R), mStrokePaint);

        // ---- 肘部青色点缀 ----
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_EYE_CYAN);
        mDetailPaint.setShadowLayer(dp(3f), 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(elbowX, elbowY, dp(2f), mDetailPaint);
        mDetailPaint.clearShadowLayer();

        // 前臂方向：从肘部向上（约 -110° + 摆动的一半）
        float forearmAngleDeg = -110f + waveOsc * 0.5f;
        float forearmAngle = (float) Math.toRadians(forearmAngleDeg);
        float forearmLen = dp(ARM_FOREARM_LEN);
        float wristX = elbowX + (float) Math.cos(forearmAngle) * forearmLen;
        float wristY = elbowY + (float) Math.sin(forearmAngle) * forearmLen;

        // 绘制前臂管
        drawArmTube(canvas, elbowX, elbowY, wristX, wristY, dp(ARM_TUBE_W) * 0.85f);

        // 腕关节球
        canvas.drawCircle(wristX, wristY, dp(JOINT_R) * 0.85f, mDarkFillPaint);
        canvas.drawCircle(wristX, wristY, dp(JOINT_R) * 0.85f, mStrokePaint);

        // ---- 腕部护腕环 ----
        drawWristCuff(canvas, wristX, wristY);

        // 绘制手（张开的挥手姿势）
        drawHand(canvas, wristX, wristY, forearmAngle, true);

        // 肩关节球（最后画）
        mDarkFillPaint.setColor(COLOR_JOINT);
        canvas.drawCircle(0, 0, dp(JOINT_R), mDarkFillPaint);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        canvas.drawCircle(0, 0, dp(JOINT_R), mStrokePaint);

        canvas.restore();
    }

    /**
     * 绘制腕部护腕环
     *
     * 在腕部位置绘制一个比手臂管略宽的深色描边圆环，
     * 作为手臂末端装饰细节。
     *
     * @param canvas 画布
     * @param wristX 腕部 X（px）
     * @param wristY 腕部 Y（px）
     */
    private void drawWristCuff(Canvas canvas, float wristX, float wristY) {
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(1.5f));
        mDetailPaint.setColor(COLOR_OUTLINE);
        mDetailPaint.clearShadowLayer();
        canvas.drawCircle(wristX, wristY, dp(JOINT_R) * 1.15f, mDetailPaint);
    }

    /**
     * 绘制手臂管段（白色圆角矩形沿方向旋转）
     *
     * 在起点到终点之间绘制一个旋转的圆角矩形管。
     *
     * @param canvas 画布
     * @param x1     起点 X（px）
     * @param y1     起点 Y（px）
     * @param x2     终点 X（px）
     * @param y2     终点 Y（px）
     * @param width  管宽度（px）
     */
    private void drawArmTube(Canvas canvas, float x1, float y1, float x2, float y2,
                             float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));

        canvas.save();
        canvas.translate(x1, y1);
        canvas.rotate(angleDeg);

        float halfW = width / 2f;
        mTempRect.set(0, -halfW, len, halfW);

        // 白色填充
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(COLOR_BODY_WHITE);
        mFillPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, halfW, halfW, mFillPaint);

        // 深灰描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_W));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, halfW, halfW, mStrokePaint);

        canvas.restore();
    }

    /**
     * 绘制机器人的手（4 根手指从腕部散开）
     *
     * 手指为小圆角矩形，从腕部沿前臂延伸方向扇形展开。
     *
     * @param canvas       画布
     * @param wristX       腕部 X（px，相对于肩膀）
     * @param wristY       腕部 Y（px，相对于肩膀）
     * @param forearmAngle 前臂角度（弧度）
     * @param isWaving     是否为挥手姿势（手指更加张开）
     */
    private void drawHand(Canvas canvas, float wristX, float wristY,
                          float forearmAngle, boolean isWaving) {
        canvas.save();
        canvas.translate(wristX, wristY);

        // 手指展开角度范围
        float spreadAngle = isWaving ? 50f : 35f;
        float baseAngle = (float) Math.toDegrees(forearmAngle);
        float startAngle = baseAngle - spreadAngle / 2f;
        float step = spreadAngle / (FINGER_COUNT - 1);

        float fingerLen = dp(FINGER_LEN);
        float fingerW = dp(FINGER_W);

        for (int i = 0; i < FINGER_COUNT; i++) {
            float angle = (float) Math.toRadians(startAngle + step * i);

            // 手指管段
            canvas.save();
            canvas.rotate((float) Math.toDegrees(angle));

            mTempRect2.set(0, -fingerW / 2f, fingerLen, fingerW / 2f);
            mFillPaint.setStyle(Paint.Style.FILL);
            mFillPaint.setColor(COLOR_BODY_WHITE);
            mFillPaint.clearShadowLayer();
            canvas.drawRoundRect(mTempRect2, fingerW / 2f, fingerW / 2f, mFillPaint);

            mStrokePaint.setStyle(Paint.Style.STROKE);
            mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
            mStrokePaint.setColor(COLOR_OUTLINE);
            mStrokePaint.clearShadowLayer();
            canvas.drawRoundRect(mTempRect2, fingerW / 2f, fingerW / 2f, mStrokePaint);

            canvas.restore();
        }

        canvas.restore();
    }

    // ==================== 脖子 ====================

    /**
     * 绘制脖子（头部和身体之间的圆柱连接器）
     *
     * 深色短圆角矩形，位于头部底部和身体顶部之间。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawNeck(Canvas canvas, RobotState state, float glow) {
        float headBottom = dp(HEAD_CY) + dp(HEAD_H) / 2f;
        float bodyTop = dp(BODY_TOP_Y);
        float neckCY = (headBottom + bodyTop) / 2f;
        float neckHW = dp(NECK_W) / 2f;
        float neckHH = dp(NECK_H) / 2f;

        mTempRect.set(-neckHW, neckCY - neckHH, neckHW, neckCY + neckHH);
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, dp(5f), dp(5f), mDarkFillPaint);

        // 深灰描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, dp(5f), dp(5f), mStrokePaint);
    }

    // ==================== 头部 ====================

    /**
     * 绘制机器人头部（大的白色圆角矩形/椭圆形）
     *
     * 头部是机器人最大的部件，约占总高度 45%。
     * 使用高圆角使其接近圆形/椭圆形的可爱造型。
     * 新增：头部左上方高光弧，增添立体光泽感。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawHead(Canvas canvas, RobotState state, float glow) {
        float hw = dp(HEAD_W) / 2f;
        float hh = dp(HEAD_H) / 2f;
        float cy = dp(HEAD_CY);
        float r = dp(HEAD_R);

        mTempRect.set(-hw, cy - hh, hw, cy + hh);

        // 白色填充
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(COLOR_BODY_WHITE);
        mFillPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, r, r, mFillPaint);

        // 深灰描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_W));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, r, r, mStrokePaint);

        // ---- 头部高光弧：左上方白色半透明弧线，模拟光照反射 ----
        drawHeadHighlight(canvas, hw, hh, cy, r);
    }

    /**
     * 绘制头部左上方的高光弧
     *
     * 在头部左上区域绘制一条半透明白色弧线，模拟环境光在光滑表面上的反射，
     * 增强头部的立体质感。
     *
     * @param canvas 画布
     * @param hw     头部半宽（px）
     * @param hh     头部半高（px）
     * @param cy     头部中心 Y（px）
     * @param r      头部圆角（px）
     */
    private void drawHeadHighlight(Canvas canvas, float hw, float hh, float cy, float r) {
        // 在头部左上区域画一段弧线，模拟高光反射
        float arcCX = -hw * 0.35f;
        float arcCY = cy - hh * 0.35f;
        float arcR = r * 0.9f;

        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(2.5f));
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.setColor(0x55FFFFFF); // 33% 透明白色
        mDetailPaint.setShadowLayer(dp(2f), 0, 0, 0x33FFFFFF);

        mTempRect3.set(arcCX - arcR, arcCY - arcR, arcCX + arcR, arcCY + arcR);
        canvas.drawArc(mTempRect3, 200f, 70f, false, mDetailPaint);
        mDetailPaint.clearShadowLayer();
    }

    // ==================== 天线 ====================

    /**
     * 绘制头部顶端的天线
     *
     * 从头部正中顶部向上延伸的细深色线杆，顶端有一个带青色辉光的小圆球。
     * 天线增加了机器人的科技感和辨识度。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawAntenna(Canvas canvas, RobotState state, float glow) {
        float headTopY = dp(HEAD_CY) - dp(HEAD_H) / 2f;
        float antennaBaseY = headTopY;
        float antennaTipY = headTopY - dp(ANTENNA_HEIGHT);
        float ballR = dp(ANTENNA_BALL_R);

        // 天线杆（深灰色细线）
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(2f));
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.setColor(COLOR_JOINT);
        mDetailPaint.clearShadowLayer();
        canvas.drawLine(0, antennaBaseY, 0, antennaTipY, mDetailPaint);

        // 天线顶部青色发光球
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_EYE_CYAN);
        mDetailPaint.setShadowLayer(dp(6f) * glow, 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(0, antennaTipY, ballR, mDetailPaint);
        mDetailPaint.clearShadowLayer();

        // 球体高光（白色小点）
        mDetailPaint.setColor(0xAAFFFFFF);
        canvas.drawCircle(-ballR * 0.25f, antennaTipY - ballR * 0.25f,
                ballR * 0.3f, mDetailPaint);
    }

    // ==================== 侧耳（耳机垫块） ====================

    /**
     * 绘制头部两侧的矩形耳块（类似耳机垫）
     *
     * 深灰色圆角矩形，附着在头部左右两侧，带一条细的青色点缀线。
     * 受 state.leftEarAngle/rightEarAngle 影响产生轻微倾斜。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawEarBlocks(Canvas canvas, RobotState state, float glow) {
        float headHW = dp(HEAD_W) / 2f;
        float headCY = dp(HEAD_CY);
        float blockW = dp(EAR_BLOCK_W);
        float blockH = dp(EAR_BLOCK_H);
        float blockR = dp(EAR_BLOCK_R);
        float offsetX = dp(EAR_BLOCK_OFFSET_X);

        // 左耳块
        canvas.save();
        float leftBlockCX = -headHW - offsetX - blockW / 2f;
        canvas.rotate(state.leftEarAngle * 0.3f, leftBlockCX, headCY);
        mTempRect.set(leftBlockCX - blockW / 2f, headCY - blockH / 2f,
                leftBlockCX + blockW / 2f, headCY + blockH / 2f);

        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, blockR, blockR, mDarkFillPaint);

        // 深灰描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, blockR, blockR, mStrokePaint);

        // 青色点缀线（垂直线在耳块中央）
        mAccentPaint.setStyle(Paint.Style.STROKE);
        mAccentPaint.setStrokeWidth(dp(EAR_ACCENT_LINE_W));
        mAccentPaint.setColor(COLOR_EYE_CYAN);
        mAccentPaint.setStrokeCap(Paint.Cap.ROUND);
        mAccentPaint.clearShadowLayer();
        float lineX = leftBlockCX;
        canvas.drawLine(lineX, headCY - blockH * 0.3f, lineX, headCY + blockH * 0.3f,
                mAccentPaint);

        canvas.restore();

        // 右耳块（镜像）
        canvas.save();
        float rightBlockCX = headHW + offsetX + blockW / 2f;
        canvas.rotate(-state.rightEarAngle * 0.3f, rightBlockCX, headCY);
        mTempRect.set(rightBlockCX - blockW / 2f, headCY - blockH / 2f,
                rightBlockCX + blockW / 2f, headCY + blockH / 2f);

        canvas.drawRoundRect(mTempRect, blockR, blockR, mDarkFillPaint);
        canvas.drawRoundRect(mTempRect, blockR, blockR, mStrokePaint);

        // 青色点缀线
        lineX = rightBlockCX;
        canvas.drawLine(lineX, headCY - blockH * 0.3f, lineX, headCY + blockH * 0.3f,
                mAccentPaint);

        canvas.restore();
    }

    // ==================== 面部面板 ====================

    /**
     * 绘制面部深色面板（类似面罩/显示屏区域）
     *
     * 横跨头部中间的深色圆角矩形，作为眼睛的背景，
     * 营造出机器人显示屏的视觉效果。
     * 新增：内侧高光边框，增加面板深度感。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawFacePanel(Canvas canvas, RobotState state, float glow) {
        float panelW = dp(PANEL_W);
        float panelH = dp(PANEL_H);
        float panelR = dp(PANEL_R);
        float panelCY = dp(HEAD_CY) + dp(PANEL_OFFSET_Y);

        mTempRect.set(-panelW / 2f, panelCY - panelH / 2f,
                panelW / 2f, panelCY + panelH / 2f);

        // 深色面板填充
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_FACE_PANEL);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, panelR, panelR, mDarkFillPaint);

        // 轻微的描边（比面板稍亮）
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        mStrokePaint.setColor(setAlpha(COLOR_OUTLINE, 0x88));
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, panelR, panelR, mStrokePaint);

        // ---- 面板内高亮边框：1dp 内缩，更亮的灰色细线，营造凹陷深度感 ----
        float inset = dp(1f);
        mTempRect3.set(-panelW / 2f + inset, panelCY - panelH / 2f + inset,
                panelW / 2f - inset, panelCY + panelH / 2f - inset);
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(1f));
        mDetailPaint.setColor(COLOR_PANEL_HIGHLIGHT);
        mDetailPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect3, panelR - inset, panelR - inset, mDetailPaint);
    }

    // ==================== 眼睛 ====================

    /**
     * 绘制双眼（青色发光环 + 黑色瞳孔，支持追踪和表情变化）
     *
     * 每只眼睛由三层构成：
     * 1. 外部青色发光环（带 shadowLayer 辉光效果）
     * 2. 中间暗色圆（眼球底色）
     * 3. 内部黑色瞳孔（根据 eyePupilOffsetX/Y 偏移追踪）
     *
     * 表情影响：
     * - IDLE：正常大小，标准亮度
     * - EXCITED：发光更强，轻微放大
     * - SURPRISED：眼睛圆睁放大
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawEyes(Canvas canvas, RobotState state, float glow) {
        float panelCY = dp(HEAD_CY) + dp(PANEL_OFFSET_Y) + dp(EYE_CY_OFFSET);
        float openness = state.eyeOpenness;

        // 表情影响眼睛尺寸和发光强度
        float eyeScaleMul = 1.0f;
        float glowMul = 1.0f;
        switch (state.expression) {
            case EXCITED:
                glowMul = 1.4f;
                eyeScaleMul = 1.05f;
                break;
            case SURPRISED:
                eyeScaleMul = 1.3f;
                glowMul = 1.2f;
                break;
            default:
                break;
        }

        // 左眼
        drawSingleEye(canvas, dp(EYE_L_CX), panelCY,
                dp(EYE_OUTER_R) * eyeScaleMul,
                dp(EYE_PUPIL_R) * eyeScaleMul,
                dp(state.eyePupilOffsetX), dp(state.eyePupilOffsetY),
                openness, glow * glowMul);

        // 右眼
        drawSingleEye(canvas, dp(EYE_R_CX), panelCY,
                dp(EYE_OUTER_R) * eyeScaleMul,
                dp(EYE_PUPIL_R) * eyeScaleMul,
                dp(state.eyePupilOffsetX), dp(state.eyePupilOffsetY),
                openness, glow * glowMul);
    }

    /**
     * 绘制单个眼睛（发光环 + 瞳孔）
     *
     * @param canvas    画布
     * @param cx        眼睛中心 X（px）
     * @param cy        眼睛中心 Y（px）
     * @param outerR    外环半径（px）
     * @param pupilR    瞳孔半径（px）
     * @param pupilOffX 瞳孔水平偏移（px）
     * @param pupilOffY 瞳孔垂直偏移（px）
     * @param openness  眼睛睁开度（0=闭合, 1=正常）
     * @param glow      发光强度
     */
    private void drawSingleEye(Canvas canvas, float cx, float cy,
                               float outerR, float pupilR,
                               float pupilOffX, float pupilOffY,
                               float openness, float glow) {
        // 眼睛几乎闭合时只画一条短横线
        if (openness < 0.1f) {
            mEyeGlowPaint.setStyle(Paint.Style.STROKE);
            mEyeGlowPaint.setStrokeWidth(dp(2.5f));
            mEyeGlowPaint.setStrokeCap(Paint.Cap.ROUND);
            mEyeGlowPaint.setColor(COLOR_EYE_CYAN);
            mEyeGlowPaint.setShadowLayer(dp(8f) * glow, 0, 0, COLOR_EYE_CYAN);
            canvas.drawLine(cx - outerR * 0.6f, cy, cx + outerR * 0.6f, cy, mEyeGlowPaint);
            return;
        }

        // 利用 openness 缩放垂直方向，实现眨眼效果
        canvas.save();
        canvas.scale(1f, openness, cx, cy);

        // 第 1 层：青色发光环（外圆，带 shadowLayer 辉光）
        setupGlowPaint(mEyeGlowPaint, COLOR_EYE_CYAN, dp(EYE_GLOW_RING_W),
                dp(12f) * glow);
        canvas.drawCircle(cx, cy, outerR, mEyeGlowPaint);

        // 第 2 层：暗色眼球底色（填充内部）
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_FACE_PANEL);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawCircle(cx, cy, outerR - dp(EYE_GLOW_RING_W) / 2f, mDarkFillPaint);

        // 第 3 层：内圈青色发光（比外环细，更紧密的光环）
        setupCorePaint(mEyeCorePaint, COLOR_EYE_CYAN, dp(1.5f), dp(6f) * glow);
        canvas.drawCircle(cx, cy, outerR - dp(EYE_GLOW_RING_W), mEyeCorePaint);

        // 第 4 层：黑色瞳孔（偏移追踪）
        float pcx = cx + pupilOffX;
        float pcy = cy + pupilOffY;
        mEyeCorePaint.setStyle(Paint.Style.FILL);
        mEyeCorePaint.setColor(COLOR_PUPIL);
        mEyeCorePaint.clearShadowLayer();
        canvas.drawCircle(pcx, pcy, pupilR, mEyeCorePaint);

        // 第 5 层：瞳孔高光点（小白点，增添灵动感）
        float hlR = pupilR * 0.3f;
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(COLOR_CORE);
        mFillPaint.clearShadowLayer();
        canvas.drawCircle(pcx - pupilR * 0.3f, pcy - pupilR * 0.3f, hlR, mFillPaint);

        canvas.restore();
    }

    // ==================== 额头装饰 ====================

    /**
     * 绘制额头上的三个小圆点装饰（三角形排列）
     *
     * 类似传感器/指示灯，位于头部上方区域，增加机器人的科技感细节。
     * 从原来的两个点改为三角形排列的三个点（上方中间一个 + 下方左右各一个）。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawForeheadDots(Canvas canvas, RobotState state, float glow) {
        float headTopY = dp(HEAD_CY) - dp(HEAD_H) / 2f;
        float dotY = headTopY + dp(FOREHEAD_DOT_Y);
        float dotR = dp(FOREHEAD_DOT_R);
        float spacing = dp(FOREHEAD_DOT_SPACING);

        // 深灰色小圆点
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();

        // 下方两个点（左右对称）
        canvas.drawCircle(-spacing / 2f, dotY, dotR, mDarkFillPaint);
        canvas.drawCircle(spacing / 2f, dotY, dotR, mDarkFillPaint);

        // 上方中间一个点（组成三角形图案）
        float topDotY = dotY - dp(8f);
        canvas.drawCircle(0, topDotY, dotR, mDarkFillPaint);

        // 轮廓描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(1f));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawCircle(-spacing / 2f, dotY, dotR, mStrokePaint);
        canvas.drawCircle(spacing / 2f, dotY, dotR, mStrokePaint);
        canvas.drawCircle(0, topDotY, dotR, mStrokePaint);
    }

    // ==================== 面板指示灯 ====================

    /**
     * 绘制面部面板上的两个微型指示灯
     *
     * 在面部面板下方（眼睛下方）绘制两个小发光圆点，
     * 左侧绿色、右侧琥珀色，模拟状态指示灯，增加科技感。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawIndicatorLights(Canvas canvas, RobotState state, float glow) {
        float panelCY = dp(HEAD_CY) + dp(PANEL_OFFSET_Y);
        float panelHalfH = dp(PANEL_H) / 2f;
        // 指示灯位于面板下方 65% 处
        float indicatorY = panelCY + panelHalfH * 0.65f;
        float indicatorR = dp(2f);
        float indicatorSpacing = dp(15f);

        // 左侧绿色指示灯
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_INDICATOR_GREEN);
        mDetailPaint.setShadowLayer(dp(4f) * glow, 0, 0, COLOR_INDICATOR_GREEN);
        canvas.drawCircle(-indicatorSpacing, indicatorY, indicatorR, mDetailPaint);

        // 右侧琥珀色指示灯
        mDetailPaint.setColor(COLOR_INDICATOR_AMBER);
        mDetailPaint.setShadowLayer(dp(4f) * glow, 0, 0, COLOR_INDICATOR_AMBER);
        canvas.drawCircle(indicatorSpacing, indicatorY, indicatorR, mDetailPaint);
        mDetailPaint.clearShadowLayer();
    }

    // ==================== 工具方法 ====================

    /**
     * 配置发光层 Paint（描边模式 + 指定宽度 + shadowLayer 辉光）
     *
     * 用于眼睛青色发光环等需要辉光效果的元素。
     *
     * @param paint       目标 Paint
     * @param glowColor   发光色
     * @param strokeWidth 描边宽度（px）
     * @param shadowRadius Shadow 半径（px）
     */
    private void setupGlowPaint(Paint paint, int glowColor, float strokeWidth,
                                float shadowRadius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(glowColor);
        paint.setShadowLayer(shadowRadius, 0, 0, glowColor);
    }

    /**
     * 配置核心层 Paint（描边模式 + 正常宽度 + 小 shadowLayer）
     *
     * 用于眼睛内环等需要柔和发光的元素。
     *
     * @param paint       目标 Paint
     * @param shadowColor Shadow 颜色
     * @param strokeWidth 描边宽度（px）
     * @param shadowRadius Shadow 半径（px）
     */
    private void setupCorePaint(Paint paint, int shadowColor, float strokeWidth,
                                float shadowRadius) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(shadowColor);
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

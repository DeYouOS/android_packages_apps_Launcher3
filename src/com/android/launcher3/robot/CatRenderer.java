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
 * 扁平风格 AI 机器人 Canvas 2D 渲染器（增强版 v2）
 *
 * 使用纯 Canvas Path/drawArc/drawCircle/drawRoundRect 绘制可爱的白色卡通机器人。
 * 所有形状采用「白色填充 + 深灰描边」的扁平设计风格，眼睛带有青色发光效果。
 *
 * 相比 v1 版本新增/增强的功能：
 * - 9 种手臂姿态（ArmPose）：自然下垂、挥手、双手上举、左/右指、抓握、思考、鼓掌、擦汗
 * - 4 种眼睛特效（EyeSpecial）：闪烁星光、晕眩螺旋、爱心瞳孔、瞌睡半闭
 * - 7 种嘴巴形状（MouthShape）：中性、微笑、大笑、O 型、D 型、扁嘴、波浪
 * - 6 种眉毛状态（EyebrowState）：中性、上扬、皱眉、单挑、悲伤、生气
 * - 10 种思维气泡（ThoughtBubbleType）：省略号、问号、感叹号、爱心、音符、ZZZ 等
 * - 5 种指示灯状态（IndicatorState）：正常、呼吸、警告、错误、AI 活跃
 * - 3 种胸部模式（ChestMode）：正常、充电、二维码
 * - 天线闪烁增强：支持 antennaFlashing + antennaGlowPhase
 * - 身体动作变换：BodyAction 驱动的平移/旋转/缩放
 * - 脸颊红晕效果：基于 AIEmotion 的害羞腮红
 *
 * 视觉风格：
 * - 深空星云渐变背景（深紫 #0A0618 → 深蓝 #0C1428）
 * - 白色圆头机器人（#F0F0F0 填充 + #3A3A3A 描边）
 * - 青色发光圆眼（#00D4FF 外环 + #1A1A1A 黑瞳）
 * - 深色面板/关节（#2D2D2D / #4A4A4A）
 * - 60~80 颗星星粒子 + 流星效果 + 环境辉光
 *
 * 所有尺寸使用 dp 定义，运行时根据 Canvas 密度转 px。
 * 绘制坐标以机器人 bodyX/bodyY 为原点，各部件相对定位。
 *
 * 绘制顺序（后→前）：
 * 背景星空 → 环境辉光 → 腿/脚 → 身体 → 手臂 → 脖子 → 头部 → 天线 → 侧耳
 * → 面板 → 眼睛 → 眉毛 → 嘴巴 → 额头点 → 指示灯 → 思维气泡 → 脸颊红晕
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

    // ---- 身体（机甲躯干：胸甲→腰部收窄→臀甲三段式） ----
    /** 胸甲顶部宽度（dp） */
    private static final float BODY_TOP_W = 145f;
    /** 胸甲最大宽度（dp，肩膀处） */
    private static final float BODY_MID_W = 157f;
    /** 腰部收窄宽度（dp）— 比胸甲和臀甲都窄，形成机甲腰线 */
    private static final float BODY_WAIST_W = 110f;
    /** 臀甲/下腹宽度（dp）— 略宽于腰部，形成装甲裙护甲 */
    private static final float BODY_HIP_W = 135f;
    /** 身体总高度（dp） */
    private static final float BODY_H = 200f;
    /** 身体顶部 Y 偏移（相对于原点，脖子下方） */
    private static final float BODY_TOP_Y = -70f;
    /** 身体圆角（dp） */
    private static final float BODY_R = 20f;

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

    // ---- 脚/机甲战靴 ----
    /** 战靴宽度（dp） */
    private static final float BOOT_W = 50f;
    /** 战靴高度（dp）— 加厚使战靴更有存在感 */
    private static final float BOOT_H = 44f;
    /** 战靴圆角（dp）— 小圆角保持棱角机甲感 */
    private static final float BOOT_R = 8f;
    /** 靴子侧面圆形点缀半径（dp） */
    private static final float BOOT_CIRCLE_R = 6f;
    /** 靴底厚度（dp）— 明显的厚底层 */
    private static final float BOOT_SOLE_H = 8f;

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

    // ==================== 主绘制入口 ====================

    /**
     * 主绘制入口
     *
     * 按照从后到前的层次绘制所有部件，实现完整的 AI 机器人视觉效果。
     * 每帧由 RobotRenderThread 调用。
     *
     * 绘制顺序：
     * 背景 → 环境辉光 → BodyAction 变换 → 腿/脚 → 身体(ChestMode) → 手臂(ArmPose)
     * → 脖子 → 头部 → 天线(闪烁) → 侧耳 → 面板 → 眼睛(EyeSpecial) → 眉毛(EyebrowState)
     * → 嘴巴(MouthShape) → 额头点 → 指示灯(IndicatorState) → 思维气泡 → 脸颊红晕
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

        // 1. 绘制深空星云渐变背景
        drawBackground(canvas, sw, sh, state);

        // 2. 保存画布状态并平移到机器人身体中心
        canvas.save();
        canvas.translate(state.bodyX, state.bodyY);

        // 3. 呼吸浮动偏移（利用 idleTimer 产生缓慢上下浮动效果）
        float bobOffset = (float) Math.sin(state.idleTimer * 1.2) * dp(5f);
        canvas.translate(0, bobOffset);

        // 4. BodyAction 变换：在呼吸浮动之后、旋转之前应用身体动作
        float actionProg = state.bodyActionProgress;
        float actionInt = state.bodyActionIntensity;
        if (state.bodyAction != RobotState.BodyAction.NONE && actionInt > 0.01f) {
            switch (state.bodyAction) {
                case BOUNCE:
                    // 弹跳：上下正弦位移，频率 4Hz
                    float bounceY = (float) Math.abs(Math.sin(actionProg * Math.PI * 2)) * dp(15f) * actionInt;
                    canvas.translate(0, -bounceY);
                    break;
                case SHIVER:
                    // 颤抖：高频随机水平微位移
                    float shiverX = (float) Math.sin(actionProg * Math.PI * 20) * dp(2f) * actionInt;
                    canvas.translate(shiverX, 0);
                    break;
                case TILT:
                    // 侧倾：身体向一侧倾斜 ~10°
                    float tiltAngle = (float) Math.sin(actionProg * Math.PI) * 10f * actionInt;
                    canvas.rotate(tiltAngle);
                    break;
                case NOD:
                    // 点头：小幅前后倾斜（用纵向位移模拟）
                    float nodY = (float) Math.sin(actionProg * Math.PI * 2) * dp(6f) * actionInt;
                    canvas.translate(0, nodY);
                    break;
                case SHAKE_HEAD:
                    // 摇头：小幅左右旋转
                    float shakeAngle = (float) Math.sin(actionProg * Math.PI * 4) * 8f * actionInt;
                    canvas.rotate(shakeAngle);
                    break;
                case YAWN:
                    // 打哈欠：身体向后仰（微小旋转）
                    float yawnAngle = (float) Math.sin(actionProg * Math.PI) * -5f * actionInt;
                    canvas.rotate(yawnAngle);
                    break;
                case STRETCH:
                    // 伸展：身体纵向拉伸
                    float stretchScale = 1f + (float) Math.sin(actionProg * Math.PI) * 0.05f * actionInt;
                    canvas.scale(1f, stretchScale);
                    break;
                default:
                    break;
            }
        }

        // 5. 全局旋转
        canvas.rotate(state.rotation);

        // 6. 呼吸缩放（bodyScale 在 0.98~1.02 范围，保持机器人自然呼吸感）
        float scale = state.bodyScale;
        if (scale > 0.01f) {
            canvas.scale(scale, scale);
        }

        // 7. 表情影响：EXCITED 时轻微前倾
        if (state.expression == RobotState.Expression.EXCITED) {
            canvas.rotate(2f * state.expressionBlend);
        }

        float glow = state.glowIntensity;

        // 8~22. 按后→前顺序绘制各部件（手臂在头部之后绘制，避免举手/托腮被头遮挡）
        drawLegsAndFeet(canvas, state, glow);           // 8
        drawBody(canvas, state, glow);                   // 9
        drawNeck(canvas, state, glow);                   // 10
        drawHead(canvas, state, glow);                   // 11
        drawAntenna(canvas, state, glow);                // 12
        drawEarBlocks(canvas, state, glow);              // 13
        drawFacePanel(canvas, state, glow);              // 14
        drawEyes(canvas, state, glow);                   // 15
        drawEyebrows(canvas, state, glow);               // 16
        drawMouth(canvas, state, glow);                  // 17
        drawForeheadDots(canvas, state, glow);           // 18
        drawIndicatorLights(canvas, state, glow);        // 19
        drawThoughtBubble(canvas, state, glow);          // 20
        drawCheekBlush(canvas, state, glow);             // 21
        drawArms(canvas, state, glow, false);            // 22（在头部/脸部之上，手臂不被遮挡）

        // 23. 恢复画布状态
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
            mStarDriftX[i] = (mRandom.nextFloat() - 0.5f) * dp(3f);
            mStarDriftY[i] = (mRandom.nextFloat() - 0.5f) * dp(2f);

            // 10% 概率为大号青色调星星
            if (mRandom.nextFloat() < 0.1f) {
                mStarType[i] = 1;
                mStarRadius[i] = 2.5f + mRandom.nextFloat() * 1.5f;
                mStarAlpha[i] = 0.4f + mRandom.nextFloat() * 0.4f;
            } else {
                mStarType[i] = 0;
                mStarRadius[i] = 1f + mRandom.nextFloat() * 2f;
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
                    mShootX[i] = mRandom.nextFloat() * sw;
                    mShootY[i] = mRandom.nextFloat() * sh * 0.3f;
                    float angle = 0.5f + mRandom.nextFloat() * 0.7f;
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

        // 小腿面板：半透明白色矩形，模拟正面板件
        float shinTop = kneeY + dp(JOINT_R) + dp(2f);
        float shinBot = topY + legLen - dp(4f);
        float shinHalfW = halfW * 0.5f;
        mTempRect3.set(cx - shinHalfW, shinTop, cx + shinHalfW, shinBot);
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(0x33FFFFFF);
        mDetailPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect3, dp(2f), dp(2f), mDetailPaint);

        // 再绘制膝关节球（覆盖在管上方以形成层次）
        mDarkFillPaint.setColor(COLOR_JOINT);
        canvas.drawCircle(cx, kneeY, dp(JOINT_R), mDarkFillPaint);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        canvas.drawCircle(cx, kneeY, dp(JOINT_R), mStrokePaint);

        // 膝盖青色点缀：关节球上的小青色圆点
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_EYE_CYAN);
        mDetailPaint.setShadowLayer(dp(3f), 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(cx, kneeY, dp(2f), mDetailPaint);
        mDetailPaint.clearShadowLayer();

        // 机甲战靴：方正厚底 + 护甲板 + 鞋底分层
        float bootY = topY + legLen;
        float bootW = dp(BOOT_W);
        float bootH = dp(BOOT_H);
        float bootR = dp(BOOT_R);
        float soleH = dp(BOOT_SOLE_H);
        float bootCX = cx;

        // 靴子主体（上部，不含鞋底）
        float upperH = bootH - soleH;
        mTempRect.set(bootCX - bootW / 2f, bootY,
                bootCX + bootW / 2f, bootY + upperH);
        mFillPaint.setColor(COLOR_BODY_WHITE);
        canvas.drawRoundRect(mTempRect, bootR, bootR, mFillPaint);
        mStrokePaint.setStrokeWidth(dp(STROKE_W));
        canvas.drawRoundRect(mTempRect, bootR, bootR, mStrokePaint);

        // 厚鞋底层（深灰色，比靴身略宽，突出厚底感）
        float soleW = bootW * 1.1f;
        float soleR = dp(5f);
        mTempRect.set(bootCX - soleW / 2f, bootY + upperH - dp(2f),
                bootCX + soleW / 2f, bootY + bootH);
        mDarkFillPaint.setColor(COLOR_JOINT);
        canvas.drawRoundRect(mTempRect, soleR, soleR, mDarkFillPaint);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        canvas.drawRoundRect(mTempRect, soleR, soleR, mStrokePaint);

        // 靴口护甲带（深色横条，分隔腿管和靴身）
        float trimY = bootY + dp(3f);
        float trimH = dp(5f);
        mTempRect3.set(bootCX - bootW * 0.42f, trimY,
                bootCX + bootW * 0.42f, trimY + trimH);
        mDarkFillPaint.setColor(0xFF505050);
        canvas.drawRoundRect(mTempRect3, dp(2f), dp(2f), mDarkFillPaint);

        // 正面护甲板（半透明浅色矩形，模拟胫甲）
        float plateTop = trimY + trimH + dp(3f);
        float plateBot = bootY + upperH - dp(5f);
        float plateHW = bootW * 0.22f;
        mTempRect3.set(bootCX - plateHW, plateTop, bootCX + plateHW, plateBot);
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(0x18000000);
        mDetailPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect3, dp(3f), dp(3f), mDetailPaint);
        // 护甲板描边
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(0.8f));
        mDetailPaint.setColor(0x30000000);
        canvas.drawRoundRect(mTempRect3, dp(3f), dp(3f), mDetailPaint);

        // 青色指示灯（靴面中央小圆点）
        float indicatorY = (plateTop + plateBot) / 2f;
        mAccentPaint.setStyle(Paint.Style.FILL);
        mAccentPaint.setColor(COLOR_EYE_CYAN);
        mAccentPaint.setShadowLayer(dp(4f), 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(bootCX, indicatorY, dp(2.5f), mAccentPaint);
        mAccentPaint.clearShadowLayer();

        // 鞋底纹路（2 条短横线模拟防滑底纹）
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(1f));
        mDetailPaint.setColor(0x44000000);
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        float grooveY1 = bootY + upperH + soleH * 0.35f;
        float grooveY2 = bootY + upperH + soleH * 0.65f;
        float grooveHW = soleW * 0.3f;
        canvas.drawLine(bootCX - grooveHW, grooveY1, bootCX + grooveHW, grooveY1, mDetailPaint);
        canvas.drawLine(bootCX - grooveHW, grooveY2, bootCX + grooveHW, grooveY2, mDetailPaint);
    }

    // ==================== 身体 ====================

    /**
     * 绘制机器人躯干（三段式机甲造型）+ 胸部徽章（支持 3 种 ChestMode）
     *
     * 三段结构：胸甲（宽肩展开）→ 腰部（收窄形成腰线）→ 臀甲（展宽，底部平直）。
     * 白色填充 + 深灰轮廓描边 + 装甲分割线/铆钉/通风口/腰带细节。
     *
     * 胸部徽章根据 state.chestMode 显示不同内容：
     * - NORMAL：青色圆环（默认核心/心脏图标）
     * - CHARGING：绿色脉冲圆环 + 进度圆弧（基于 chestTaskProgress）
     * - QR_CODE：简化的网格图案
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawBody(Canvas canvas, RobotState state, float glow) {
        float topY = dp(BODY_TOP_Y);
        float topHW = dp(BODY_TOP_W) / 2f;
        float midHW = dp(BODY_MID_W) / 2f;
        float waistHW = dp(BODY_WAIST_W) / 2f;
        float hipHW = dp(BODY_HIP_W) / 2f;
        float h = dp(BODY_H);
        float r = dp(BODY_R);
        float botY = topY + h;

        // 三段式机甲躯干 Path：
        // 1. 胸甲区（0%~35%）：从顶部宽肩展开到最大宽度
        // 2. 腰部区（35%~55%）：急速收窄形成机甲腰线
        // 3. 臀甲区（55%~100%）：再次展宽，底部平直截断
        float chestY = topY + h * 0.35f;   // 胸甲最宽点
        float waistY = topY + h * 0.55f;   // 腰部最窄点
        float hipY = topY + h * 0.75f;     // 臀甲最宽点
        float botR = dp(12f);              // 底部圆角（小圆角 = 棱角感）

        mBodyPath.reset();
        // 顶部左上角开始，顺时针
        mBodyPath.moveTo(-topHW + r, topY);
        mBodyPath.lineTo(topHW - r, topY);
        mBodyPath.quadTo(topHW, topY, topHW, topY + r);
        // 右侧：胸甲展宽
        mBodyPath.lineTo(midHW, chestY);
        // 右侧：腰部内收
        mBodyPath.lineTo(waistHW, waistY);
        // 右侧：臀甲外展
        mBodyPath.lineTo(hipHW, hipY);
        // 右下角圆角
        mBodyPath.lineTo(hipHW, botY - botR);
        mBodyPath.quadTo(hipHW, botY, hipHW - botR, botY);
        // 底部平直线
        mBodyPath.lineTo(-hipHW + botR, botY);
        // 左下角圆角
        mBodyPath.quadTo(-hipHW, botY, -hipHW, botY - botR);
        // 左侧：臀甲
        mBodyPath.lineTo(-hipHW, hipY);
        // 左侧：腰部内收
        mBodyPath.lineTo(-waistHW, waistY);
        // 左侧：胸甲展宽
        mBodyPath.lineTo(-midHW, chestY);
        // 左侧：回到顶部
        mBodyPath.lineTo(-topHW, topY + r);
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

        // 装甲分割线：在腰线和臀甲交界处各画一条，强调三段式结构
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(1.2f));
        mDetailPaint.setColor(0x55000000);
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.clearShadowLayer();
        // 腰线分割（在腰部最窄处）
        canvas.drawLine(-waistHW * 0.85f, waistY, waistHW * 0.85f, waistY, mDetailPaint);
        // 臀甲上沿分割（在臀甲展宽起点）
        canvas.drawLine(-hipHW * 0.75f, hipY, hipHW * 0.75f, hipY, mDetailPaint);
        // 分割线高光（腰线上方 + 臀甲上方各 1px 白线）
        mDetailPaint.setStrokeWidth(dp(0.5f));
        mDetailPaint.setColor(0x22FFFFFF);
        canvas.drawLine(-waistHW * 0.85f, waistY - dp(1f),
                waistHW * 0.85f, waistY - dp(1f), mDetailPaint);
        canvas.drawLine(-hipHW * 0.75f, hipY - dp(1f),
                hipHW * 0.75f, hipY - dp(1f), mDetailPaint);

        // 胸甲区域两侧通风口格栅（胸甲最宽处附近，4 条短线）
        float ventW = dp(10f);
        float ventGap = dp(3.5f);
        float ventStartY = chestY + dp(5f);
        mDetailPaint.setStrokeWidth(dp(0.8f));
        mDetailPaint.setColor(0x33000000);
        for (int i = 0; i < 4; i++) {
            float vy = ventStartY + i * ventGap;
            float seamWidthAtVent = midHW - (midHW - waistHW) * ((vy - chestY) / (waistY - chestY));
            canvas.drawLine(-seamWidthAtVent + dp(3f), vy,
                    -seamWidthAtVent + dp(3f) + ventW, vy, mDetailPaint);
            canvas.drawLine(seamWidthAtVent - dp(3f) - ventW, vy,
                    seamWidthAtVent - dp(3f), vy, mDetailPaint);
        }

        // 臀甲区域铆钉：左右各 2 个
        float rivetR = dp(2f);
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_JOINT);
        // 上排铆钉（臀甲展宽点附近）
        float rY1 = hipY + dp(6f);
        float rX1 = hipHW * 0.55f;
        canvas.drawCircle(-rX1, rY1, rivetR, mDetailPaint);
        canvas.drawCircle(rX1, rY1, rivetR, mDetailPaint);
        // 下排铆钉（底部附近）
        float rY2 = botY - dp(10f);
        canvas.drawCircle(-rX1, rY2, rivetR, mDetailPaint);
        canvas.drawCircle(rX1, rY2, rivetR, mDetailPaint);
        // 铆钉高光
        mDetailPaint.setColor(0xAAFFFFFF);
        float highlightR = dp(0.8f);
        canvas.drawCircle(-rX1 - dp(0.3f), rY1 - dp(0.3f), highlightR, mDetailPaint);
        canvas.drawCircle(rX1 - dp(0.3f), rY1 - dp(0.3f), highlightR, mDetailPaint);
        canvas.drawCircle(-rX1 - dp(0.3f), rY2 - dp(0.3f), highlightR, mDetailPaint);
        canvas.drawCircle(rX1 - dp(0.3f), rY2 - dp(0.3f), highlightR, mDetailPaint);

        // 腰部深色护甲带：在腰线处画一条深色横条，强调机甲腰带
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(0x22000000);
        mTempRect.set(-waistHW * 0.9f, waistY - dp(4f),
                waistHW * 0.9f, waistY + dp(4f));
        canvas.drawRoundRect(mTempRect, dp(3f), dp(3f), mDetailPaint);

        // 肩甲
        drawShoulderPad(canvas, -midHW, topY, chestY, true);
        drawShoulderPad(canvas, midHW, topY, chestY, false);

        // ---- 胸部徽章：位于胸甲区中心 ----
        float emblemCY = topY + h * 0.25f;
        float emblemR = dp(CHEST_EMBLEM_R);

        switch (state.chestMode) {
            case CHARGING:
                // 充电模式：绿色脉冲外圈 + 进度弧
                float chargePulse = 0.6f + 0.4f * (float) Math.sin(state.idleTimer * 3.0);
                int chargeGreen = 0xFF00FF66;
                mAccentPaint.setStyle(Paint.Style.STROKE);
                mAccentPaint.setStrokeWidth(dp(2.5f));
                mAccentPaint.setColor(chargeGreen);
                mAccentPaint.setShadowLayer(dp(8f) * chargePulse, 0, 0, chargeGreen);
                canvas.drawCircle(0, emblemCY, emblemR, mAccentPaint);
                mAccentPaint.clearShadowLayer();

                // 进度弧：从顶部顺时针绘制，基于 chestTaskProgress
                float sweepAngle = state.chestTaskProgress * 360f;
                mTempRect2.set(-emblemR * 0.75f, emblemCY - emblemR * 0.75f,
                        emblemR * 0.75f, emblemCY + emblemR * 0.75f);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(3f));
                mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
                mDetailPaint.setColor(chargeGreen);
                mDetailPaint.clearShadowLayer();
                canvas.drawArc(mTempRect2, -90f, sweepAngle, false, mDetailPaint);

                // 中心闪电符号简化：一条折线
                mDetailPaint.setStrokeWidth(dp(1.5f));
                mDetailPaint.setColor(0xCCFFFFFF);
                float boltH = emblemR * 0.5f;
                canvas.drawLine(dp(1f), emblemCY - boltH, -dp(2f), emblemCY, mDetailPaint);
                canvas.drawLine(-dp(2f), emblemCY, dp(1f), emblemCY + boltH, mDetailPaint);
                break;

            case QR_CODE:
                // 二维码模式：简化的 3x3 网格
                float gridSize = emblemR * 1.2f;
                float cellSize = gridSize / 3f;
                float gridLeft = -gridSize / 2f;
                float gridTop = emblemCY - gridSize / 2f;

                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(COLOR_FACE_PANEL);
                mDetailPaint.clearShadowLayer();
                // 绘制 3x3 网格中的部分单元格（模拟二维码图案）
                // 填充角落和中心形成类 QR 定位符
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        // 四个角和中心绘制深色块
                        boolean fill = (row == 0 && col == 0)
                                || (row == 0 && col == 2)
                                || (row == 2 && col == 0)
                                || (row == 1 && col == 1)
                                || (row == 2 && col == 2);
                        if (fill) {
                            float cx = gridLeft + col * cellSize + cellSize * 0.15f;
                            float cy = gridTop + row * cellSize + cellSize * 0.15f;
                            mTempRect2.set(cx, cy, cx + cellSize * 0.7f, cy + cellSize * 0.7f);
                            canvas.drawRect(mTempRect2, mDetailPaint);
                        }
                    }
                }
                // 外框
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(1f));
                mDetailPaint.setColor(COLOR_OUTLINE);
                mTempRect2.set(gridLeft, gridTop, gridLeft + gridSize, gridTop + gridSize);
                canvas.drawRect(mTempRect2, mDetailPaint);
                break;

            case NORMAL:
            default:
                // WiFi 信号图标：3 层同心弧线（扇形朝上）+ 底部中心圆点
                mAccentPaint.setStyle(Paint.Style.STROKE);
                mAccentPaint.setStrokeWidth(dp(2f));
                mAccentPaint.setColor(COLOR_EYE_CYAN);
                mAccentPaint.setShadowLayer(dp(8f) * glow, 0, 0, COLOR_EYE_CYAN);
                mAccentPaint.setStrokeCap(Paint.Cap.ROUND);
                // 外弧（最大半径 = emblemR）
                float wifiArcR1 = emblemR;
                mTempRect2.set(-wifiArcR1, emblemCY - wifiArcR1,
                        wifiArcR1, emblemCY + wifiArcR1);
                canvas.drawArc(mTempRect2, -150f, 120f, false, mAccentPaint);
                // 中弧（半径 = emblemR * 0.65）
                float wifiArcR2 = emblemR * 0.65f;
                mTempRect2.set(-wifiArcR2, emblemCY - wifiArcR2,
                        wifiArcR2, emblemCY + wifiArcR2);
                canvas.drawArc(mTempRect2, -150f, 120f, false, mAccentPaint);
                // 内弧（半径 = emblemR * 0.3）
                float wifiArcR3 = emblemR * 0.3f;
                mTempRect2.set(-wifiArcR3, emblemCY - wifiArcR3,
                        wifiArcR3, emblemCY + wifiArcR3);
                canvas.drawArc(mTempRect2, -150f, 120f, false, mAccentPaint);
                mAccentPaint.clearShadowLayer();
                // 底部中心圆点（实心青色）
                mAccentPaint.setStyle(Paint.Style.FILL);
                mAccentPaint.setColor(COLOR_EYE_CYAN);
                canvas.drawCircle(0, emblemCY, dp(2f), mAccentPaint);
                break;
        }
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
        float baseX = sideX;
        float dirMul = isLeft ? -1f : 1f;

        mTempPath.moveTo(baseX, topY + dp(10f));
        mTempPath.lineTo(baseX + dirMul * padW * 0.3f, topY + dp(5f));
        mTempPath.lineTo(baseX + dirMul * padW * 0.5f, topY + padH * 0.5f);
        mTempPath.lineTo(baseX, topY + padH);
        mTempPath.close();

        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(0x22000000);
        mDetailPaint.clearShadowLayer();
        canvas.drawPath(mTempPath, mDetailPaint);
    }

    // ==================== 手臂（9 种姿态） ====================

    /**
     * 绘制双臂（支持 9 种 ArmPose 姿态）
     *
     * 根据 state.armPose 决定左右臂的上臂角度和前臂角度，
     * 通过 state.armTransitionBlend 在姿态间平滑过渡。
     * 每条手臂由上臂管 + 肘关节球 + 前臂管 + 腕关节球 + 手指组成。
     *
     * 姿态角度定义（度，0°=右水平，-90°=正上，+90°=正下）：
     * - IDLE_SIDE: 双臂下垂，上臂 -105°，前臂 -80°
     * - WAVE: 右臂挥手（±8° 振荡），左臂下垂
     * - BOTH_UP: 双臂上举约 50°，轻微振荡
     * - POINT_LEFT: 左臂斜下 135° + 前臂向左 190°，右臂下垂
     * - POINT_RIGHT: 右臂斜下 45° + 前臂向右 -10°，左臂下垂
     * - GRAB_HOLD: 双臂前弯约 70°
     * - THINKING: 右手托腮 -60°/-160°，左臂下垂（右臂延迟到脸前绘制）
     * - CLAP: 双臂前方振荡对拍（6Hz 正弦）
     * - WIPE_SWEAT: 右手抹额 -70°/-150°，左臂下垂（右臂延迟到脸前绘制）
     *
     * @param canvas        画布
     * @param state         机器人状态
     * @param glow          发光强度
     * @param skipRightArm  true 时跳过右臂（留给 drawDeferredRightArm 在脸部之后绘制）
     */
    private void drawArms(Canvas canvas, RobotState state, float glow,
                          boolean skipRightArm) {
        float shoulderY = dp(BODY_TOP_Y) + dp(SHOULDER_Y_OFFSET);
        float bodyMidHW = dp(BODY_MID_W) / 2f;

        // 根据 ArmPose 计算左右臂的目标角度
        float leftUpperDeg, leftForearmDeg, rightUpperDeg, rightForearmDeg;
        // 挥手振荡量（仅 WAVE 和 CLAP 使用）
        float waveOsc = (float) Math.sin(state.idleTimer * 3.0) * 8f;
        // 鼓掌振荡量（6Hz）
        float clapOsc = (float) Math.sin(state.idleTimer * 6.0 * Math.PI * 2) * 15f;

        switch (state.armPose) {
            case WAVE:
                // 右臂挥手：上臂斜上 -45° + 振荡，前臂 -110° + 振荡；左臂自然下垂
                leftUpperDeg = 88f;
                leftForearmDeg = 75f;
                rightUpperDeg = -45f + waveOsc;
                rightForearmDeg = -110f + waveOsc * 0.5f;
                break;
            case BOTH_UP:
                // 双臂上举：上臂 -50°，前臂 -120°，轻微振荡
                float upOsc = (float) Math.sin(state.idleTimer * 2.0) * 3f;
                leftUpperDeg = -50f - upOsc;
                leftForearmDeg = -120f;
                rightUpperDeg = -50f + upOsc;
                rightForearmDeg = -120f;
                break;
            case POINT_LEFT:
                // 左臂指向左侧：上臂斜下135°，前臂向左190°，缩短延伸避免超出屏幕
                leftUpperDeg = 135f;
                leftForearmDeg = 190f;
                rightUpperDeg = 88f;
                rightForearmDeg = 75f;
                break;
            case POINT_RIGHT:
                // 右臂指向右侧：上臂斜下45°，前臂向右-10°，缩短延伸距离避免超出屏幕
                leftUpperDeg = 88f;
                leftForearmDeg = 75f;
                rightUpperDeg = 45f;
                rightForearmDeg = -10f;
                break;
            case GRAB_HOLD:
                // 双臂前弯在胸前：上臂前伸 30°，前臂向内弯 -60°
                leftUpperDeg = 30f;
                leftForearmDeg = -60f;
                rightUpperDeg = 30f;
                rightForearmDeg = -60f;
                break;
            case THINKING:
                // 右手托腮：上臂向左上-120°（从右肩跨过身体到脸前），前臂向右下30°折回（手掌托下巴）
                leftUpperDeg = 88f;
                leftForearmDeg = 75f;
                rightUpperDeg = -120f;
                rightForearmDeg = 30f;
                break;
            case CLAP:
                // 双臂在身前振荡对拍
                leftUpperDeg = -60f + clapOsc;
                leftForearmDeg = -120f;
                rightUpperDeg = -60f - clapOsc;
                rightForearmDeg = -120f;
                break;
            case WIPE_SWEAT:
                // 右手抹额：上臂向左上-130°（从右肩跨过到额前），前臂水平向右0°（手掌擦额头）
                leftUpperDeg = 88f;
                leftForearmDeg = 75f;
                rightUpperDeg = -130f;
                rightForearmDeg = 0f;
                break;
            case IDLE_SIDE:
            default:
                // 双臂自然下垂：上臂向下 88°（略微外撇），前臂微弯 75°
                leftUpperDeg = 88f;
                leftForearmDeg = 75f;
                rightUpperDeg = 88f;
                rightForearmDeg = 75f;
                break;
        }

        // 叠加角度偏移（来自物理响应/个性化微调）
        leftUpperDeg += state.leftArmAngleOffset;
        rightUpperDeg += state.rightArmAngleOffset;

        // 绘制左臂
        drawSingleArm(canvas, -bodyMidHW, shoulderY,
                leftUpperDeg, leftForearmDeg, false, state, glow);

        // 绘制右臂（skipRightArm 时跳过，由 drawDeferredRightArm 在脸部之后补画）
        if (!skipRightArm) {
            drawSingleArm(canvas, bodyMidHW, shoulderY,
                    rightUpperDeg, rightForearmDeg, true, state, glow);
        }
    }

    /**
     * 延迟绘制右臂（THINKING/WIPE_SWEAT 时调用，确保手在脸前面）
     *
     * 复用 drawArms 的角度计算逻辑，仅绘制右臂。
     * 在脸部所有部件（头/眼/嘴/眉）之后调用，z-order 在最前。
     */
    private void drawDeferredRightArm(Canvas canvas, RobotState state, float glow) {
        float shoulderY = dp(BODY_TOP_Y) + dp(SHOULDER_Y_OFFSET);
        float bodyMidHW = dp(BODY_MID_W) / 2f;

        float rightUpperDeg, rightForearmDeg;
        switch (state.armPose) {
            case THINKING:
                rightUpperDeg = -120f;
                rightForearmDeg = 30f;
                break;
            case WIPE_SWEAT:
                rightUpperDeg = -130f;
                rightForearmDeg = 0f;
                break;
            default:
                return;
        }
        rightUpperDeg += state.rightArmAngleOffset;
        drawSingleArm(canvas, bodyMidHW, shoulderY,
                rightUpperDeg, rightForearmDeg, true, state, glow);
    }

    /**
     * 绘制单条手臂（上臂管 + 肘关节 + 前臂管 + 腕关节 + 手指）
     *
     * 通用手臂绘制方法，接受上臂和前臂的角度参数，
     * 支持所有 9 种姿态的统一渲染。
     *
     * @param canvas      画布
     * @param shoulderX   肩膀关节 X（px）
     * @param shoulderY   肩膀关节 Y（px）
     * @param upperDeg    上臂角度（度，0°=右水平）
     * @param forearmDeg  前臂角度（度，相对于世界坐标）
     * @param isRight     是否为右臂（影响手指展开方向）
     * @param state       机器人状态
     * @param glow        发光强度
     */
    private void drawSingleArm(Canvas canvas, float shoulderX, float shoulderY,
                                float upperDeg, float forearmDeg,
                                boolean isRight, RobotState state, float glow) {
        canvas.save();
        canvas.translate(shoulderX, shoulderY);

        // 上臂
        float upperAngle = (float) Math.toRadians(upperDeg);
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

        // 肘部青色点缀
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(COLOR_EYE_CYAN);
        mDetailPaint.setShadowLayer(dp(3f), 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(elbowX, elbowY, dp(2f), mDetailPaint);
        mDetailPaint.clearShadowLayer();

        // 前臂
        float forearmAngle = (float) Math.toRadians(forearmDeg);
        float forearmLen = dp(ARM_FOREARM_LEN);
        float wristX = elbowX + (float) Math.cos(forearmAngle) * forearmLen;
        float wristY = elbowY + (float) Math.sin(forearmAngle) * forearmLen;

        // 绘制前臂管
        drawArmTube(canvas, elbowX, elbowY, wristX, wristY, dp(ARM_TUBE_W) * 0.85f);

        // 腕关节球
        canvas.drawCircle(wristX, wristY, dp(JOINT_R) * 0.85f, mDarkFillPaint);
        canvas.drawCircle(wristX, wristY, dp(JOINT_R) * 0.85f, mStrokePaint);

        // 腕部护腕环
        drawWristCuff(canvas, wristX, wristY);

        // 手（手指散开，挥手/鼓掌/上举时手指更张开）
        boolean isWaving = (state.armPose == RobotState.ArmPose.WAVE && isRight)
                || state.armPose == RobotState.ArmPose.BOTH_UP
                || state.armPose == RobotState.ArmPose.CLAP;
        drawHand(canvas, wristX, wristY, forearmAngle, isWaving);

        // 肩关节球（最后画以覆盖在管上方）
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

        // 头部高光弧
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
        float arcCX = -hw * 0.35f;
        float arcCY = cy - hh * 0.35f;
        float arcR = r * 0.9f;

        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(2.5f));
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.setColor(0x55FFFFFF);
        mDetailPaint.setShadowLayer(dp(2f), 0, 0, 0x33FFFFFF);

        mTempRect3.set(arcCX - arcR, arcCY - arcR, arcCX + arcR, arcCY + arcR);
        canvas.drawArc(mTempRect3, 200f, 70f, false, mDetailPaint);
        mDetailPaint.clearShadowLayer();
    }

    // ==================== 天线（增强：闪烁支持） ====================

    /**
     * 绘制头部顶端的天线（增强版：支持闪烁和发光相位）
     *
     * 从头部正中顶部向上延伸的细深色线杆，顶端有一个带青色辉光的小圆球。
     * 当 state.antennaFlashing 为 true 时，球体在亮/暗间快速交替。
     * 亮度由 state.antennaGlowPhase 的正弦值驱动。
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

        // 计算天线球体的发光强度
        float antennaGlow;
        if (state.antennaFlashing) {
            // 闪烁模式：基于 antennaGlowPhase 的正弦在亮/暗间快速切换
            float flashSin = (float) Math.sin(state.antennaGlowPhase);
            // 将正弦值映射到 0.2~1.0 区间
            antennaGlow = 0.2f + 0.8f * Math.max(0f, flashSin);
        } else {
            // 静态模式：正常稳定发光
            antennaGlow = glow;
        }

        // 天线顶部发光球
        int ballColor = state.antennaFlashing ? COLOR_EYE_CYAN : COLOR_EYE_CYAN;
        int ballAlpha = (int) (antennaGlow * 255);
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(setAlpha(ballColor, ballAlpha));
        mDetailPaint.setShadowLayer(dp(6f) * antennaGlow, 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(0, antennaTipY, ballR, mDetailPaint);
        mDetailPaint.clearShadowLayer();

        // 球体高光（白色小点）
        mDetailPaint.setColor(setAlpha(0xFFFFFFFF, (int) (antennaGlow * 170)));
        canvas.drawCircle(-ballR * 0.25f, antennaTipY - ballR * 0.25f,
                ballR * 0.3f, mDetailPaint);
    }

    // ==================== 侧耳（耳机垫块） ====================

    /**
     * 绘制头部两侧的信号接收器耳块（分层装甲式设计）
     *
     * 外层为上窄下宽的梯形深灰色轮廓，内部有 3 条水平格栅线模拟扬声器/散热片，
     * 中间偏上有一个青色圆形指示灯（带发光），底部有 2 个铆钉装饰点。
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
        float offsetX = dp(EAR_BLOCK_OFFSET_X);

        // 左耳块（信号接收器）
        float leftBlockCX = -headHW - offsetX - blockW / 2f;
        drawSingleEarReceiver(canvas, leftBlockCX, headCY, blockW, blockH,
                state.leftEarAngle, glow, true);

        // 右耳块（镜像）
        float rightBlockCX = headHW + offsetX + blockW / 2f;
        drawSingleEarReceiver(canvas, rightBlockCX, headCY, blockW, blockH,
                state.rightEarAngle, glow, false);
    }

    /**
     * 绘制单个信号接收器耳块
     *
     * 梯形轮廓（上窄下宽）+ 水平格栅 + 青色指示灯 + 底部铆钉。
     *
     * @param canvas  画布
     * @param cx      耳块中心 X（px）
     * @param cy      耳块中心 Y（px）
     * @param w       耳块宽度（px）
     * @param h       耳块高度（px）
     * @param angle   耳朵旋转角度
     * @param glow    发光强度
     * @param isLeft  是否为左耳
     */
    private void drawSingleEarReceiver(Canvas canvas, float cx, float cy,
                                        float w, float h, float angle,
                                        float glow, boolean isLeft) {
        canvas.save();
        float rotateMul = isLeft ? 1f : -1f;
        canvas.rotate(angle * 0.3f * rotateMul, cx, cy);

        // 梯形参数：上边窄、下边宽
        float topHW = w * 0.35f;   // 上边半宽
        float botHW = w * 0.50f;   // 下边半宽
        float halfH = h / 2f;
        float cornerR = dp(5f);    // 圆角半径

        // 构建梯形 Path（上窄下宽）
        mTempPath.reset();
        mTempPath.moveTo(cx - topHW + cornerR, cy - halfH);
        mTempPath.lineTo(cx + topHW - cornerR, cy - halfH);
        mTempPath.quadTo(cx + topHW, cy - halfH, cx + topHW, cy - halfH + cornerR);
        mTempPath.lineTo(cx + botHW, cy + halfH - cornerR);
        mTempPath.quadTo(cx + botHW, cy + halfH, cx + botHW - cornerR, cy + halfH);
        mTempPath.lineTo(cx - botHW + cornerR, cy + halfH);
        mTempPath.quadTo(cx - botHW, cy + halfH, cx - botHW, cy + halfH - cornerR);
        mTempPath.lineTo(cx - topHW, cy - halfH + cornerR);
        mTempPath.quadTo(cx - topHW, cy - halfH, cx - topHW + cornerR, cy - halfH);
        mTempPath.close();

        // 外层：深灰色梯形填充
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_JOINT);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawPath(mTempPath, mDarkFillPaint);

        // 梯形描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        mStrokePaint.setColor(COLOR_OUTLINE);
        mStrokePaint.clearShadowLayer();
        canvas.drawPath(mTempPath, mStrokePaint);

        // 中层：3 条水平格栅线（模拟扬声器/散热片）
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(1f));
        mDetailPaint.setColor(COLOR_OUTLINE);
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.clearShadowLayer();
        // 格栅分布在耳块上半部分（指示灯下方留空）
        float grillStartY = cy - halfH * 0.55f;
        float grillEndY = cy + halfH * 0.15f;
        float grillSpacing = (grillEndY - grillStartY) / 3f;
        for (int i = 0; i < 3; i++) {
            float lineY = grillStartY + grillSpacing * (i + 0.5f);
            // 根据 Y 位置线性插值计算当前行的半宽（梯形内）
            float t = (lineY - (cy - halfH)) / h;
            float currentHW = topHW + (botHW - topHW) * t;
            float inset = dp(3f);
            canvas.drawLine(cx - currentHW + inset, lineY,
                    cx + currentHW - inset, lineY, mDetailPaint);
        }

        // 内层：青色圆形指示灯（中间偏上，带发光）
        float ledY = cy + halfH * 0.35f;
        float ledR = dp(3f);
        mAccentPaint.setStyle(Paint.Style.FILL);
        mAccentPaint.setColor(COLOR_EYE_CYAN);
        mAccentPaint.setShadowLayer(dp(6f) * glow, 0, 0, COLOR_EYE_CYAN);
        canvas.drawCircle(cx, ledY, ledR, mAccentPaint);
        mAccentPaint.clearShadowLayer();

        // 底部 2 个铆钉点（深灰色小圆）
        float rivetY = cy + halfH * 0.72f;
        float rivetSpacing = dp(5f);
        float rivetR = dp(1.5f);
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(0xFF3A3A3E);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawCircle(cx - rivetSpacing, rivetY, rivetR, mDarkFillPaint);
        canvas.drawCircle(cx + rivetSpacing, rivetY, rivetR, mDarkFillPaint);

        canvas.restore();
    }

    // ==================== 面部面板 ====================

    /**
     * 绘制面部深色面板（类似面罩/显示屏区域）
     *
     * 横跨头部中间的深色圆角矩形，作为眼睛的背景，
     * 营造出机器人显示屏的视觉效果。含内侧高光边框增加深度感。
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

        // 轻微的描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(STROKE_THIN));
        mStrokePaint.setColor(setAlpha(COLOR_OUTLINE, 0x88));
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, panelR, panelR, mStrokePaint);

        // 面板内高光边框
        float inset = dp(1f);
        mTempRect3.set(-panelW / 2f + inset, panelCY - panelH / 2f + inset,
                panelW / 2f - inset, panelCY + panelH / 2f - inset);
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(dp(1f));
        mDetailPaint.setColor(COLOR_PANEL_HIGHLIGHT);
        mDetailPaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect3, panelR - inset, panelR - inset, mDetailPaint);
    }

    // ==================== 眼睛（增强：EyeSpecial 特效） ====================

    /**
     * 绘制双眼（青色发光环 + 黑色瞳孔 + EyeSpecial 特效叠加）
     *
     * 每只眼睛由三层构成：
     * 1. 外部青色发光环（带 shadowLayer 辉光效果）
     * 2. 中间暗色圆（眼球底色）
     * 3. 内部黑色瞳孔（根据 eyePupilOffsetX/Y 偏移追踪）
     *
     * 在基础眼睛绘制之后，叠加 EyeSpecial 特效：
     * - SPARKLE：瞳孔高光位置绘制 4 角十字星
     * - DIZZY：螺旋旋转覆盖层
     * - HEART：粉色心形替代瞳孔
     * - SLEEPY：半闭效果 + "=" 线条
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

        float outerR = dp(EYE_OUTER_R) * eyeScaleMul;
        float pupilR = dp(EYE_PUPIL_R) * eyeScaleMul;
        float pOffX = dp(state.eyePupilOffsetX);
        float pOffY = dp(state.eyePupilOffsetY);
        float finalGlow = glow * glowMul;

        // 左眼
        drawSingleEye(canvas, dp(EYE_L_CX), panelCY, outerR, pupilR,
                pOffX, pOffY, openness, finalGlow);

        // 右眼
        drawSingleEye(canvas, dp(EYE_R_CX), panelCY, outerR, pupilR,
                pOffX, pOffY, openness, finalGlow);

        // 叠加 EyeSpecial 特效（在两只眼睛上方绘制）
        if (state.eyeSpecial != RobotState.EyeSpecial.NONE && state.eyeSpecialIntensity > 0.01f) {
            drawEyeSpecialEffect(canvas, dp(EYE_L_CX), panelCY, outerR, pupilR,
                    pOffX, pOffY, state);
            drawEyeSpecialEffect(canvas, dp(EYE_R_CX), panelCY, outerR, pupilR,
                    pOffX, pOffY, state);
        }
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

        // 第 1 层：青色发光环
        setupGlowPaint(mEyeGlowPaint, COLOR_EYE_CYAN, dp(EYE_GLOW_RING_W),
                dp(12f) * glow);
        canvas.drawCircle(cx, cy, outerR, mEyeGlowPaint);

        // 第 2 层：暗色眼球底色
        mDarkFillPaint.setStyle(Paint.Style.FILL);
        mDarkFillPaint.setColor(COLOR_FACE_PANEL);
        mDarkFillPaint.clearShadowLayer();
        canvas.drawCircle(cx, cy, outerR - dp(EYE_GLOW_RING_W) / 2f, mDarkFillPaint);

        // 第 3 层：内圈青色发光
        setupCorePaint(mEyeCorePaint, COLOR_EYE_CYAN, dp(1.5f), dp(6f) * glow);
        canvas.drawCircle(cx, cy, outerR - dp(EYE_GLOW_RING_W), mEyeCorePaint);

        // 第 4 层：黑色瞳孔
        float pcx = cx + pupilOffX;
        float pcy = cy + pupilOffY;
        mEyeCorePaint.setStyle(Paint.Style.FILL);
        mEyeCorePaint.setColor(COLOR_PUPIL);
        mEyeCorePaint.clearShadowLayer();
        canvas.drawCircle(pcx, pcy, pupilR, mEyeCorePaint);

        // 第 5 层：瞳孔高光点
        float hlR = pupilR * 0.3f;
        mFillPaint.setStyle(Paint.Style.FILL);
        mFillPaint.setColor(COLOR_CORE);
        mFillPaint.clearShadowLayer();
        canvas.drawCircle(pcx - pupilR * 0.3f, pcy - pupilR * 0.3f, hlR, mFillPaint);

        canvas.restore();
    }

    /**
     * 绘制单只眼睛上的 EyeSpecial 特效
     *
     * 根据 state.eyeSpecial 类型在眼睛上叠加不同的视觉效果。
     * 特效强度由 state.eyeSpecialIntensity 控制透明度。
     *
     * @param canvas  画布
     * @param cx      眼睛中心 X（px）
     * @param cy      眼睛中心 Y（px）
     * @param outerR  外环半径（px）
     * @param pupilR  瞳孔半径（px）
     * @param pOffX   瞳孔水平偏移（px）
     * @param pOffY   瞳孔垂直偏移（px）
     * @param state   机器人状态
     */
    private void drawEyeSpecialEffect(Canvas canvas, float cx, float cy,
                                       float outerR, float pupilR,
                                       float pOffX, float pOffY,
                                       RobotState state) {
        float intensity = state.eyeSpecialIntensity;
        int alphaBase = (int) (intensity * 255);
        // 瞳孔中心坐标
        float pcx = cx + pOffX;
        float pcy = cy + pOffY;

        switch (state.eyeSpecial) {
            case SPARKLE: {
                // 闪烁星光：在瞳孔高光位置绘制 4 角十字星
                float sparkleR = pupilR * 0.8f;
                float hlX = pcx - pupilR * 0.2f;
                float hlY = pcy - pupilR * 0.2f;
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(1.5f));
                mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
                mDetailPaint.setColor(setAlpha(COLOR_CORE, alphaBase));
                mDetailPaint.setShadowLayer(dp(4f), 0, 0, setAlpha(COLOR_CORE, alphaBase / 2));
                // 水平线
                canvas.drawLine(hlX - sparkleR, hlY, hlX + sparkleR, hlY, mDetailPaint);
                // 垂直线
                canvas.drawLine(hlX, hlY - sparkleR, hlX, hlY + sparkleR, mDetailPaint);
                // 对角线（45° 旋转，较短）
                float diagR = sparkleR * 0.6f;
                canvas.drawLine(hlX - diagR, hlY - diagR, hlX + diagR, hlY + diagR, mDetailPaint);
                canvas.drawLine(hlX + diagR, hlY - diagR, hlX - diagR, hlY + diagR, mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
            case DIZZY: {
                // 晕眩螺旋：以瞳孔为中心绘制旋转的螺旋线
                canvas.save();
                // 基于 idleTimer 旋转螺旋
                float rotAngle = state.idleTimer * 180f; // 每秒半圈
                canvas.rotate(rotAngle, pcx, pcy);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(1.5f));
                mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
                mDetailPaint.setColor(setAlpha(COLOR_EYE_CYAN, alphaBase));
                mDetailPaint.clearShadowLayer();
                // 绘制 2 圈阿基米德螺旋（用短线段近似）
                float spiralMaxR = outerR * 0.7f;
                float prevSX = pcx;
                float prevSY = pcy;
                int spiralSegments = 24;
                for (int i = 1; i <= spiralSegments; i++) {
                    float t = (float) i / spiralSegments;
                    float spiralAngle = t * (float) (Math.PI * 4); // 2 圈
                    float spiralR = t * spiralMaxR;
                    float sx = pcx + (float) Math.cos(spiralAngle) * spiralR;
                    float sy = pcy + (float) Math.sin(spiralAngle) * spiralR;
                    canvas.drawLine(prevSX, prevSY, sx, sy, mDetailPaint);
                    prevSX = sx;
                    prevSY = sy;
                }
                canvas.restore();
                break;
            }
            case HEART: {
                // 爱心瞳孔：在瞳孔位置绘制粉色心形
                float heartSize = pupilR * 1.2f;
                int heartColor = setAlpha(0xFFFF69B4, alphaBase); // 粉色
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(heartColor);
                mDetailPaint.setShadowLayer(dp(3f), 0, 0, setAlpha(0xFFFF69B4, alphaBase / 2));
                // 用 Path 构建心形
                mTempPath.reset();
                float hx = pcx;
                float hy = pcy;
                // 心形由两段贝塞尔曲线构成
                mTempPath.moveTo(hx, hy + heartSize * 0.3f);
                mTempPath.cubicTo(hx - heartSize, hy - heartSize * 0.3f,
                        hx - heartSize * 0.5f, hy - heartSize,
                        hx, hy - heartSize * 0.5f);
                mTempPath.cubicTo(hx + heartSize * 0.5f, hy - heartSize,
                        hx + heartSize, hy - heartSize * 0.3f,
                        hx, hy + heartSize * 0.3f);
                mTempPath.close();
                canvas.drawPath(mTempPath, mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
            case SLEEPY: {
                // 瞌睡半闭：在眼睛上半部分覆盖深色遮挡 + 绘制 "=" 线条
                // 上半部分遮挡（模拟眼皮下垂）
                float coverH = outerR * 0.6f * intensity;
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(COLOR_FACE_PANEL, alphaBase));
                mDetailPaint.clearShadowLayer();
                mTempRect2.set(cx - outerR, cy - outerR, cx + outerR, cy - outerR + coverH);
                canvas.drawRect(mTempRect2, mDetailPaint);
                // 绘制 "=" 线条（两条水平线）
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
                mDetailPaint.setColor(setAlpha(COLOR_EYE_CYAN, (int) (alphaBase * 0.7f)));
                float lineW = outerR * 0.5f;
                float lineY1 = cy - dp(2f);
                float lineY2 = cy + dp(2f);
                canvas.drawLine(cx - lineW, lineY1, cx + lineW, lineY1, mDetailPaint);
                canvas.drawLine(cx - lineW, lineY2, cx + lineW, lineY2, mDetailPaint);
                break;
            }
            default:
                break;
        }
    }

    // ==================== 眉毛（新增） ====================

    /**
     * 绘制双眉（6 种 EyebrowState 状态 + 角度微调 + 混合过渡）
     *
     * 眉毛绘制在面部面板上方，每条眉毛为一条带弧度的粗描边线。
     * 位置：headCY + PANEL_OFFSET_Y - PANEL_H/2 - 5dp
     *
     * 状态效果：
     * - NEUTRAL：水平略弯弧线
     * - RAISED：位置上移，弧度更大
     * - FURROWED：位置下移，内侧向下倾斜
     * - ONE_UP：左侧正常，右侧上扬（或反向，由 blend 控制）
     * - SAD：外侧下垂呈八字形
     * - ANGRY：尖锐 V 形，粗描边
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawEyebrows(Canvas canvas, RobotState state, float glow) {
        float panelCY = dp(HEAD_CY) + dp(PANEL_OFFSET_Y);
        float panelHalfH = dp(PANEL_H) / 2f;
        // 眉毛基准 Y：面板上边缘上方 5dp
        float baseY = panelCY - panelHalfH - dp(5f);
        // 眉毛 X 范围（与眼睛对齐）
        float leftCX = dp(EYE_L_CX);  // 左眉中心
        float rightCX = dp(EYE_R_CX); // 右眉中心
        float browHalfW = dp(18f);    // 眉毛半宽

        // 状态混合因子
        float blend = Math.max(0f, Math.min(1f, state.eyebrowBlend));

        // 根据状态确定左右眉的角度和 Y 偏移
        float leftAngle = 0f;   // 左眉旋转角度（度，正值=外侧上扬）
        float rightAngle = 0f;
        float yOffset = 0f;     // Y 偏移（负值=上移）
        float strokeW = dp(2.5f);
        float arcHeight = dp(3f); // 弧度高度

        switch (state.eyebrowState) {
            case RAISED:
                yOffset = -dp(4f) * blend;
                arcHeight = dp(5f);
                leftAngle = -3f * blend;
                rightAngle = 3f * blend;
                break;
            case FURROWED:
                yOffset = dp(2f) * blend;
                // 内侧下压：左眉右端下移（负角度），右眉左端下移（正角度）
                leftAngle = 8f * blend;
                rightAngle = -8f * blend;
                arcHeight = dp(1f);
                break;
            case ONE_UP:
                // 左侧正常，右侧上扬
                leftAngle = 0f;
                rightAngle = 10f * blend;
                yOffset = 0f;
                break;
            case SAD:
                // 外侧下垂：八字形
                leftAngle = -10f * blend;
                rightAngle = 10f * blend;
                yOffset = dp(1f) * blend;
                arcHeight = dp(2f);
                break;
            case ANGRY:
                // V 形：内侧高外侧低
                leftAngle = 12f * blend;
                rightAngle = -12f * blend;
                yOffset = dp(2f) * blend;
                strokeW = dp(3.5f);
                arcHeight = dp(1f);
                break;
            case NEUTRAL:
            default:
                leftAngle = 0f;
                rightAngle = 0f;
                yOffset = 0f;
                arcHeight = dp(3f);
                break;
        }

        // 叠加角度微调
        leftAngle += state.leftEyebrowAngle;
        rightAngle += state.rightEyebrowAngle;

        // 绘制配置
        mDetailPaint.setStyle(Paint.Style.STROKE);
        mDetailPaint.setStrokeWidth(strokeW);
        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.setColor(COLOR_BODY_WHITE);
        mDetailPaint.setShadowLayer(dp(2f), 0, 0, 0x44FFFFFF);

        // 左眉
        canvas.save();
        canvas.rotate(leftAngle, leftCX, baseY + yOffset);
        mTempPath.reset();
        mTempPath.moveTo(leftCX - browHalfW, baseY + yOffset);
        mTempPath.quadTo(leftCX, baseY + yOffset - arcHeight, leftCX + browHalfW, baseY + yOffset);
        canvas.drawPath(mTempPath, mDetailPaint);
        canvas.restore();

        // 右眉
        canvas.save();
        canvas.rotate(rightAngle, rightCX, baseY + yOffset);
        mTempPath.reset();
        mTempPath.moveTo(rightCX - browHalfW, baseY + yOffset);
        mTempPath.quadTo(rightCX, baseY + yOffset - arcHeight, rightCX + browHalfW, baseY + yOffset);
        canvas.drawPath(mTempPath, mDetailPaint);
        canvas.restore();

        mDetailPaint.clearShadowLayer();
    }

    // ==================== 嘴巴（新增） ====================

    /**
     * 绘制嘴巴（7 种 MouthShape + mouthBlend + mouthOpenness + TTS 同步）
     *
     * 嘴巴绘制在面部面板下方，位置：headCY + PANEL_OFFSET_Y + PANEL_H/2 * 0.7
     *
     * 形状定义：
     * - NEUTRAL：10dp 宽水平短线
     * - SMILE：向上弯曲的弧线（三次贝塞尔）
     * - WIDE_SMILE：更宽的上弯弧 + 白色填充
     * - OPEN_O：圆形（半径 = mouthOpenness * 8dp）
     * - OPEN_D：半圆形（平顶圆底）
     * - FLAT：比 NEUTRAL 稍宽的水平线
     * - WAVY：正弦波路径（约 3 个周期）
     *
     * mouthOpenness 影响 OPEN_O 和 OPEN_D 的张开程度，也受 ttsAmplitude 影响。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawMouth(Canvas canvas, RobotState state, float glow) {
        float panelCY = dp(HEAD_CY) + dp(PANEL_OFFSET_Y);
        float panelHalfH = dp(PANEL_H) / 2f;
        // 嘴巴 Y 位置：面板底部再往下 18dp，拉开眼睛和嘴巴间距
        float mouthY = panelCY + panelHalfH + dp(18f);
        float mouthCX = 0f;

        // 实际张开度：mouthOpenness 和 ttsAmplitude 取较大值
        float openness = Math.max(state.mouthOpenness, state.ttsAmplitude * 0.8f);

        mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
        mDetailPaint.clearShadowLayer();

        switch (state.mouthShape) {
            case SMILE: {
                // 微笑：向上弯曲弧线
                float smileW = dp(16f);
                float smileH = dp(6f);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setColor(COLOR_EYE_CYAN);
                mDetailPaint.setShadowLayer(dp(3f) * glow, 0, 0, COLOR_EYE_CYAN);
                mTempPath.reset();
                mTempPath.moveTo(mouthCX - smileW / 2f, mouthY);
                mTempPath.cubicTo(mouthCX - smileW / 4f, mouthY + smileH,
                        mouthCX + smileW / 4f, mouthY + smileH,
                        mouthCX + smileW / 2f, mouthY);
                canvas.drawPath(mTempPath, mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
            case WIDE_SMILE: {
                // 大笑：更宽弧线 + 白色半透明填充
                float wideW = dp(24f);
                float wideH = dp(10f);
                // 填充
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(0x44FFFFFF);
                mTempPath.reset();
                mTempPath.moveTo(mouthCX - wideW / 2f, mouthY);
                mTempPath.cubicTo(mouthCX - wideW / 4f, mouthY + wideH,
                        mouthCX + wideW / 4f, mouthY + wideH,
                        mouthCX + wideW / 2f, mouthY);
                mTempPath.close();
                canvas.drawPath(mTempPath, mDetailPaint);
                // 描边
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setColor(COLOR_EYE_CYAN);
                mDetailPaint.setShadowLayer(dp(4f) * glow, 0, 0, COLOR_EYE_CYAN);
                mTempPath.reset();
                mTempPath.moveTo(mouthCX - wideW / 2f, mouthY);
                mTempPath.cubicTo(mouthCX - wideW / 4f, mouthY + wideH,
                        mouthCX + wideW / 4f, mouthY + wideH,
                        mouthCX + wideW / 2f, mouthY);
                canvas.drawPath(mTempPath, mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
            case OPEN_O: {
                // O 型：圆形，半径由 openness 控制
                float oRadius = Math.max(dp(2f), openness * dp(8f));
                // 外环
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setColor(COLOR_EYE_CYAN);
                mDetailPaint.setShadowLayer(dp(3f) * glow, 0, 0, COLOR_EYE_CYAN);
                canvas.drawCircle(mouthCX, mouthY, oRadius, mDetailPaint);
                // 内部暗色填充
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(COLOR_FACE_PANEL, 0xCC));
                mDetailPaint.clearShadowLayer();
                canvas.drawCircle(mouthCX, mouthY, oRadius - dp(1f), mDetailPaint);
                break;
            }
            case OPEN_D: {
                // D 型：平顶半圆（上边水平直线，下边半圆弧）
                float dWidth = dp(18f);
                float dHeight = Math.max(dp(2f), openness * dp(10f));
                // 构建 D 形 Path
                mTempPath.reset();
                mTempPath.moveTo(mouthCX - dWidth / 2f, mouthY);
                mTempPath.lineTo(mouthCX + dWidth / 2f, mouthY);
                // 下半圆弧
                mTempRect2.set(mouthCX - dWidth / 2f, mouthY, mouthCX + dWidth / 2f, mouthY + dHeight * 2f);
                mTempPath.arcTo(mTempRect2, 0f, 180f);
                mTempPath.close();
                // 暗色填充
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(COLOR_FACE_PANEL, 0xCC));
                mDetailPaint.clearShadowLayer();
                canvas.drawPath(mTempPath, mDetailPaint);
                // 描边
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setColor(COLOR_EYE_CYAN);
                mDetailPaint.setShadowLayer(dp(3f) * glow, 0, 0, COLOR_EYE_CYAN);
                canvas.drawPath(mTempPath, mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
            case FLAT: {
                // 扁嘴：水平直线，比 NEUTRAL 稍宽
                float flatW = dp(14f);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setColor(COLOR_EYE_CYAN);
                mDetailPaint.setShadowLayer(dp(2f) * glow, 0, 0, COLOR_EYE_CYAN);
                canvas.drawLine(mouthCX - flatW / 2f, mouthY, mouthCX + flatW / 2f, mouthY,
                        mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
            case WAVY: {
                // 波浪：正弦波路径，约 3 个周期
                float wavyW = dp(20f);
                float wavyAmp = dp(3f);
                int segments = 30;
                mTempPath.reset();
                for (int i = 0; i <= segments; i++) {
                    float t = (float) i / segments;
                    float x = mouthCX - wavyW / 2f + t * wavyW;
                    float y = mouthY + wavyAmp * (float) Math.sin(t * Math.PI * 6); // 3 周期
                    if (i == 0) {
                        mTempPath.moveTo(x, y);
                    } else {
                        mTempPath.lineTo(x, y);
                    }
                }
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setColor(COLOR_EYE_CYAN);
                mDetailPaint.setShadowLayer(dp(2f) * glow, 0, 0, COLOR_EYE_CYAN);
                canvas.drawPath(mTempPath, mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
            case NEUTRAL:
            default: {
                // 中性：10dp 宽水平短线
                float neutralW = dp(10f);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setColor(COLOR_EYE_CYAN);
                mDetailPaint.setShadowLayer(dp(2f) * glow, 0, 0, COLOR_EYE_CYAN);
                canvas.drawLine(mouthCX - neutralW / 2f, mouthY,
                        mouthCX + neutralW / 2f, mouthY, mDetailPaint);
                mDetailPaint.clearShadowLayer();
                break;
            }
        }
    }

    // ==================== 额头装饰 ====================

    /**
     * 绘制额头上的三个小圆点装饰（三角形排列）
     *
     * 类似传感器/指示灯，位于头部上方区域，增加机器人的科技感细节。
     * 三角形排列：上方中间一个 + 下方左右各一个。
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

    // ==================== 指示灯（增强：5 种 IndicatorState） ====================

    /**
     * 绘制面部面板上的两个微型指示灯（支持 5 种 IndicatorState）
     *
     * 在面部面板下方绘制两个小发光圆点，状态不同时显示效果各异：
     * - NORMAL：左绿右琥珀，静态发光
     * - BREATHING：双灯缓慢脉冲透明度
     * - WARNING：双灯琥珀色闪烁
     * - ERROR：双灯红色快速闪烁
     * - AI_ACTIVE：双灯青色脉冲（与 AI 阶段同步）
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawIndicatorLights(Canvas canvas, RobotState state, float glow) {
        float panelCY = dp(HEAD_CY) + dp(PANEL_OFFSET_Y);
        float panelHalfH = dp(PANEL_H) / 2f;
        float indicatorY = panelCY + panelHalfH * 0.65f;
        float indicatorR = dp(2f);
        float indicatorSpacing = dp(15f);

        int leftColor;
        int rightColor;
        float leftAlpha;
        float rightAlpha;

        switch (state.indicatorState) {
            case BREATHING: {
                // 呼吸模式：双灯缓慢脉冲
                float breathPhase = 0.5f + 0.5f * (float) Math.sin(state.idleTimer * 1.5);
                leftColor = COLOR_INDICATOR_GREEN;
                rightColor = COLOR_INDICATOR_AMBER;
                leftAlpha = breathPhase;
                rightAlpha = breathPhase;
                break;
            }
            case WARNING: {
                // 警告：双灯琥珀色闪烁（约 2Hz）
                float warnBlink = (float) Math.sin(state.idleTimer * 4.0 * Math.PI) > 0 ? 1f : 0.2f;
                leftColor = COLOR_INDICATOR_AMBER;
                rightColor = COLOR_INDICATOR_AMBER;
                leftAlpha = warnBlink;
                rightAlpha = warnBlink;
                break;
            }
            case ERROR: {
                // 错误：双灯红色快速闪烁（约 4Hz）
                float errBlink = (float) Math.sin(state.idleTimer * 8.0 * Math.PI) > 0 ? 1f : 0.1f;
                int redColor = 0xFFFF3333;
                leftColor = redColor;
                rightColor = redColor;
                leftAlpha = errBlink;
                rightAlpha = errBlink;
                break;
            }
            case AI_ACTIVE: {
                // AI 活跃：青色脉冲
                float aiPulse = 0.4f + 0.6f * (float) Math.sin(state.idleTimer * 3.0);
                leftColor = COLOR_EYE_CYAN;
                rightColor = COLOR_EYE_CYAN;
                leftAlpha = aiPulse;
                rightAlpha = aiPulse;
                break;
            }
            case NORMAL:
            default:
                // 正常：左绿右琥珀，静态
                leftColor = COLOR_INDICATOR_GREEN;
                rightColor = COLOR_INDICATOR_AMBER;
                leftAlpha = 1f;
                rightAlpha = 1f;
                break;
        }

        // 左侧指示灯
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(setAlpha(leftColor, (int) (leftAlpha * 255)));
        mDetailPaint.setShadowLayer(dp(4f) * glow * leftAlpha, 0, 0, leftColor);
        canvas.drawCircle(-indicatorSpacing, indicatorY, indicatorR, mDetailPaint);

        // 右侧指示灯
        mDetailPaint.setColor(setAlpha(rightColor, (int) (rightAlpha * 255)));
        mDetailPaint.setShadowLayer(dp(4f) * glow * rightAlpha, 0, 0, rightColor);
        canvas.drawCircle(indicatorSpacing, indicatorY, indicatorR, mDetailPaint);
        mDetailPaint.clearShadowLayer();
    }

    // ==================== 思维气泡（新增） ====================

    /**
     * 绘制思维气泡（10 种 ThoughtBubbleType + 透明度 + 动画进度）
     *
     * 气泡位于头部右上方（headCX + HEAD_W/2 * 0.6, headCY - HEAD_H/2 * 0.4）。
     * 由主椭圆 + 2 个小尾随圆组成云状外形。
     *
     * 内容根据 type 变化：
     * - DOTS：3 个跳动圆点
     * - QUESTION："?" 文字
     * - EXCLAMATION："!" 文字
     * - HEART_BUBBLE：粉色心形
     * - MUSIC_NOTE："♪" 文字
     * - ZZZ："Zzz" 文字
     * - SWEAT：蓝色水滴
     * - ANGRY_MARK："×" 标记
     * - SPARKLE_BURST：星形爆发
     * - LOADING：旋转弧线
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawThoughtBubble(Canvas canvas, RobotState state, float glow) {
        // NONE 类型或透明度为零时不绘制
        if (state.thoughtBubbleType == RobotState.ThoughtBubbleType.NONE
                || state.thoughtBubbleAlpha < 0.01f) {
            return;
        }

        float alpha = state.thoughtBubbleAlpha;
        float progress = state.thoughtBubbleProgress;
        int alphaInt = (int) (alpha * 255);

        // 气泡位置：头部右上方
        float headCX = 0f;
        float headCY = dp(HEAD_CY);
        float headHW = dp(HEAD_W) / 2f;
        float headHH = dp(HEAD_H) / 2f;

        // 气泡位置：头顶右上方
        float bubbleCX = headCX + headHW * 0.75f;
        float bubbleCY = headCY - headHH * 1.05f;

        // 主气泡尺寸（加大让气泡更醒目）
        float bubbleW = dp(55f);
        float bubbleH = dp(42f);

        // 绘制尾随小圆（从头部到气泡的过渡，位置调整让弧线自然）
        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(setAlpha(COLOR_CORE, (int) (alphaInt * 0.9f)));
        mDetailPaint.clearShadowLayer();
        // 小圆 1（靠近头顶）
        float trail1X = headCX + headHW * 0.45f;
        float trail1Y = headCY - headHH * 0.55f;
        canvas.drawCircle(trail1X, trail1Y, dp(4f), mDetailPaint);
        // 小圆 2（中间过渡）
        float trail2X = headCX + headHW * 0.6f;
        float trail2Y = headCY - headHH * 0.8f;
        canvas.drawCircle(trail2X, trail2Y, dp(6.5f), mDetailPaint);

        // 主气泡椭圆
        mDetailPaint.setColor(setAlpha(COLOR_CORE, alphaInt));
        mTempRect.set(bubbleCX - bubbleW / 2f, bubbleCY - bubbleH / 2f,
                bubbleCX + bubbleW / 2f, bubbleCY + bubbleH / 2f);
        canvas.drawRoundRect(mTempRect, bubbleH / 2f, bubbleH / 2f, mDetailPaint);

        // 气泡描边
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(dp(1f));
        mStrokePaint.setColor(setAlpha(COLOR_OUTLINE, (int) (alphaInt * 0.5f)));
        mStrokePaint.clearShadowLayer();
        canvas.drawRoundRect(mTempRect, bubbleH / 2f, bubbleH / 2f, mStrokePaint);

        // 绘制气泡内容
        drawThoughtBubbleContent(canvas, bubbleCX, bubbleCY, bubbleW, bubbleH,
                state.thoughtBubbleType, progress, alphaInt, state);
    }

    /**
     * 绘制思维气泡内部内容
     *
     * 根据气泡类型在主气泡椭圆内绘制对应的图标或文字。
     *
     * @param canvas   画布
     * @param cx       气泡中心 X（px）
     * @param cy       气泡中心 Y（px）
     * @param w        气泡宽度（px）
     * @param h        气泡高度（px）
     * @param type     气泡内容类型
     * @param progress 动画进度 [0,1]
     * @param alphaInt 透明度 [0,255]
     * @param state    机器人状态（获取 idleTimer）
     */
    private void drawThoughtBubbleContent(Canvas canvas, float cx, float cy,
                                           float w, float h,
                                           RobotState.ThoughtBubbleType type,
                                           float progress, int alphaInt,
                                           RobotState state) {
        mDetailPaint.clearShadowLayer();

        switch (type) {
            case DOTS: {
                // 3 个跳动圆点，根据 progress 产生错位的上下弹跳
                float dotR = dp(3f);
                float dotSpacing = dp(8f);
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(COLOR_JOINT, alphaInt));
                for (int i = 0; i < 3; i++) {
                    float dx = (i - 1) * dotSpacing;
                    // 每个点的弹跳相位错开 0.33
                    float bounce = (float) Math.abs(Math.sin((progress + i * 0.33f) * Math.PI * 2));
                    float dy = -bounce * dp(5f);
                    canvas.drawCircle(cx + dx, cy + dy, dotR, mDetailPaint);
                }
                break;
            }
            case QUESTION: {
                // "?" 字符
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(COLOR_JOINT, alphaInt));
                mDetailPaint.setTextSize(dp(18f));
                mDetailPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("?", cx, cy + dp(6f), mDetailPaint);
                break;
            }
            case EXCLAMATION: {
                // "!" 字符
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(0xFFFF6600, alphaInt));
                mDetailPaint.setTextSize(dp(18f));
                mDetailPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("!", cx, cy + dp(6f), mDetailPaint);
                break;
            }
            case HEART_BUBBLE: {
                // 粉色小心形
                float heartS = dp(8f);
                int heartPink = setAlpha(0xFFFF69B4, alphaInt);
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(heartPink);
                mTempPath.reset();
                mTempPath.moveTo(cx, cy + heartS * 0.3f);
                mTempPath.cubicTo(cx - heartS, cy - heartS * 0.3f,
                        cx - heartS * 0.5f, cy - heartS,
                        cx, cy - heartS * 0.4f);
                mTempPath.cubicTo(cx + heartS * 0.5f, cy - heartS,
                        cx + heartS, cy - heartS * 0.3f,
                        cx, cy + heartS * 0.3f);
                mTempPath.close();
                canvas.drawPath(mTempPath, mDetailPaint);
                break;
            }
            case MUSIC_NOTE: {
                // 音符符号 "♪"
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(COLOR_EYE_CYAN, alphaInt));
                mDetailPaint.setTextSize(dp(16f));
                mDetailPaint.setTextAlign(Paint.Align.CENTER);
                // 音符随 progress 轻微上下浮动
                float noteY = cy + dp(5f) - (float) Math.sin(progress * Math.PI * 2) * dp(3f);
                canvas.drawText("\u266A", cx, noteY, mDetailPaint);
                break;
            }
            case ZZZ: {
                // "Zzz" 瞌睡符号，从小到大排列
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(setAlpha(COLOR_EYE_CYAN, alphaInt));
                mDetailPaint.setTextAlign(Paint.Align.CENTER);
                mDetailPaint.setTextSize(dp(8f));
                canvas.drawText("z", cx - dp(6f), cy + dp(4f), mDetailPaint);
                mDetailPaint.setTextSize(dp(11f));
                canvas.drawText("z", cx, cy - dp(1f), mDetailPaint);
                mDetailPaint.setTextSize(dp(14f));
                canvas.drawText("Z", cx + dp(6f), cy - dp(6f), mDetailPaint);
                break;
            }
            case SWEAT: {
                // 蓝色水滴
                float dropH = dp(10f);
                float dropW = dp(6f);
                int blueColor = setAlpha(0xFF4488FF, alphaInt);
                mDetailPaint.setStyle(Paint.Style.FILL);
                mDetailPaint.setColor(blueColor);
                // 水滴形状：上尖下圆
                mTempPath.reset();
                mTempPath.moveTo(cx, cy - dropH / 2f);
                mTempPath.quadTo(cx + dropW / 2f, cy, cx + dropW / 3f, cy + dropH / 3f);
                mTempPath.arcTo(new RectF(cx - dropW / 3f, cy, cx + dropW / 3f, cy + dropH / 2f),
                        0f, 180f);
                mTempPath.quadTo(cx - dropW / 2f, cy, cx, cy - dropH / 2f);
                mTempPath.close();
                canvas.drawPath(mTempPath, mDetailPaint);
                break;
            }
            case ANGRY_MARK: {
                // 井字怒气标记（简化为 × 交叉）
                float markR = dp(7f);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2.5f));
                mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
                mDetailPaint.setColor(setAlpha(0xFFFF4444, alphaInt));
                canvas.drawLine(cx - markR, cy - markR, cx + markR, cy + markR, mDetailPaint);
                canvas.drawLine(cx + markR, cy - markR, cx - markR, cy + markR, mDetailPaint);
                break;
            }
            case SPARKLE_BURST: {
                // 星形爆发：从中心向外的 6 条射线
                float rayLen = dp(8f);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(1.5f));
                mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
                mDetailPaint.setColor(setAlpha(0xFFFFDD00, alphaInt));
                for (int i = 0; i < 6; i++) {
                    float angle = (float) (i * Math.PI / 3.0 + progress * Math.PI * 2);
                    float innerR = dp(2f);
                    float x1 = cx + (float) Math.cos(angle) * innerR;
                    float y1 = cy + (float) Math.sin(angle) * innerR;
                    float x2 = cx + (float) Math.cos(angle) * rayLen;
                    float y2 = cy + (float) Math.sin(angle) * rayLen;
                    canvas.drawLine(x1, y1, x2, y2, mDetailPaint);
                }
                break;
            }
            case LOADING: {
                // 旋转加载弧线
                float loadR = dp(8f);
                mTempRect2.set(cx - loadR, cy - loadR, cx + loadR, cy + loadR);
                mDetailPaint.setStyle(Paint.Style.STROKE);
                mDetailPaint.setStrokeWidth(dp(2f));
                mDetailPaint.setStrokeCap(Paint.Cap.ROUND);
                mDetailPaint.setColor(setAlpha(COLOR_EYE_CYAN, alphaInt));
                // 起始角度随 progress 旋转
                float startAngle = progress * 360f;
                canvas.drawArc(mTempRect2, startAngle, 270f, false, mDetailPaint);
                break;
            }
            default:
                break;
        }
    }

    // ==================== 脸颊红晕（新增） ====================

    /**
     * 绘制脸颊红晕效果
     *
     * 当 AI 情感为 SHY 或 aiEmotionIntensity > 0 且情感适合时，
     * 在面部面板两侧下方绘制半透明粉色圆形，模拟脸红效果。
     *
     * 位置：面部面板下缘左右各 ±45dp 处。
     * 透明度由 aiEmotionIntensity 控制，仅在 SHY/HAPPY/EXCITED 情感下显示。
     *
     * @param canvas 画布
     * @param state  机器人状态
     * @param glow   发光强度
     */
    private void drawCheekBlush(Canvas canvas, RobotState state, float glow) {
        // 仅在特定情感下显示腮红
        boolean showBlush = false;
        float blushAlpha = 0f;
        switch (state.aiEmotion) {
            case SHY:
                showBlush = true;
                blushAlpha = state.aiEmotionIntensity * 0.6f;
                break;
            case HAPPY:
                showBlush = true;
                blushAlpha = state.aiEmotionIntensity * 0.3f;
                break;
            case EXCITED:
                showBlush = true;
                blushAlpha = state.aiEmotionIntensity * 0.25f;
                break;
            default:
                break;
        }

        if (!showBlush || blushAlpha < 0.01f) {
            return;
        }

        float panelCY = dp(HEAD_CY) + dp(PANEL_OFFSET_Y);
        float panelHalfH = dp(PANEL_H) / 2f;
        // 腮红位置：面板下缘
        float blushY = panelCY + panelHalfH * 0.5f;
        float blushX = dp(45f);
        float blushR = dp(12f);

        int pinkColor = 0xFFFF8FAA; // 粉色
        int blushAlphaInt = (int) (blushAlpha * 255);

        mDetailPaint.setStyle(Paint.Style.FILL);
        mDetailPaint.setColor(setAlpha(pinkColor, blushAlphaInt));
        mDetailPaint.clearShadowLayer();

        // 左脸颊
        canvas.drawCircle(-blushX, blushY, blushR, mDetailPaint);
        // 右脸颊
        canvas.drawCircle(blushX, blushY, blushR, mDetailPaint);
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

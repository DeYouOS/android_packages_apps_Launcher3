package com.android.launcher3.robot;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;

/**
 * Canvas 2D 机器人渲染器
 *
 * 绘制科技风格的可爱机器人形象，包括圆角矩形身体、LED 点阵眼睛、
 * 线条手臂、天线等部件。所有形状附带蓝色荧光效果（setShadowLayer）。
 *
 * 视觉风格：
 * - 深色背景 (#0A0A1A) + 科技蓝主色 (#00D4FF) + 荧光发光
 * - 表情系统：IDLE（眨眼微笑）、EXCITED（弧形笑眼）、SURPRISED（圆形大眼O嘴）
 * - 空闲动画：sin 波驱动的上下浮动（呼吸感）
 *
 * 所有尺寸使用 dp 单位定义，运行时根据屏幕密度转换为 px。
 * 绘制坐标以 bodyX/bodyY 为原点，调用方负责 Canvas.translate。
 */
public class RobotRenderer {

    /** 背景纯色：深邃暗蓝黑 */
    private static final int COLOR_BACKGROUND = 0xFF0A0A1A;

    /** 主色调：科技蓝 */
    private static final int COLOR_PRIMARY = 0xFF00D4FF;

    /** 荧光色：半透明科技蓝，用于 setShadowLayer */
    private static final int COLOR_GLOW = 0x4000D4FF;

    /** 强调色：暖橙色，用于表情特效 */
    private static final int COLOR_ACCENT = 0xFFFF6B35;

    /** 荧光半径（像素），setShadowLayer 的 radius 参数 */
    private static final float GLOW_RADIUS = 20f;

    /** 身体宽度（dp） */
    private static final float BODY_WIDTH_DP = 120f;
    /** 身体高度（dp） */
    private static final float BODY_HEIGHT_DP = 160f;
    /** 身体圆角半径（dp） */
    private static final float BODY_CORNER_DP = 20f;

    /** 头部宽度（dp） */
    private static final float HEAD_WIDTH_DP = 100f;
    /** 头部高度（dp） */
    private static final float HEAD_HEIGHT_DP = 70f;
    /** 头部圆角半径（dp） */
    private static final float HEAD_CORNER_DP = 16f;

    /** 眼睛宽度（dp） */
    private static final float EYE_WIDTH_DP = 22f;
    /** 眼睛高度（dp） */
    private static final float EYE_HEIGHT_DP = 18f;
    /** 眼睛圆角半径（dp） */
    private static final float EYE_CORNER_DP = 5f;
    /** 两眼间距（dp），从中心到眼睛中心 */
    private static final float EYE_SPACING_DP = 20f;
    /** 眼睛距头部顶端的偏移（dp） */
    private static final float EYE_OFFSET_Y_DP = 24f;

    /** 嘴巴 Y 方向偏移（dp），从头部中心向下 */
    private static final float MOUTH_OFFSET_Y_DP = 46f;

    /** 手臂长度（dp） */
    private static final float ARM_LENGTH_DP = 50f;
    /** 手臂线宽（dp） */
    private static final float ARM_STROKE_DP = 4f;
    /** 关节圆半径（dp） */
    private static final float JOINT_RADIUS_DP = 5f;
    /** 手掌宽度（dp） */
    private static final float HAND_WIDTH_DP = 14f;
    /** 手掌高度（dp） */
    private static final float HAND_HEIGHT_DP = 10f;

    /** 天线杆长度（dp） */
    private static final float ANTENNA_LENGTH_DP = 25f;
    /** 天线顶球半径（dp） */
    private static final float ANTENNA_BALL_DP = 5f;

    /** 空闲浮动幅度（dp） */
    private static final float IDLE_BOB_AMPLITUDE_DP = 5f;

    /** 触发"抓握屏幕边缘"效果的手臂角度阈值（度） */
    private static final float GRIP_ANGLE_THRESHOLD = 50f;

    /** 绘制用主画笔 */
    private final Paint mPaint;

    /** 复用的矩形对象，避免每帧分配 */
    private final RectF mTempRect;

    /** 屏幕密度，dp→px 转换因子 */
    private float mDensity = 2.0f;

    /**
     * 构造渲染器
     *
     * 初始化画笔为抗锯齿模式，启用硬件加速兼容的阴影绘制。
     */
    public RobotRenderer() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTempRect = new RectF();
    }

    /**
     * dp 转 px
     *
     * @param dp 密度无关像素值
     * @return 对应的物理像素值
     */
    private float dp(float dp) {
        return dp * mDensity;
    }

    /**
     * 绘制完整机器人
     *
     * 主入口方法，按照从后到前的层次绘制所有部件：
     * 1. 清屏填充深色背景
     * 2. 平移到机器人 bodyX/bodyY 中心点
     * 3. 依次绘制手臂（最底层）→ 身体 → 头部 → 天线 → 眼睛 → 嘴巴
     *
     * 空闲动画的上下浮动通过在 translate 时叠加 sin 波偏移实现。
     *
     * @param canvas 绘制目标画布
     * @param state  当前机器人状态（位置、表情、手臂角度、计时器等）
     */
    public void draw(Canvas canvas, RobotState state) {
        // 更新屏幕密度（SurfaceView Canvas 可能返回 0，使用 fallback）
        int canvasDensity = canvas.getDensity();
        if (canvasDensity > 0 && canvasDensity != 0xFFFF) {
            mDensity = canvasDensity / 160f;
        }

        // 清屏为全透明，让下层壁纸/Workspace 透出来
        // TextureView setOpaque(false) 配合 CLEAR 模式实现透明背景
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        canvas.save();

        // 空闲呼吸动画：sin 波驱动上下浮动
        float bobOffset = (float) Math.sin(state.idleTimer * 2.0) * dp(IDLE_BOB_AMPLITUDE_DP);

        // 平移到机器人中心位置（含浮动偏移）
        canvas.translate(state.bodyX, state.bodyY + bobOffset);

        // 应用身体倾斜旋转
        canvas.rotate(state.rotation);

        // 按层次从后到前绘制
        drawArm(canvas, state, true);   // 左臂（底层）
        drawArm(canvas, state, false);  // 右臂（底层）
        drawBody(canvas);
        drawHead(canvas);
        drawAntenna(canvas);
        drawEyes(canvas, state);
        drawMouth(canvas, state);

        canvas.restore();
    }

    /**
     * 绘制机器人身体
     *
     * 圆角矩形，带荧光描边和半透明填充。
     * 身体中心在原点 (0, 0)。
     *
     * @param canvas 画布
     */
    private void drawBody(Canvas canvas) {
        float w = dp(BODY_WIDTH_DP);
        float h = dp(BODY_HEIGHT_DP);
        float r = dp(BODY_CORNER_DP);

        mTempRect.set(-w / 2f, -h / 2f, w / 2f, h / 2f);

        // 荧光填充层
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0x1A00D4FF); // 极低透明度的科技蓝填充
        mPaint.setShadowLayer(GLOW_RADIUS, 0, 0, COLOR_GLOW);
        canvas.drawRoundRect(mTempRect, r, r, mPaint);

        // 描边轮廓
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(2f));
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS, 0, 0, COLOR_GLOW);
        canvas.drawRoundRect(mTempRect, r, r, mPaint);

        mPaint.clearShadowLayer();
    }

    /**
     * 绘制机器人头部
     *
     * 位于身体顶部的较小圆角矩形，与身体有轻微重叠。
     *
     * @param canvas 画布
     */
    private void drawHead(Canvas canvas) {
        float headW = dp(HEAD_WIDTH_DP);
        float headH = dp(HEAD_HEIGHT_DP);
        float headR = dp(HEAD_CORNER_DP);
        float bodyH = dp(BODY_HEIGHT_DP);

        // 头部位于身体正上方，底边与身体顶边重叠 8dp
        float headCenterY = -bodyH / 2f - headH / 2f + dp(8f);
        mTempRect.set(-headW / 2f, headCenterY - headH / 2f,
                headW / 2f, headCenterY + headH / 2f);

        // 荧光填充
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(0x1A00D4FF);
        mPaint.setShadowLayer(GLOW_RADIUS, 0, 0, COLOR_GLOW);
        canvas.drawRoundRect(mTempRect, headR, headR, mPaint);

        // 描边
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(2f));
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS, 0, 0, COLOR_GLOW);
        canvas.drawRoundRect(mTempRect, headR, headR, mPaint);

        mPaint.clearShadowLayer();
    }

    /**
     * 绘制天线
     *
     * 从头部顶部中央向上延伸的线段 + 顶部小圆球。
     *
     * @param canvas 画布
     */
    private void drawAntenna(Canvas canvas) {
        float bodyH = dp(BODY_HEIGHT_DP);
        float headH = dp(HEAD_HEIGHT_DP);
        float antennaLen = dp(ANTENNA_LENGTH_DP);
        float ballR = dp(ANTENNA_BALL_DP);

        // 天线底部：头部顶边位置
        float headTop = -bodyH / 2f - headH + dp(8f);
        float antennaTop = headTop - antennaLen;

        // 天线杆
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(2f));
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS * 0.5f, 0, 0, COLOR_GLOW);
        canvas.drawLine(0, headTop, 0, antennaTop, mPaint);

        // 天线顶球
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS, 0, 0, COLOR_GLOW);
        canvas.drawCircle(0, antennaTop, ballR, mPaint);

        mPaint.clearShadowLayer();
    }

    /**
     * 绘制眼睛
     *
     * LED 点阵风格的眼睛，形态随表情变化：
     * - IDLE：圆角矩形，带周期性眨眼（高度通过 sin 波收缩到 2dp）
     * - EXCITED：向上弯曲的弧形（快乐眼）
     * - SURPRISED：大圆形
     *
     * @param canvas 画布
     * @param state  机器人状态，用于获取表情和空闲计时器
     */
    private void drawEyes(Canvas canvas, RobotState state) {
        float bodyH = dp(BODY_HEIGHT_DP);
        float headH = dp(HEAD_HEIGHT_DP);
        float eyeSpacing = dp(EYE_SPACING_DP);

        // 头部中心 Y 坐标
        float headCenterY = -bodyH / 2f - headH / 2f + dp(8f);
        // 眼睛 Y 坐标
        float eyeY = headCenterY - headH / 2f + dp(EYE_OFFSET_Y_DP);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS * 0.6f, 0, 0, COLOR_GLOW);

        switch (state.expression) {
            case IDLE:
                drawIdleEyes(canvas, eyeY, eyeSpacing, state.idleTimer);
                break;
            case EXCITED:
                drawExcitedEyes(canvas, eyeY, eyeSpacing);
                break;
            case SURPRISED:
                drawSurprisedEyes(canvas, eyeY, eyeSpacing);
                break;
        }

        mPaint.clearShadowLayer();
    }

    /**
     * 绘制空闲状态眼睛（带周期性眨眼）
     *
     * 眨眼效果：每约 3 秒触发一次，持续约 0.15 秒。
     * 通过 sin 函数周期性压缩眼睛高度实现。
     * 当 sin(idleTimer * 2.1) > 0.97 时触发眨眼（高度缩至 2dp）。
     *
     * @param canvas     画布
     * @param eyeY       眼睛中心 Y 坐标
     * @param eyeSpacing 眼睛间距（中心到眼睛中心）
     * @param idleTimer  空闲计时器（秒）
     */
    private void drawIdleEyes(Canvas canvas, float eyeY, float eyeSpacing, float idleTimer) {
        float eyeW = dp(EYE_WIDTH_DP);
        float eyeH = dp(EYE_HEIGHT_DP);
        float eyeR = dp(EYE_CORNER_DP);

        // 眨眼检测：sin 波峰值 > 0.97 时触发
        float blinkWave = (float) Math.sin(idleTimer * 2.1);
        if (blinkWave > 0.97f) {
            // 眨眼：高度压缩到 2dp
            eyeH = dp(2f);
        }

        // 左眼
        mTempRect.set(-eyeSpacing - eyeW / 2f, eyeY - eyeH / 2f,
                -eyeSpacing + eyeW / 2f, eyeY + eyeH / 2f);
        canvas.drawRoundRect(mTempRect, eyeR, eyeR, mPaint);

        // 右眼
        mTempRect.set(eyeSpacing - eyeW / 2f, eyeY - eyeH / 2f,
                eyeSpacing + eyeW / 2f, eyeY + eyeH / 2f);
        canvas.drawRoundRect(mTempRect, eyeR, eyeR, mPaint);
    }

    /**
     * 绘制兴奋状态眼睛（向上弯曲的弧形快乐眼）
     *
     * 使用粗描边弧线模拟快乐表情，弧线从 200° 到 340°（向上弯曲的笑眼）。
     *
     * @param canvas     画布
     * @param eyeY       眼睛中心 Y 坐标
     * @param eyeSpacing 眼睛间距
     */
    private void drawExcitedEyes(Canvas canvas, float eyeY, float eyeSpacing) {
        float eyeW = dp(EYE_WIDTH_DP);
        float eyeH = dp(EYE_HEIGHT_DP);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(3f));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(COLOR_ACCENT);
        mPaint.setShadowLayer(GLOW_RADIUS * 0.6f, 0, 0, 0x40FF6B35);

        // 左眼弧线（向上弯曲 = 笑眼）
        mTempRect.set(-eyeSpacing - eyeW / 2f, eyeY - eyeH / 2f,
                -eyeSpacing + eyeW / 2f, eyeY + eyeH / 2f);
        canvas.drawArc(mTempRect, 200f, 140f, false, mPaint);

        // 右眼弧线
        mTempRect.set(eyeSpacing - eyeW / 2f, eyeY - eyeH / 2f,
                eyeSpacing + eyeW / 2f, eyeY + eyeH / 2f);
        canvas.drawArc(mTempRect, 200f, 140f, false, mPaint);

        // 恢复画笔状态
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS * 0.6f, 0, 0, COLOR_GLOW);
    }

    /**
     * 绘制惊讶状态眼睛（大圆形）
     *
     * 用实心圆表示惊讶的大眼睛，半径比正常眼睛大。
     *
     * @param canvas     画布
     * @param eyeY       眼睛中心 Y 坐标
     * @param eyeSpacing 眼睛间距
     */
    private void drawSurprisedEyes(Canvas canvas, float eyeY, float eyeSpacing) {
        float bigRadius = dp(12f);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS * 0.8f, 0, 0, COLOR_GLOW);

        // 左眼圆形
        canvas.drawCircle(-eyeSpacing, eyeY, bigRadius, mPaint);
        // 右眼圆形
        canvas.drawCircle(eyeSpacing, eyeY, bigRadius, mPaint);
    }

    /**
     * 绘制嘴巴
     *
     * 位于头部下半区域，形态随表情变化：
     * - IDLE：小幅向上弯曲（微笑）
     * - EXCITED：宽开口弧线（大笑）
     * - SURPRISED：小圆形（O 型嘴）
     *
     * @param canvas 画布
     * @param state  机器人状态
     */
    private void drawMouth(Canvas canvas, RobotState state) {
        float bodyH = dp(BODY_HEIGHT_DP);
        float headH = dp(HEAD_HEIGHT_DP);
        float headCenterY = -bodyH / 2f - headH / 2f + dp(8f);
        float mouthY = headCenterY - headH / 2f + dp(MOUTH_OFFSET_Y_DP);

        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS * 0.5f, 0, 0, COLOR_GLOW);

        switch (state.expression) {
            case IDLE:
                // 微笑：小幅向上弧线
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(dp(2f));
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                mTempRect.set(-dp(12f), mouthY - dp(6f), dp(12f), mouthY + dp(6f));
                canvas.drawArc(mTempRect, 20f, 140f, false, mPaint);
                break;

            case EXCITED:
                // 大笑：宽弧线，使用强调色
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(dp(3f));
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                mPaint.setColor(COLOR_ACCENT);
                mPaint.setShadowLayer(GLOW_RADIUS * 0.5f, 0, 0, 0x40FF6B35);
                mTempRect.set(-dp(18f), mouthY - dp(10f), dp(18f), mouthY + dp(10f));
                canvas.drawArc(mTempRect, 10f, 160f, false, mPaint);
                break;

            case SURPRISED:
                // O 型嘴：小椭圆
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(dp(2f));
                mTempRect.set(-dp(6f), mouthY - dp(7f), dp(6f), mouthY + dp(7f));
                canvas.drawOval(mTempRect, mPaint);
                break;
        }

        mPaint.clearShadowLayer();
    }

    /**
     * 绘制手臂
     *
     * 从身体侧面延伸的线段 + 关节圆 + 矩形手掌。
     * 手臂角度由物理引擎驱动，当角度超过阈值时绘制"抓握"效果
     * （手掌变大、颜色加深）。
     *
     * @param canvas 画布
     * @param state  机器人状态（获取手臂角度）
     * @param isLeft true 为左臂，false 为右臂
     */
    private void drawArm(Canvas canvas, RobotState state, boolean isLeft) {
        float bodyW = dp(BODY_WIDTH_DP);
        float armLen = dp(ARM_LENGTH_DP);
        float jointR = dp(JOINT_RADIUS_DP);
        float handW = dp(HAND_WIDTH_DP);
        float handH = dp(HAND_HEIGHT_DP);

        // 手臂起点：身体侧面中央
        float startX = isLeft ? -bodyW / 2f : bodyW / 2f;
        float startY = 0f;

        // 获取手臂角度（度）
        float angleDeg = isLeft ? state.leftArmAngle : state.rightArmAngle;
        // 方向系数：左臂向左延伸，右臂向右延伸
        float dirX = isLeft ? -1f : 1f;

        // 将角度转换为弧度，计算手臂末端位置
        float angleRad = (float) Math.toRadians(angleDeg);
        float endX = startX + dirX * armLen * (float) Math.cos(angleRad);
        float endY = startY - armLen * (float) Math.sin(angleRad);

        // 绘制手臂线段
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(ARM_STROKE_DP));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(COLOR_PRIMARY);
        mPaint.setShadowLayer(GLOW_RADIUS * 0.4f, 0, 0, COLOR_GLOW);
        canvas.drawLine(startX, startY, endX, endY, mPaint);

        // 绘制关节圆（手臂起点）
        mPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(startX, startY, jointR, mPaint);

        // 判断是否触发抓握效果
        boolean gripping = Math.abs(angleDeg) >= GRIP_ANGLE_THRESHOLD;
        float gripScale = gripping ? 1.3f : 1.0f;

        // 绘制手掌矩形
        float scaledHandW = handW * gripScale;
        float scaledHandH = handH * gripScale;
        if (gripping) {
            mPaint.setColor(COLOR_ACCENT);
            mPaint.setShadowLayer(GLOW_RADIUS * 0.6f, 0, 0, 0x40FF6B35);
        }
        mTempRect.set(endX - scaledHandW / 2f, endY - scaledHandH / 2f,
                endX + scaledHandW / 2f, endY + scaledHandH / 2f);
        canvas.drawRoundRect(mTempRect, dp(3f), dp(3f), mPaint);

        mPaint.clearShadowLayer();
    }
}

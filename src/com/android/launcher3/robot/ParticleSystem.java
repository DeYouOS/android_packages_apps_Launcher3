package com.android.launcher3.robot;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/**
 * 霓虹猫主题多类型粒子系统（重写版）
 *
 * 支持 5 种粒子类型，为赛博朋克霓虹猫提供丰富的视觉层次：
 * - AMBIENT：浮动背景光点，缓慢漂移，呼吸透明度，25% 不透明度青色
 * - ENERGY_TRAIL：猫快速移动时的运动拖尾，继承猫速度 × 0.3，60% 主色
 * - PURR：EXCITED 状态下向上浮动的粒子，50% 强调色
 * - BURST：SURPRISED 时径向爆炸粒子，80% 黄色
 * - STAR：背景星云微粒，极小、极慢、长寿命、低透明度
 *
 * 使用对象池（80 粒子上限）避免高频 GC。
 * 提供 drawBackground/drawForeground 分层绘制，配合 CatRenderer 的绘制顺序。
 */
public class ParticleSystem {

    /**
     * 粒子类型枚举
     */
    public enum ParticleType {
        /** 环境光点：浮动背景粒子 */
        AMBIENT,
        /** 能量拖尾：运动轨迹粒子 */
        ENERGY_TRAIL,
        /** 呼噜粒子：EXCITED 状态上浮 */
        PURR,
        /** 爆发粒子：SURPRISED 径向发散 */
        BURST,
        /** 星空粒子：背景星云微粒 */
        STAR
    }

    /**
     * 单个粒子数据结构
     *
     * 对象池成员，alive 标志控制是否参与更新和绘制。
     * color 字段为完整 ARGB 值（含 alpha），绘制时直接使用。
     */
    static class Particle {
        float x, y;
        float vx, vy;
        /** 剩余生命（秒） */
        float life;
        /** 初始最大生命（秒） */
        float maxLife;
        /** 粒子直径（像素） */
        float size;
        /** 完整 ARGB 颜色 */
        int color;
        /** 粒子类型 */
        ParticleType type;
        /** 是否存活 */
        boolean alive;
    }

    // ---- 颜色常量（不含 alpha，alpha 在发射时合成） ----
    /** 主色：科技青 */
    private static final int COLOR_CYAN = 0x00D4FF;
    /** 主色：霓虹蓝（用于 ENERGY_TRAIL） */
    private static final int COLOR_PRIMARY = 0x00D4FF;
    /** 强调色：霓虹粉（用于 PURR） */
    private static final int COLOR_ACCENT = 0xFF6B9D;
    /** 爆发色：霓虹黄（用于 BURST） */
    private static final int COLOR_YELLOW = 0xFFE066;
    /** 星空色：白/淡青 */
    private static final int COLOR_STAR_WHITE = 0xE0F0FF;
    /** 星空色：浅青 */
    private static final int COLOR_STAR_CYAN = 0xB0E0FF;

    /** 粒子对象池 */
    private final Particle[] mPool;
    /** 最大粒子数量 */
    private final int mMaxParticles;
    /** 随机数生成器 */
    private final Random mRandom;

    /**
     * 构造粒子系统
     *
     * 预分配对象池，所有粒子初始为死亡状态。
     *
     * @param maxParticles 粒子池容量上限
     */
    public ParticleSystem(int maxParticles) {
        mMaxParticles = maxParticles;
        mPool = new Particle[maxParticles];
        mRandom = new Random();
        for (int i = 0; i < maxParticles; i++) {
            mPool[i] = new Particle();
            mPool[i].alive = false;
        }
    }

    /**
     * 更新所有存活粒子
     *
     * 欧拉积分更新位置，消耗生命，生命耗尽则标记死亡归还对象池。
     *
     * @param dt 时间步长（秒）
     */
    public void update(float dt) {
        for (int i = 0; i < mMaxParticles; i++) {
            Particle p = mPool[i];
            if (!p.alive) continue;

            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt;

            if (p.life <= 0f) {
                p.alive = false;
            }
        }
    }

    /**
     * 绘制所有存活粒子（不分层）
     *
     * @param canvas 画布
     * @param paint  复用画笔
     */
    public void draw(Canvas canvas, Paint paint) {
        drawParticles(canvas, paint, null);
    }

    /**
     * 绘制背景层粒子（STAR + AMBIENT 类型）
     *
     * 在猫身体绘制之前调用，为背景提供星空和光点氛围。
     *
     * @param canvas 画布
     * @param paint  复用画笔
     */
    public void drawBackground(Canvas canvas, Paint paint) {
        for (int i = 0; i < mMaxParticles; i++) {
            Particle p = mPool[i];
            if (!p.alive) continue;
            if (p.type != ParticleType.STAR && p.type != ParticleType.AMBIENT) continue;
            drawSingleParticle(canvas, paint, p);
        }
    }

    /**
     * 绘制前景层粒子（ENERGY_TRAIL + PURR + BURST 类型）
     *
     * 在猫身体绘制之后调用，覆盖在猫之上形成能量特效。
     *
     * @param canvas 画布
     * @param paint  复用画笔
     */
    public void drawForeground(Canvas canvas, Paint paint) {
        for (int i = 0; i < mMaxParticles; i++) {
            Particle p = mPool[i];
            if (!p.alive) continue;
            if (p.type != ParticleType.ENERGY_TRAIL
                    && p.type != ParticleType.PURR
                    && p.type != ParticleType.BURST) continue;
            drawSingleParticle(canvas, paint, p);
        }
    }

    /**
     * 发射环境光点粒子（AMBIENT）
     *
     * 特征：1-3dp 大小，缓慢漂移，呼吸透明度，青色 25% 不透明度
     *
     * @param x     发射中心 X
     * @param y     发射中心 Y
     * @param count 发射数量
     */
    public void emitAmbient(float x, float y, int count) {
        int emitted = 0;
        for (int i = 0; i < mMaxParticles && emitted < count; i++) {
            Particle p = mPool[i];
            if (p.alive) continue;

            p.x = x + (mRandom.nextFloat() - 0.5f) * 100f;
            p.y = y + (mRandom.nextFloat() - 0.5f) * 100f;
            // 缓慢漂移：[-20, 20] px/s
            p.vx = (mRandom.nextFloat() - 0.5f) * 40f;
            p.vy = (mRandom.nextFloat() - 0.5f) * 40f;
            // 长寿命 3~5 秒
            p.maxLife = 3f + mRandom.nextFloat() * 2f;
            p.life = p.maxLife;
            // 1-3 dp 大小
            p.size = 1f + mRandom.nextFloat() * 2f;
            // 青色 25% 不透明度 (alpha=0x40=64)
            p.color = 0x40000000 | COLOR_CYAN;
            p.type = ParticleType.AMBIENT;
            p.alive = true;
            emitted++;
        }
    }

    /**
     * 发射能量拖尾粒子（ENERGY_TRAIL）
     *
     * 特征：2-4dp 大小，继承猫速度 × 0.3 + 随机扰动，主色 60% 不透明度
     *
     * @param x     发射位置 X
     * @param y     发射位置 Y
     * @param vx    猫 X 方向速度
     * @param vy    猫 Y 方向速度
     * @param count 发射数量
     */
    public void emitEnergyTrail(float x, float y, float vx, float vy, int count) {
        int emitted = 0;
        for (int i = 0; i < mMaxParticles && emitted < count; i++) {
            Particle p = mPool[i];
            if (p.alive) continue;

            p.x = x + (mRandom.nextFloat() - 0.5f) * 20f;
            p.y = y + (mRandom.nextFloat() - 0.5f) * 20f;
            // 继承猫速度 30% + 随机扰动
            p.vx = vx * 0.3f + (mRandom.nextFloat() - 0.5f) * 60f;
            p.vy = vy * 0.3f + (mRandom.nextFloat() - 0.5f) * 60f;
            // 短寿命 0.5~1.0 秒
            p.maxLife = 0.5f + mRandom.nextFloat() * 0.5f;
            p.life = p.maxLife;
            // 2-4 dp
            p.size = 2f + mRandom.nextFloat() * 2f;
            // 主色 60% (alpha=0x99=153)
            p.color = 0x99000000 | COLOR_PRIMARY;
            p.type = ParticleType.ENERGY_TRAIL;
            p.alive = true;
            emitted++;
        }
    }

    /**
     * 发射呼噜粒子（PURR）
     *
     * 特征：1.5-2.5dp 大小，向上浮动，强调色 50% 不透明度
     *
     * @param x     发射位置 X
     * @param y     发射位置 Y
     * @param count 发射数量
     */
    public void emitPurr(float x, float y, int count) {
        int emitted = 0;
        for (int i = 0; i < mMaxParticles && emitted < count; i++) {
            Particle p = mPool[i];
            if (p.alive) continue;

            p.x = x + (mRandom.nextFloat() - 0.5f) * 60f;
            p.y = y + (mRandom.nextFloat() - 0.5f) * 30f;
            // 向上浮动 + 轻微横向随机
            p.vx = (mRandom.nextFloat() - 0.5f) * 30f;
            p.vy = -(30f + mRandom.nextFloat() * 40f); // 负值 = 向上
            // 寿命 1.0~2.0 秒
            p.maxLife = 1.0f + mRandom.nextFloat() * 1.0f;
            p.life = p.maxLife;
            // 1.5-2.5 dp
            p.size = 1.5f + mRandom.nextFloat() * 1.0f;
            // 强调色 50% (alpha=0x80=128)
            p.color = 0x80000000 | COLOR_ACCENT;
            p.type = ParticleType.PURR;
            p.alive = true;
            emitted++;
        }
    }

    /**
     * 发射爆发粒子（BURST）
     *
     * 特征：2-5dp 大小，径向爆炸发散，黄色 80% 不透明度
     *
     * @param x     爆发中心 X
     * @param y     爆发中心 Y
     * @param count 发射数量
     */
    public void emitBurst(float x, float y, int count) {
        int emitted = 0;
        for (int i = 0; i < mMaxParticles && emitted < count; i++) {
            Particle p = mPool[i];
            if (p.alive) continue;

            p.x = x;
            p.y = y;
            // 径向爆炸：随机角度 + 随机速度 100~250 px/s
            float angle = mRandom.nextFloat() * (float)(Math.PI * 2.0);
            float speed = 100f + mRandom.nextFloat() * 150f;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            // 短寿命 0.3~0.8 秒
            p.maxLife = 0.3f + mRandom.nextFloat() * 0.5f;
            p.life = p.maxLife;
            // 2-5 dp
            p.size = 2f + mRandom.nextFloat() * 3f;
            // 黄色 80% (alpha=0xCC=204)
            p.color = 0xCC000000 | COLOR_YELLOW;
            p.type = ParticleType.BURST;
            p.alive = true;
            emitted++;
        }
    }

    /**
     * 发射星空背景粒子（STAR）
     *
     * 特征：0.5-2dp 极小粒子，极慢移动，5-10 秒超长寿命，白/青极低透明度
     * 在全屏范围内随机分布。
     *
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @param count        发射数量
     */
    public void emitStar(float screenWidth, float screenHeight, int count) {
        int emitted = 0;
        for (int i = 0; i < mMaxParticles && emitted < count; i++) {
            Particle p = mPool[i];
            if (p.alive) continue;

            // 全屏随机位置
            p.x = mRandom.nextFloat() * screenWidth;
            p.y = mRandom.nextFloat() * screenHeight;
            // 极慢漂移 [-5, 5] px/s
            p.vx = (mRandom.nextFloat() - 0.5f) * 10f;
            p.vy = (mRandom.nextFloat() - 0.5f) * 10f;
            // 超长寿命 5~10 秒
            p.maxLife = 5f + mRandom.nextFloat() * 5f;
            p.life = p.maxLife;
            // 0.5-2 dp 极小
            p.size = 0.5f + mRandom.nextFloat() * 1.5f;
            // 白/青极低透明度 (alpha=0x1A~0x33 约 10%~20%)
            int alpha = 0x1A + mRandom.nextInt(0x19); // 26~50
            int baseColor = mRandom.nextBoolean() ? COLOR_STAR_WHITE : COLOR_STAR_CYAN;
            p.color = (alpha << 24) | baseColor;
            p.type = ParticleType.STAR;
            p.alive = true;
            emitted++;
        }
    }

    /**
     * 获取当前存活粒子数量
     *
     * @return 存活粒子数
     */
    public int getActiveCount() {
        int count = 0;
        for (int i = 0; i < mMaxParticles; i++) {
            if (mPool[i].alive) count++;
        }
        return count;
    }

    /**
     * 绘制指定类型过滤的粒子（null 表示不过滤）
     */
    private void drawParticles(Canvas canvas, Paint paint, ParticleType filter) {
        for (int i = 0; i < mMaxParticles; i++) {
            Particle p = mPool[i];
            if (!p.alive) continue;
            if (filter != null && p.type != filter) continue;
            drawSingleParticle(canvas, paint, p);
        }
    }

    /**
     * 绘制单个粒子
     *
     * 使用生命比例计算透明度衰减：当前 alpha = 初始 alpha × (life/maxLife)
     * 粒子以填充圆形绘制，颜色从 color 字段的 ARGB 中提取。
     *
     * @param canvas 画布
     * @param paint  复用画笔
     * @param p      要绘制的粒子
     */
    private void drawSingleParticle(Canvas canvas, Paint paint, Particle p) {
        // 从 ARGB 中提取各通道
        int originalAlpha = (p.color >>> 24) & 0xFF;
        int rgb = p.color & 0x00FFFFFF;

        // 生命衰减透明度
        float lifeRatio = (p.maxLife > 0f) ? (p.life / p.maxLife) : 0f;
        int fadedAlpha = (int)(originalAlpha * lifeRatio);
        if (fadedAlpha <= 0) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor((fadedAlpha << 24) | rgb);
        paint.clearShadowLayer();

        // BURST 和 ENERGY_TRAIL 类型添加微弱发光效果
        if (p.type == ParticleType.BURST || p.type == ParticleType.ENERGY_TRAIL) {
            paint.setShadowLayer(p.size * 2f, 0, 0, (Math.min(fadedAlpha, 80) << 24) | rgb);
        }

        canvas.drawCircle(p.x, p.y, p.size, paint);
        paint.clearShadowLayer();
    }
}

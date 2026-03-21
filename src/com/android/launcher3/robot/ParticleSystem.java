package com.android.launcher3.robot;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2D 粒子系统
 *
 * 用于渲染背景环境粒子和运动拖尾特效。采用对象池（object pool）模式
 * 复用已死亡的粒子对象，避免高频 GC。
 *
 * 两种使用场景：
 * - 背景粒子：低速随机漂移、长寿命（3~5秒）、小半径（2~4dp）、低透明度
 * - 拖尾粒子：与运动方向相反的高速喷射、短寿命（0.5~1秒）、中等半径（3~6dp）
 */
public class ParticleSystem {

    /**
     * 单个粒子数据结构
     *
     * 保存粒子的位置、速度、生命周期、视觉属性等。
     * 作为对象池的成员被复用，alive 标志控制是否参与更新和绘制。
     */
    static class Particle {
        /** X 坐标（像素） */
        float x;
        /** Y 坐标（像素） */
        float y;
        /** X 方向速度（像素/秒） */
        float vx;
        /** Y 方向速度（像素/秒） */
        float vy;
        /** 当前剩余生命（秒） */
        float life;
        /** 初始最大生命（秒），用于计算衰减比例 */
        float maxLife;
        /** 当前透明度（0~1），随生命线性衰减 */
        float alpha;
        /** 初始透明度（0~1），发射时设定 */
        float startAlpha;
        /** 粒子半径（像素） */
        float radius;
        /** 粒子颜色（不含 alpha 分量，alpha 由 alpha 字段控制） */
        int color;
        /** 是否存活，false 表示在对象池中可复用 */
        boolean alive;
    }

    /** 粒子对象池，容量固定为 maxParticles */
    private final List<Particle> mPool;

    /** 最大粒子数量上限 */
    private final int mMaxParticles;

    /** 随机数生成器，用于粒子初始参数的随机化 */
    private final Random mRandom;

    /**
     * 构造粒子系统
     *
     * 预分配所有粒子对象到对象池中，初始状态全部为"死亡"（alive=false）。
     *
     * @param maxParticles 粒子池最大容量
     */
    public ParticleSystem(int maxParticles) {
        mMaxParticles = maxParticles;
        mPool = new ArrayList<>(maxParticles);
        mRandom = new Random();
        // 预分配对象池
        for (int i = 0; i < maxParticles; i++) {
            Particle p = new Particle();
            p.alive = false;
            mPool.add(p);
        }
    }

    /**
     * 在指定位置发射粒子
     *
     * 从对象池中查找死亡粒子进行复用，设置随机速度、寿命和大小。
     * 如果对象池已满（所有粒子都存活），新发射请求将被忽略。
     *
     * 速度范围为 [-80, 80] 像素/秒，寿命范围为 [0.5, 1.5] 秒，
     * 半径范围为 [3, 6] 像素，适合拖尾效果。
     *
     * @param x     发射点 X 坐标（像素）
     * @param y     发射点 Y 坐标（像素）
     * @param count 本次发射的粒子数量
     */
    public void emit(float x, float y, int count) {
        int emitted = 0;
        for (int i = 0; i < mPool.size() && emitted < count; i++) {
            Particle p = mPool.get(i);
            if (!p.alive) {
                // 复用死亡粒子，重新初始化参数
                p.x = x;
                p.y = y;
                p.vx = (mRandom.nextFloat() - 0.5f) * 160f;
                p.vy = (mRandom.nextFloat() - 0.5f) * 160f;
                p.maxLife = 0.5f + mRandom.nextFloat() * 1.0f;
                p.life = p.maxLife;
                p.startAlpha = 0.5f + mRandom.nextFloat() * 0.3f;
                p.alpha = p.startAlpha;
                p.radius = 3f + mRandom.nextFloat() * 3f;
                p.color = 0x00D4FF; // 科技蓝（不含 alpha）
                p.alive = true;
                emitted++;
            }
        }
    }

    /**
     * 发射背景环境粒子
     *
     * 与 emit() 不同，背景粒子使用更慢的速度、更长的寿命和更低的透明度，
     * 营造静谧的科技感氛围。
     *
     * 速度范围：[-20, 20] 像素/秒（缓慢漂移）
     * 寿命范围：[3, 5] 秒（长时间可见）
     * 半径范围：[2, 4] 像素（细小光点）
     * 透明度：[0.2, 0.5]（低调不抢眼）
     *
     * @param x     发射点 X 坐标
     * @param y     发射点 Y 坐标
     * @param count 发射数量
     */
    public void emitBackground(float x, float y, int count) {
        int emitted = 0;
        for (int i = 0; i < mPool.size() && emitted < count; i++) {
            Particle p = mPool.get(i);
            if (!p.alive) {
                p.x = x;
                p.y = y;
                // 背景粒子：低速随机漂移
                p.vx = (mRandom.nextFloat() - 0.5f) * 40f;
                p.vy = (mRandom.nextFloat() - 0.5f) * 40f;
                // 背景粒子：长寿命 3~5 秒
                p.maxLife = 3f + mRandom.nextFloat() * 2f;
                p.life = p.maxLife;
                // 背景粒子：低透明度 0.2~0.5
                p.startAlpha = 0.2f + mRandom.nextFloat() * 0.3f;
                p.alpha = p.startAlpha;
                // 背景粒子：小半径 2~4 像素
                p.radius = 2f + mRandom.nextFloat() * 2f;
                p.color = 0x00D4FF; // 科技蓝
                p.alive = true;
                emitted++;
            }
        }
    }

    /**
     * 更新所有存活粒子的状态
     *
     * 每帧调用一次，执行以下操作：
     * 1. 位置 += 速度 × dt（欧拉积分）
     * 2. 生命 -= dt
     * 3. 透明度随生命线性衰减：alpha = startAlpha × (life / maxLife)
     * 4. 生命耗尽的粒子标记为死亡（alive=false），归还对象池
     *
     * @param dt 时间步长（秒）
     */
    public void update(float dt) {
        for (int i = 0; i < mPool.size(); i++) {
            Particle p = mPool.get(i);
            if (!p.alive) {
                continue;
            }
            // 欧拉积分更新位置
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            // 消耗生命
            p.life -= dt;
            if (p.life <= 0f) {
                // 生命耗尽，标记死亡归还对象池
                p.alive = false;
            } else {
                // 线性衰减透明度
                p.alpha = p.startAlpha * (p.life / p.maxLife);
            }
        }
    }

    /**
     * 绘制所有存活粒子到 Canvas
     *
     * 遍历对象池，对每个存活粒子绘制一个带透明度的填充圆。
     * Paint 对象由调用方提供以避免每帧创建。
     *
     * @param canvas 绘制目标画布
     * @param paint  复用的 Paint 对象，方法内部会修改其 color 和 alpha
     */
    public void draw(Canvas canvas, Paint paint) {
        for (int i = 0; i < mPool.size(); i++) {
            Particle p = mPool.get(i);
            if (!p.alive) {
                continue;
            }
            // 设置颜色（不含 alpha）和透明度（0~255）
            paint.setColor(0xFF000000 | p.color);
            paint.setAlpha((int) (p.alpha * 255f));
            paint.setStyle(Paint.Style.FILL);
            // 清除可能残留的阴影层
            paint.clearShadowLayer();
            canvas.drawCircle(p.x, p.y, p.radius, paint);
        }
    }

    /**
     * 获取当前存活粒子数量
     *
     * 可用于调试或动态调节发射频率。
     *
     * @return 存活粒子数
     */
    public int getAliveCount() {
        int count = 0;
        for (int i = 0; i < mPool.size(); i++) {
            if (mPool.get(i).alive) {
                count++;
            }
        }
        return count;
    }
}

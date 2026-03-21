package com.android.launcher3.robot;

/**
 * 赛博朋克霓虹猫动画状态容器（可变）
 *
 * 持有霓虹猫所有动画参数的当前值，由物理引擎每帧更新，
 * 供 CatRenderer 读取以绘制猫咪各部件。
 *
 * 包含以下子系统的状态：
 * - 身体位置与旋转（px 坐标）
 * - 表情系统（IDLE/EXCITED/SURPRISED，含混合过渡因子）
 * - 耳朵角度（左/右独立控制）
 * - 眼睛参数（瞳孔偏移、睁开度、瞳孔放大率）
 * - 尾巴物理（6 段角度与角速度数组）
 * - 呼吸/发光脉冲（glowIntensity、bodyScale）
 * - 计时器（闲置计时、眨眼计时、耳朵抽动计时）
 * - 呼噜振幅与粒子爆发请求
 * - 传感器输入（横向力、纵向力、偏航速率）
 *
 * 该类为可变对象，仅在物理引擎线程中修改，渲染线程通过快照读取。
 * 所有角度单位为度（°），坐标单位为像素（px），dp 值在渲染时转换。
 */
public class RobotState {

    // ==================== 身体位置 ====================

    /** 身体中心 X 坐标（像素），屏幕坐标系 */
    public float bodyX;

    /** 身体中心 Y 坐标（像素），屏幕坐标系 */
    public float bodyY;

    /** 身体旋转角度（度），正值为顺时针旋转 */
    public float rotation;

    /** 屏幕宽度（像素），用于坐标计算和边缘检测 */
    public float screenWidth;

    /** 屏幕高度（像素），用于坐标计算和运动范围限制 */
    public float screenHeight;

    // ==================== 表情系统 ====================

    /**
     * 猫咪表情枚举
     *
     * 表情根据车辆运动合力大小自动切换：
     * - IDLE：合力 ≤ 0.3，日常状态（呼吸 + 眨眼 + 耳朵偶尔抽动）
     * - EXCITED：合力 > 0.3，兴奋状态（瞳孔变绿变圆、尾巴上翘颤动）
     * - SURPRISED：合力 > 0.7，惊讶状态（瞳孔圆睁变黄、尾巴炸毛、耳朵后压）
     */
    public enum Expression {
        /** 闲置状态：平静，呼吸动画 + 周期性眨眼 + 尾巴自然下垂 */
        IDLE,
        /** 兴奋状态：瞳孔变绿变圆，尾巴上翘高频颤动 */
        EXCITED,
        /** 惊讶状态：瞳孔圆睁变黄，尾巴炸毛加粗，耳朵向后压平 */
        SURPRISED
    }

    /** 当前表情状态，决定猫咪各部件的绘制参数和行为 */
    public Expression expression = Expression.IDLE;

    /**
     * 表情混合过渡因子 [0, 1]
     * 在表情切换时从 0 平滑过渡到 1，用于插值新旧表情参数，
     * 避免突变造成的视觉跳跃。
     */
    public float expressionBlend;

    // ==================== 耳朵 ====================

    /**
     * 左耳角度（度）
     * 0 = 竖直朝上（自然状态）
     * 正值 = 向外倾斜
     * 负值 = 向内倾斜
     * 有效范围 [-30, 30]
     */
    public float leftEarAngle;

    /**
     * 右耳角度（度）
     * 0 = 竖直朝上（自然状态）
     * 正值 = 向外倾斜
     * 负值 = 向内倾斜
     * 有效范围 [-30, 30]
     */
    public float rightEarAngle;

    // ==================== 眼睛 ====================

    /**
     * 瞳孔水平偏移（dp 单位，渲染时转 px）
     * 正值 = 瞳孔向右看，负值 = 向左看
     * 有效范围 [-5, 5]（已按 1.45x 缩放后的 dp 值）
     */
    public float eyePupilOffsetX;

    /**
     * 瞳孔垂直偏移（dp 单位，渲染时转 px）
     * 正值 = 瞳孔向下看，负值 = 向上看
     * 有效范围 [-4, 4]
     */
    public float eyePupilOffsetY;

    /**
     * 眼睛睁开度
     * 1.0 = 完全睁开（正常状态）
     * 0.0 = 完全闭合（眨眼中）
     * 1.4 = 惊讶时圆睁（SURPRISED 状态放大）
     */
    public float eyeOpenness;

    /**
     * 瞳孔放大率
     * 1.0 = 正常大小
     * 0.6 = 收缩（强光环境或惊讶瞬间）
     * 1.3 = 放大（暗环境或兴奋状态）
     */
    public float pupilDilation;

    // ==================== 尾巴（6 段物理链） ====================

    /**
     * 尾巴各段角度数组（度），长度为 6
     * 索引 0 = 根部（连接身体），索引 5 = 尾尖
     * 每段角度相对于上一段的局部旋转
     */
    public float[] tailAngles = new float[6];

    /**
     * 尾巴各段角速度数组（度/秒），长度为 6
     * 由 TailPhysics 弹簧链求解器更新
     */
    public float[] tailAngularVel = new float[6];

    // ==================== 呼吸/发光脉冲 ====================

    /**
     * 全局发光强度 [0.2, 1.0]
     * 影响所有部件的 setShadowLayer radius 乘数
     * 通过 sin 波呼吸动画周期性变化
     */
    public float glowIntensity;

    /**
     * 发光脉冲相位（弧度）
     * 用于驱动 glowIntensity 的周期性变化：
     * glowIntensity = 0.6 + 0.4 * sin(glowPulsePhase)
     */
    public float glowPulsePhase;

    /**
     * 身体呼吸缩放 [0.98, 1.02]
     * 微小的 scale 变化配合发光脉冲营造"呼吸"感
     */
    public float bodyScale;

    // ==================== 计时器 ====================

    /**
     * 闲置计时器（秒）
     * 持续累加，驱动呼吸动画、浮动动画等周期行为
     */
    public float idleTimer;

    /**
     * 眨眼计时器（秒）
     * 倒计时到 0 时触发一次眨眼（eyeOpenness 快速降到 0 再恢复）
     * 每次眨眼后重置为 2~5 秒的随机间隔
     */
    public float blinkTimer;

    /**
     * 耳朵抽动计时器（秒）
     * 倒计时到 0 时触发一侧或双侧耳朵的快速抽动
     * 重置间隔 3~8 秒
     */
    public float earTwitchTimer;

    // ==================== 呼噜与粒子 ====================

    /**
     * 呼噜振幅 [0, 1]
     * 大于 0 时在猫咪周围产生微小的振动效果和粒子发射
     * IDLE 状态下缓慢增长至 0.3，EXCITED 状态下可达 0.8
     */
    public float purringAmplitude;

    /**
     * 粒子爆发请求计数
     * 大于 0 时触发一次粒子爆发效果（如表情切换时的光效）
     * 处理后由渲染器清零
     */
    public int particleBurstRequest;

    // ==================== 传感器输入（由外部写入） ====================

    /**
     * 横向力 [-1, 1]
     * 来自 CarSensorManager 的归一化横向加速度
     * -1 = 急左转，+1 = 急右转
     */
    public float lateralForce;

    /**
     * 纵向力 [-1, 1]
     * 来自 CarSensorManager 的归一化纵向加速度
     * -1 = 急刹车，+1 = 急加速
     */
    public float longitudinalForce;

    /**
     * 偏航速率 [-1, 1]
     * 来自 CarSensorManager 的归一化陀螺仪 Z 轴角速度
     * -1 = 快速左转，+1 = 快速右转
     */
    public float yawRate;

    /**
     * 默认构造函数
     *
     * 初始化为居中、无旋转、闲置表情、眼睛完全睁开、
     * 瞳孔正常大小、中等发光强度的默认状态。
     */
    public RobotState() {
        bodyX = 0f;
        bodyY = 0f;
        rotation = 0f;
        screenWidth = 0f;
        screenHeight = 0f;

        expression = Expression.IDLE;
        expressionBlend = 0f;

        leftEarAngle = 0f;
        rightEarAngle = 0f;

        eyePupilOffsetX = 0f;
        eyePupilOffsetY = 0f;
        eyeOpenness = 1.0f;
        pupilDilation = 1.0f;

        // 尾巴初始角度和角速度全为 0（自然下垂）
        for (int i = 0; i < 6; i++) {
            tailAngles[i] = 0f;
            tailAngularVel[i] = 0f;
        }

        glowIntensity = 0.6f;
        glowPulsePhase = 0f;
        bodyScale = 1.0f;

        idleTimer = 0f;
        blinkTimer = 3.0f;
        earTwitchTimer = 5.0f;

        purringAmplitude = 0f;
        particleBurstRequest = 0;

        lateralForce = 0f;
        longitudinalForce = 0f;
        yawRate = 0f;
    }
}

package com.android.launcher3.robot;

/**
 * 车载 AI 机器人动画状态容器（可变）
 *
 * 持有机器人所有动画参数的当前值，由物理引擎每帧更新，
 * 供渲染器读取以绘制机器人各部件。
 *
 * 包含以下子系统的状态：
 * - 身体位置与旋转（px 坐标）
 * - 表情系统（IDLE/EXCITED/SURPRISED，含混合过渡因子）
 * - 耳朵角度（左/右独立控制）
 * - 眼睛参数（瞳孔偏移、睁开度、瞳孔放大率）
 * - 眼睛特效（闪烁、晕眩、爱心、瞌睡等）
 * - 眉毛状态（左/右独立角度 + 混合过渡）
 * - 嘴巴形状（语音同步、表情驱动）
 * - 手臂姿态（9 种预设姿势 + 过渡混合）
 * - 身体动作（点头、摇头、弹跳、颤抖等）
 * - 尾巴物理（6 段角度与角速度数组）
 * - 呼吸/发光脉冲（glowIntensity、bodyScale）
 * - 天线状态（发光相位、闪烁标志）
 * - 胸部显示（常规/充电/二维码模式）
 * - 指示灯状态（呼吸、警告、错误、AI 活跃）
 * - 速度与 GPS 数据（时速、加速度、航向、速度区间）
 * - 驾驶场景（驾驶模式、充电状态、时段）
 * - AI 连接状态（断连/连接中/已连接/错误/重连）
 * - AI 对话阶段（监听/理解/思考/说话/执行）
 * - AI 情感系统（中性/开心/兴奋/好奇/困惑等 10 种）
 * - AI 任务状态（语音唤醒/识别/TTS/导航/音乐等）
 * - 思维气泡（省略号、问号、感叹号、音符、ZZZ 等）
 * - 语音参数（TTS 振幅、声源方向）
 * - 计时器（闲置计时、眨眼计时、耳朵抽动计时）
 * - 呼噜振幅与粒子爆发请求
 * - 传感器输入（横向力、纵向力、偏航速率）
 *
 * 该类为可变对象，仅在物理引擎线程中修改，渲染线程通过快照读取。
 * 所有角度单位为度（°），坐标单位为像素（px），dp 值在渲染时转换。
 */
public class RobotState {

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                      内 部 枚 举 定 义                       ║
    // ╚══════════════════════════════════════════════════════════════╝

    // ==================== 表情枚举 ====================

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

    // ==================== 手臂姿态枚举 ====================

    /**
     * 手臂姿态枚举
     *
     * 定义机器人双臂的预设姿势，用于表达不同情绪和动作。
     * 手臂在不同姿态间通过 armTransitionBlend 平滑过渡。
     */
    public enum ArmPose {
        /** 自然下垂：双臂放在身体两侧，闲置默认姿势 */
        IDLE_SIDE,
        /** 挥手：单臂抬起左右摆动，用于打招呼或告别 */
        WAVE,
        /** 双臂上举：欢呼或庆祝时双手高举 */
        BOTH_UP,
        /** 左指：左臂伸出指向左侧，用于导航指示 */
        POINT_LEFT,
        /** 右指：右臂伸出指向右侧，用于导航指示 */
        POINT_RIGHT,
        /** 抓握：双臂在胸前呈握持姿态，用于展示物品 */
        GRAB_HOLD,
        /** 思考：单臂抬起托腮，配合思考表情使用 */
        THINKING,
        /** 鼓掌：双臂在胸前拍手，用于完成任务时庆祝 */
        CLAP,
        /** 擦汗：单臂抬至额头擦拭，表示辛苦或如释重负 */
        WIPE_SWEAT
    }

    // ==================== 嘴巴形状枚举 ====================

    /**
     * 嘴巴形状枚举
     *
     * 定义机器人嘴巴的基本形状，用于表情和语音同步。
     * 实际形状通过 mouthBlend 在相邻形状间插值。
     */
    public enum MouthShape {
        /** 中性：自然闭合的微弯弧线，默认状态 */
        NEUTRAL,
        /** 微笑：嘴角上扬的弧线，轻度愉悦 */
        SMILE,
        /** 大笑：嘴角大幅上扬并张开，强烈愉悦 */
        WIDE_SMILE,
        /** O 型：嘴巴张成圆形，表示惊讶或说"哦" */
        OPEN_O,
        /** D 型：嘴巴张成 D 形，表示大笑或说话中的开口音 */
        OPEN_D,
        /** 扁嘴：嘴唇水平紧闭，表示不满或平淡 */
        FLAT,
        /** 波浪：嘴唇呈波浪线，表示紧张或不确定 */
        WAVY
    }

    // ==================== 眉毛状态枚举 ====================

    /**
     * 眉毛状态枚举
     *
     * 定义机器人眉毛的表情姿态，与眼睛和嘴巴配合传达情绪。
     * 左右眉毛角度可通过 leftEyebrowAngle/rightEyebrowAngle 微调。
     */
    public enum EyebrowState {
        /** 中性：水平自然放松，默认状态 */
        NEUTRAL,
        /** 上扬：双眉上挑，表示惊讶或期待 */
        RAISED,
        /** 皱眉：双眉向中间收拢下压，表示困惑或专注 */
        FURROWED,
        /** 单挑：一侧眉毛上扬另一侧保持，表示疑问或俏皮 */
        ONE_UP,
        /** 悲伤：双眉内侧上扬外侧下垂，呈八字形 */
        SAD,
        /** 生气：双眉向下倾斜内收，呈倒八字形 */
        ANGRY
    }

    // ==================== 眼睛特效枚举 ====================

    /**
     * 眼睛特效枚举
     *
     * 在基本眼睛绘制之上叠加的特殊效果，用于强化情绪表达。
     * 特效强度由 eyeSpecialIntensity 控制。
     */
    public enum EyeSpecial {
        /** 无特效：正常眼睛渲染 */
        NONE,
        /** 闪烁星光：瞳孔中出现十字星光，表示兴奋或崇拜 */
        SPARKLE,
        /** 晕眩螺旋：瞳孔变为旋转螺旋，表示头晕或混乱 */
        DIZZY,
        /** 爱心瞳孔：瞳孔变为心形，表示喜爱或心动 */
        HEART,
        /** 瞌睡半闭：眼睛半闭下垂，表示困倦或无聊 */
        SLEEPY
    }

    // ==================== 身体动作枚举 ====================

    /**
     * 身体动作枚举
     *
     * 定义机器人身体的附加动画动作，叠加在基础姿态之上。
     * 动作进度由 bodyActionProgress 控制，强度由 bodyActionIntensity 调节。
     */
    public enum BodyAction {
        /** 无动作：身体保持静止，仅有呼吸动画 */
        NONE,
        /** 点头：身体小幅前后倾斜，表示肯定或理解 */
        NOD,
        /** 摇头：身体小幅左右旋转，表示否定或不赞同 */
        SHAKE_HEAD,
        /** 弹跳：身体上下弹跳，表示兴奋或开心 */
        BOUNCE,
        /** 颤抖：身体高频微颤，表示寒冷或紧张 */
        SHIVER,
        /** 侧倾：身体向一侧倾斜，表示好奇或疑问 */
        TILT,
        /** 打哈欠：身体向后仰并伸展，配合张嘴动画 */
        YAWN,
        /** 伸展：身体上拉伸展，用于长时间静止后的舒展 */
        STRETCH
    }

    // ==================== 速度区间枚举 ====================

    /**
     * 速度区间枚举
     *
     * 根据 GPS 时速自动划分，驱动机器人表情和动画风格切换。
     * 不同区间下机器人的活跃度和注意力表现不同。
     */
    public enum SpeedZone {
        /** 停车：时速 ≈ 0，完全静止状态 */
        PARKED,
        /** 市区：时速 0-40 km/h，低速行驶 */
        CITY,
        /** 正常：时速 40-80 km/h，中速巡航 */
        NORMAL,
        /** 高速：时速 80-120 km/h，高速行驶 */
        HIGHWAY,
        /** 超速：时速 > 120 km/h，超过限速 */
        OVER_LIMIT,
        /** 危险：时速极高或急加速，危险驾驶状态 */
        DANGER
    }

    // ==================== 驾驶模式枚举 ====================

    /**
     * 驾驶模式枚举
     *
     * 综合速度、加速度、转向等信息判断当前驾驶场景，
     * 影响机器人的整体行为模式和动画风格。
     */
    public enum DrivingMode {
        /** 睡眠：车辆熄火或长时间静止，机器人进入休眠状态 */
        SLEEPING,
        /** 停车：引擎启动但未移动，机器人保持低功耗闲置 */
        PARKED,
        /** 起步：车辆从静止开始移动，机器人切换到活跃模式 */
        STARTING,
        /** 巡航：稳定速度行驶，机器人保持平静警觉 */
        CRUISING,
        /** 过弯：检测到较大横向力，机器人身体向弯道内侧倾斜 */
        CORNERING,
        /** 颠簸：检测到垂直力波动，机器人身体随之抖动 */
        BUMPY,
        /** 倒车：检测到倒车信号，机器人回头观察姿态 */
        REVERSING
    }

    // ==================== 指示灯状态枚举 ====================

    /**
     * 指示灯状态枚举
     *
     * 控制机器人身上状态指示灯的显示模式，
     * 反映系统运行状态和通知级别。
     */
    public enum IndicatorState {
        /** 正常：指示灯稳定发光，系统运行正常 */
        NORMAL,
        /** 呼吸：指示灯缓慢明暗交替，表示待机或等待中 */
        BREATHING,
        /** 警告：指示灯黄色闪烁，表示非致命异常 */
        WARNING,
        /** 错误：指示灯红色快速闪烁，表示需要注意的错误 */
        ERROR,
        /** AI 活跃：指示灯蓝色流光，表示 AI 正在处理任务 */
        AI_ACTIVE
    }

    // ==================== AI 连接状态枚举 ====================

    /**
     * AI 连接状态枚举
     *
     * 表示机器人与 AI 后端服务的网络连接状况，
     * 影响机器人的可用功能和天线动画。
     */
    public enum AIConnectionState {
        /** 已断开：无网络连接，天线暗淡无光 */
        DISCONNECTED,
        /** 连接中：正在建立连接，天线缓慢闪烁 */
        CONNECTING,
        /** 已连接待命：连接建立但 AI 空闲，天线稳定发光 */
        CONNECTED_IDLE,
        /** 连接错误：连接失败或超时，天线红色闪烁 */
        ERROR,
        /** 重连中：断连后自动重试，天线快速闪烁 */
        RECONNECTING
    }

    // ==================== AI 对话阶段枚举 ====================

    /**
     * AI 对话阶段枚举
     *
     * 描述 AI 语音交互的完整生命周期，每个阶段有对应的动画表现。
     * 阶段间通过 aiPhaseBlend 平滑过渡。
     */
    public enum AIDialogPhase {
        /** 空闲：无对话进行，机器人处于默认状态 */
        IDLE,
        /** 聆听：检测到语音输入，机器人竖耳专注表情 */
        LISTENING,
        /** 理解：语音识别完成正在语义分析，机器人微微歪头 */
        UNDERSTANDING,
        /** 思考：AI 正在生成回答，机器人托腮思考表情 */
        THINKING,
        /** 说话：TTS 语音输出中，嘴巴随音频振幅同步动画 */
        SPEAKING,
        /** 执行：AI 正在执行用户指令（如导航、播放音乐） */
        EXECUTING,
        /** 成功：任务执行成功，机器人展示庆祝动画 */
        SUCCESS,
        /** 失败：任务执行失败，机器人展示抱歉表情 */
        FAILED
    }

    // ==================== AI 情感枚举 ====================

    /**
     * AI 情感枚举
     *
     * AI 对话过程中根据语义分析和任务结果产生的情感状态，
     * 影响机器人的表情、动作和语气。
     * 强度由 aiEmotionIntensity 控制，过渡由 aiEmotionBlend 控制。
     */
    public enum AIEmotion {
        /** 中性：平静无明显情感，默认状态 */
        NEUTRAL,
        /** 开心：轻度愉悦，微笑表情 + 轻微弹跳 */
        HAPPY,
        /** 兴奋：强烈愉悦，大笑 + 手臂上举 + 弹跳 */
        EXCITED,
        /** 好奇：发现有趣信息，歪头 + 眉毛上扬 + 眼睛放大 */
        CURIOUS,
        /** 困惑：无法理解或处理，皱眉 + 头顶问号气泡 */
        CONFUSED,
        /** 抱歉：任务失败或无法满足，微微鞠躬 + 悲伤眉毛 */
        SORRY,
        /** 担心：检测到异常或风险，眉毛下垂 + 身体微缩 */
        WORRIED,
        /** 自豪：出色完成任务，挺胸 + 微笑 + 胸灯发亮 */
        PROUD,
        /** 害羞：收到夸奖或亲密互动，脸红 + 微微低头 */
        SHY,
        /** 困倦回复：深夜时段的低能量回复，半闭眼 + 哈欠 */
        SLEEPY_REPLY
    }

    // ==================== AI 任务状态枚举 ====================

    /**
     * AI 任务状态枚举
     *
     * 表示 AI 当前正在执行的具体任务类型，
     * 影响胸部显示内容、手臂姿态和特殊动画。
     */
    public enum AITaskState {
        /** 无任务：AI 空闲，无特殊显示 */
        NONE,
        /** 语音唤醒：检测到唤醒词，天线亮起 + 耳朵竖直 */
        VOICE_WAKE,
        /** 语音识别中：正在将语音转为文字，耳朵微动 + 聆听表情 */
        VOICE_RECOGNIZING,
        /** TTS 播报中：正在语音输出，嘴巴同步动画 */
        TTS_SPEAKING,
        /** 导航中：正在提供导航指引，手臂指向方向 */
        NAVIGATING,
        /** 音乐播放中：正在播放音乐，身体随节奏微动 */
        MUSIC_PLAYING,
        /** 搜索中：正在搜索信息，眼睛快速转动 */
        SEARCHING,
        /** 下载中：正在下载内容，胸部显示进度条 */
        DOWNLOADING,
        /** 拍照中：正在拍摄照片，闪光灯效果 */
        TAKING_PHOTO,
        /** 显示二维码：胸部显示二维码供扫描 */
        SHOWING_QR
    }

    // ==================== 思维气泡类型枚举 ====================

    /**
     * 思维气泡类型枚举
     *
     * 定义机器人头顶漫画风格思维气泡的内容类型，
     * 用于可视化表达机器人当前的"心理状态"。
     */
    public enum ThoughtBubbleType {
        /** 无气泡：不显示任何思维气泡 */
        NONE,
        /** 省略号：三个跳动的点，表示正在思考或加载中 */
        DOTS,
        /** 问号：大问号，表示疑惑或不理解 */
        QUESTION,
        /** 感叹号：大感叹号，表示突然领悟或警告 */
        EXCLAMATION,
        /** 爱心气泡：冒出爱心，表示喜爱或感谢 */
        HEART_BUBBLE,
        /** 音符：跳动的音符，表示正在听音乐或心情愉快 */
        MUSIC_NOTE,
        /** ZZZ：瞌睡符号，表示休眠或无聊 */
        ZZZ,
        /** 汗滴：额头冒汗，表示紧张或尴尬 */
        SWEAT,
        /** 怒气符号：井字怒气标记，表示不满或烦躁 */
        ANGRY_MARK,
        /** 闪光爆发：星星爆发效果，表示灵感或兴奋 */
        SPARKLE_BURST,
        /** 加载圈：旋转加载指示器，表示等待后端响应 */
        LOADING
    }

    // ==================== 胸部显示模式枚举 ====================

    /**
     * 胸部显示模式枚举
     *
     * 控制机器人胸部区域的显示内容模式。
     */
    public enum ChestMode {
        /** 正常：显示默认胸部图案或状态指示 */
        NORMAL,
        /** 充电中：显示充电动画和电量指示 */
        CHARGING,
        /** 二维码：显示二维码供用户扫描 */
        QR_CODE
    }

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                      状 态 字 段 定 义                       ║
    // ╚══════════════════════════════════════════════════════════════╝

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

    // ==================== 眼睛特效 ====================

    /**
     * 眼睛特效类型
     * 叠加在基本眼睛渲染之上的特殊视觉效果。
     * 由 AI 情感和任务状态驱动切换。
     */
    public EyeSpecial eyeSpecial = EyeSpecial.NONE;

    /**
     * 眼睛特效强度 [0, 1]
     * 0 = 特效完全不可见，1 = 特效完全显示。
     * 用于特效的淡入淡出过渡。
     */
    public float eyeSpecialIntensity;

    /**
     * 困倦度 [0, 1]
     * 0 = 完全清醒，1 = 即将入睡。
     * 影响眼睛睁开度、眨眼频率和身体下沉幅度。
     * 由时段和闲置时长驱动。
     */
    public float sleepiness;

    // ==================== 眉毛 ====================

    /**
     * 眉毛表情状态
     * 定义双眉的基本姿态，与 EyeSpecial、MouthShape 配合传达情绪。
     * 由表情系统和 AI 情感驱动。
     */
    public EyebrowState eyebrowState = EyebrowState.NEUTRAL;

    /**
     * 左眉角度偏移（度）
     * 在基本眉毛状态上叠加的角度微调。
     * 正值 = 上扬，负值 = 下压。
     * 用于非对称表情和细微情绪变化。
     */
    public float leftEyebrowAngle;

    /**
     * 右眉角度偏移（度）
     * 在基本眉毛状态上叠加的角度微调。
     * 正值 = 上扬，负值 = 下压。
     * 用于非对称表情和细微情绪变化。
     */
    public float rightEyebrowAngle;

    /**
     * 眉毛过渡混合因子 [0, 1]
     * 在眉毛状态切换时从 0 平滑过渡到 1，
     * 用于插值前后状态的角度和位置参数。
     */
    public float eyebrowBlend;

    // ==================== 嘴巴 ====================

    /**
     * 嘴巴形状
     * 定义嘴巴的基本形态，用于表情和 TTS 语音同步。
     * 由 AI 对话阶段和情感状态驱动。
     */
    public MouthShape mouthShape = MouthShape.NEUTRAL;

    /**
     * 嘴巴形状混合因子 [0, 1]
     * 在嘴巴形状切换时从 0 平滑过渡到 1，
     * 用于插值相邻形状的轮廓参数。
     */
    public float mouthBlend;

    /**
     * 嘴巴张开度 [0, 1]
     * 0 = 完全闭合，1 = 最大张开。
     * 主要用于打哈欠动画和说话时的口型同步，
     * 与 ttsAmplitude 联动驱动。
     */
    public float mouthOpenness;

    // ==================== 手臂 ====================

    /**
     * 手臂姿态
     * 定义双臂的预设姿势，不同姿态间通过 armTransitionBlend 平滑过渡。
     * 由 AI 任务状态和情感驱动切换。
     */
    public ArmPose armPose = ArmPose.IDLE_SIDE;

    /**
     * 手臂过渡混合因子 [0, 1]
     * 在手臂姿态切换时从 0 平滑过渡到 1，
     * 用于插值前后姿态的关节角度。
     */
    public float armTransitionBlend;

    /**
     * 左臂角度偏移（度）
     * 在预设姿态基础上叠加的微调角度。
     * 用于手臂随车辆晃动的物理响应和个性化动画。
     */
    public float leftArmAngleOffset;

    /**
     * 右臂角度偏移（度）
     * 在预设姿态基础上叠加的微调角度。
     * 用于手臂随车辆晃动的物理响应和个性化动画。
     */
    public float rightArmAngleOffset;

    // ==================== 身体动作 ====================

    /**
     * 当前身体动作
     * 叠加在基础姿态之上的附加动画。
     * 由 AI 情感、驾驶模式和交互事件触发。
     */
    public BodyAction bodyAction = BodyAction.NONE;

    /**
     * 身体动作播放进度 [0, 1]
     * 0 = 动作起始帧，1 = 动作结束帧。
     * 到达 1 后根据动作类型决定循环或回到 NONE。
     */
    public float bodyActionProgress;

    /**
     * 身体动作强度
     * 控制动作幅度的缩放系数。
     * 1.0 = 标准幅度，0.5 = 轻微，2.0 = 夸张。
     * 由情感强度和驾驶状态驱动。
     */
    public float bodyActionIntensity;

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

    // ==================== 天线 ====================

    /**
     * 天线发光相位（弧度）
     * 驱动天线顶部光球的脉冲动画。
     * 与 AI 连接状态联动：已连接时稳定脉冲，断开时暗淡。
     */
    public float antennaGlowPhase;

    /**
     * 天线是否闪烁
     * true = 快速闪烁模式（连接中/重连中/收到消息）
     * false = 正常脉冲或熄灭
     */
    public boolean antennaFlashing;

    // ==================== 指示灯 ====================

    /**
     * 指示灯状态
     * 控制机器人身上状态指示灯的显示模式。
     * 由系统运行状态和 AI 连接状态驱动。
     */
    public IndicatorState indicatorState = IndicatorState.NORMAL;

    // ==================== 胸部显示 ====================

    /**
     * 胸部显示模式
     * 控制机器人胸部区域的渲染内容。
     * 默认显示常规图案，充电时显示电量动画，需要时显示二维码。
     */
    public ChestMode chestMode = ChestMode.NORMAL;

    /**
     * 胸部任务进度 [0, 1]
     * 在下载、充电等有进度的任务中指示完成百分比。
     * 0 = 未开始，1 = 已完成。
     * 仅在 chestMode 为 CHARGING 或任务需要进度显示时有效。
     */
    public float chestTaskProgress;

    // ==================== 速度与 GPS ====================

    /**
     * GPS 时速（km/h）
     * 来自 GPS 定位的实时行驶速度。
     * 驱动 SpeedZone 区间判定和驾驶模式切换。
     */
    public float speedKmh;

    /**
     * 速度变化率（km/h/s）
     * 时速的一阶导数，正值为加速，负值为减速。
     * 用于检测急加速/急刹车事件。
     */
    public float speedAcceleration;

    /**
     * GPS 航向角（度）[0, 360)
     * 0/360 = 正北，90 = 正东，180 = 正南，270 = 正西。
     * 用于导航指引和方向变化检测。
     */
    public float bearing;

    /**
     * 速度区间
     * 根据 speedKmh 自动划分的速度等级。
     * 影响机器人的活跃度、注意力和警觉程度。
     */
    public SpeedZone speedZone = SpeedZone.PARKED;

    /**
     * GPS 是否可用
     * false = GPS 信号丢失或未授权，速度数据不可靠。
     * 信号丢失时机器人头顶可显示信号丢失提示。
     */
    public boolean gpsAvailable;

    // ==================== 驾驶场景 ====================

    /**
     * 当前驾驶模式
     * 综合多种传感器数据判定的驾驶场景。
     * 影响机器人的整体行为策略和动画风格。
     */
    public DrivingMode drivingMode = DrivingMode.PARKED;

    /**
     * 闲置持续时间（秒）
     * 车辆停止后的累计时间。
     * 超过阈值后机器人逐步进入打盹 → 睡眠状态。
     */
    public float idleDuration;

    /**
     * 行驶持续时间（秒）
     * 本次连续行驶的累计时间。
     * 长时间驾驶后机器人可提示休息。
     */
    public float drivingDuration;

    /**
     * 是否正在充电
     * true = 车辆正在充电（有线/无线）。
     * 充电时胸部切换为 CHARGING 模式显示电量动画。
     */
    public boolean isCharging;

    /**
     * 当前小时 [0, 23]
     * 用于时段感知：深夜 (22-6) 机器人进入低能量模式，
     * 白天 (7-21) 保持正常活跃度。
     */
    public int hourOfDay;

    // ==================== AI 连接 ====================

    /**
     * AI 后端连接状态
     * 反映与 AI 服务的网络连接状况。
     * 影响天线动画和可用功能指示。
     */
    public AIConnectionState aiConnectionState = AIConnectionState.DISCONNECTED;

    /**
     * AI 重连尝试次数
     * 断连后已进行的重连尝试数。
     * 用于指数退避策略和用户提示。
     */
    public int aiReconnectAttempts;

    // ==================== AI 对话 ====================

    /**
     * AI 对话当前阶段
     * 描述语音交互的生命周期位置。
     * 每个阶段驱动不同的表情、动作和音效组合。
     */
    public AIDialogPhase aiDialogPhase = AIDialogPhase.IDLE;

    /**
     * 当前对话阶段持续时间（秒）
     * 进入当前阶段后的累计时间。
     * 用于阶段内的定时动画和超时检测。
     */
    public float aiDialogPhaseDuration;

    /**
     * AI 对话阶段过渡混合因子 [0, 1]
     * 在对话阶段切换时从 0 平滑过渡到 1，
     * 用于表情和姿态的平滑过渡。
     */
    public float aiPhaseBlend;

    // ==================== AI 情感 ====================

    /**
     * AI 当前情感状态
     * 由语义分析和任务结果驱动。
     * 影响表情、动作、语气和思维气泡。
     */
    public AIEmotion aiEmotion = AIEmotion.NEUTRAL;

    /**
     * AI 情感强度 [0, 1]
     * 0 = 情感微弱（细微表现），1 = 情感强烈（夸张表现）。
     * 影响对应情感动画的幅度和时长。
     */
    public float aiEmotionIntensity;

    /**
     * AI 情感过渡混合因子 [0, 1]
     * 在情感状态切换时从 0 平滑过渡到 1，
     * 避免表情突变造成的不自然感。
     */
    public float aiEmotionBlend;

    // ==================== AI 任务 ====================

    /**
     * AI 当前任务状态
     * 表示 AI 正在执行的具体任务类型。
     * 影响胸部显示、手臂姿态和特殊动画效果。
     */
    public AITaskState aiTaskState = AITaskState.NONE;

    /**
     * AI 任务进度 [0, 1]
     * 0 = 任务开始，1 = 任务完成。
     * 用于下载、导航等有明确进度的任务。
     * 无进度的任务（如搜索）保持为 0。
     */
    public float aiTaskProgress;

    // ==================== 思维气泡 ====================

    /**
     * 思维气泡类型
     * 控制机器人头顶漫画风格气泡的内容。
     * 由 AI 对话阶段和情感状态自动驱动。
     */
    public ThoughtBubbleType thoughtBubbleType = ThoughtBubbleType.NONE;

    /**
     * 思维气泡透明度 [0, 1]
     * 0 = 完全透明（不可见），1 = 完全不透明。
     * 用于气泡的淡入淡出动画。
     */
    public float thoughtBubbleAlpha;

    /**
     * 思维气泡内容动画进度 [0, 1]
     * 驱动气泡内部内容的动画：
     * - DOTS：控制三个点的跳动相位
     * - LOADING：控制旋转角度
     * - MUSIC_NOTE：控制音符上浮和摆动
     */
    public float thoughtBubbleProgress;

    // ==================== 语音 ====================

    /**
     * TTS 语音振幅 [0, 1]
     * 当前 TTS 输出的音频振幅，用于嘴巴开合同步。
     * 0 = 静音（嘴巴闭合），1 = 最大音量（嘴巴最大张开）。
     * 由音频分析模块每帧更新。
     */
    public float ttsAmplitude;

    /**
     * 声源方向（度）
     * 检测到的声音来源方向角，用于驱动耳朵和头部朝向。
     * 0 = 正前方，正值 = 右侧，负值 = 左侧。
     * 范围 [-180, 180]。
     */
    public float voiceDirection;

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

    // ╔══════════════════════════════════════════════════════════════╗
    // ║                        构 造 函 数                           ║
    // ╚══════════════════════════════════════════════════════════════╝

    /**
     * 默认构造函数
     *
     * 初始化为居中、无旋转、闲置表情、眼睛完全睁开、
     * 瞳孔正常大小、中等发光强度的默认静止状态。
     * 所有枚举字段设为各自的默认值，所有数值字段设为零或安全默认值。
     */
    public RobotState() {
        // --- 身体位置 ---
        bodyX = 0f;
        bodyY = 0f;
        rotation = 0f;
        screenWidth = 0f;
        screenHeight = 0f;

        // --- 表情系统 ---
        expression = Expression.IDLE;
        expressionBlend = 0f;

        // --- 耳朵 ---
        leftEarAngle = 0f;
        rightEarAngle = 0f;

        // --- 眼睛 ---
        eyePupilOffsetX = 0f;
        eyePupilOffsetY = 0f;
        eyeOpenness = 1.0f;
        pupilDilation = 1.0f;

        // --- 眼睛特效 ---
        eyeSpecial = EyeSpecial.NONE;
        eyeSpecialIntensity = 0f;
        sleepiness = 0f;

        // --- 眉毛 ---
        eyebrowState = EyebrowState.NEUTRAL;
        leftEyebrowAngle = 0f;
        rightEyebrowAngle = 0f;
        eyebrowBlend = 0f;

        // --- 嘴巴 ---
        mouthShape = MouthShape.NEUTRAL;
        mouthBlend = 0f;
        mouthOpenness = 0f;

        // --- 手臂 ---
        armPose = ArmPose.IDLE_SIDE;
        armTransitionBlend = 0f;
        leftArmAngleOffset = 0f;
        rightArmAngleOffset = 0f;

        // --- 身体动作 ---
        bodyAction = BodyAction.NONE;
        bodyActionProgress = 0f;
        bodyActionIntensity = 1.0f;

        // --- 尾巴初始角度和角速度全为 0（自然下垂） ---
        for (int i = 0; i < 6; i++) {
            tailAngles[i] = 0f;
            tailAngularVel[i] = 0f;
        }

        // --- 呼吸/发光脉冲 ---
        glowIntensity = 0.6f;
        glowPulsePhase = 0f;
        bodyScale = 1.0f;

        // --- 天线 ---
        antennaGlowPhase = 0f;
        antennaFlashing = false;

        // --- 指示灯 ---
        indicatorState = IndicatorState.NORMAL;

        // --- 胸部显示 ---
        chestMode = ChestMode.NORMAL;
        chestTaskProgress = 0f;

        // --- 速度与 GPS ---
        speedKmh = 0f;
        speedAcceleration = 0f;
        bearing = 0f;
        speedZone = SpeedZone.PARKED;
        gpsAvailable = false;

        // --- 驾驶场景 ---
        drivingMode = DrivingMode.PARKED;
        idleDuration = 0f;
        drivingDuration = 0f;
        isCharging = false;
        hourOfDay = 0;

        // --- AI 连接 ---
        aiConnectionState = AIConnectionState.DISCONNECTED;
        aiReconnectAttempts = 0;

        // --- AI 对话 ---
        aiDialogPhase = AIDialogPhase.IDLE;
        aiDialogPhaseDuration = 0f;
        aiPhaseBlend = 0f;

        // --- AI 情感 ---
        aiEmotion = AIEmotion.NEUTRAL;
        aiEmotionIntensity = 0f;
        aiEmotionBlend = 0f;

        // --- AI 任务 ---
        aiTaskState = AITaskState.NONE;
        aiTaskProgress = 0f;

        // --- 思维气泡 ---
        thoughtBubbleType = ThoughtBubbleType.NONE;
        thoughtBubbleAlpha = 0f;
        thoughtBubbleProgress = 0f;

        // --- 语音 ---
        ttsAmplitude = 0f;
        voiceDirection = 0f;

        // --- 计时器 ---
        idleTimer = 0f;
        blinkTimer = 3.0f;
        earTwitchTimer = 5.0f;

        // --- 呼噜与粒子 ---
        purringAmplitude = 0f;
        particleBurstRequest = 0;

        // --- 传感器输入 ---
        lateralForce = 0f;
        longitudinalForce = 0f;
        yawRate = 0f;
    }
}

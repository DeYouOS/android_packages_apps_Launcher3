package com.android.launcher3.robot;

/**
 * 逐部件动画优先级仲裁管理器
 *
 * <h2>核心职责</h2>
 * 解决机器人多状态并发时的动画冲突问题。当多个子系统（安全系统、AI 指令、
 * 驾驶反应、速度氛围、场景、闲置动画）同时想控制同一个部件时，
 * 本管理器通过优先级层系统确保高优先级动画始终优先显示。
 *
 * <h2>优先级体系（P0 最高 → P5 最低）</h2>
 * <ul>
 *   <li>P0 SAFETY — 危险车速 / 紧急制动 / 碰撞预警，不可被覆盖</li>
 *   <li>P1 AI_COMMAND — 用户触发的 AI 交互，仅 P0 可中断</li>
 *   <li>P2 DRIVING_REACT — 传感器驱动的实时响应：过弯/加速/颠簸</li>
 *   <li>P3 SPEED_AMBIENT — 速度区间基础氛围：巡航/高速/专注</li>
 *   <li>P4 SCENE — 时间/环境类：困倦/充电/启动</li>
 *   <li>P5 IDLE — 随机闲置动画：挥手/摇头/环顾四周</li>
 * </ul>
 *
 * <h2>架构设计</h2>
 * 内部维护一个 [9 部件][6 优先级] 的二维数组 {@code layerValues}，
 * 以及一个 [9 部件] 的当前激活优先级数组 {@code activePriority}。
 * 每个部件的每一层都可以独立 request / release，release 后自动回退
 * 到下一个最高优先级的活跃层。
 *
 * <h2>线程安全</h2>
 * 本类的所有公共方法均通过 synchronized 保护，可在多线程环境中安全调用。
 * 建议在物理引擎线程中统一调用 request / release，渲染线程通过
 * getEffectiveValue 读取当前值。
 */
public class AnimationPriorityManager {

    // ==================== 优先级常量 ====================

    /** P0 安全优先级：危险车速 / 紧急制动 / 碰撞预警，不可被覆盖 */
    public static final int PRIORITY_SAFETY = 0;

    /** P1 AI 指令优先级：用户触发的 AI 交互，仅 P0 可中断 */
    public static final int PRIORITY_AI_COMMAND = 1;

    /** P2 驾驶反应优先级：传感器驱动的实时响应（过弯/加速/颠簸） */
    public static final int PRIORITY_DRIVING_REACT = 2;

    /** P3 速度氛围优先级：速度区间基础氛围（巡航/高速/专注） */
    public static final int PRIORITY_SPEED_AMBIENT = 3;

    /** P4 场景优先级：时间/环境类动画（困倦/充电/启动） */
    public static final int PRIORITY_SCENE = 4;

    /** P5 闲置优先级：随机闲置动画（挥手/摇头/环顾四周） */
    public static final int PRIORITY_IDLE = 5;

    /** 优先级层数总计 */
    private static final int PRIORITY_COUNT = 6;

    // ==================== 部件常量 ====================

    /** 手臂部件索引 */
    public static final int PART_ARM = 0;

    /** 嘴巴部件索引 */
    public static final int PART_MOUTH = 1;

    /** 眉毛部件索引 */
    public static final int PART_EYEBROW = 2;

    /** 眼睛部件索引 */
    public static final int PART_EYE = 3;

    /** 身体部件索引 */
    public static final int PART_BODY = 4;

    /** 天线部件索引 */
    public static final int PART_ANTENNA = 5;

    /** 指示灯部件索引 */
    public static final int PART_INDICATOR = 6;

    /** 胸部/前胸板部件索引 */
    public static final int PART_CHEST = 7;

    /** 气泡/对话框部件索引 */
    public static final int PART_BUBBLE = 8;

    /** 部件总数 */
    public static final int PART_COUNT = 9;

    // ==================== 内部状态 ====================

    /**
     * 各部件各优先级层的值存储
     * 第一维：部件索引 (0~8)
     * 第二维：优先级层索引 (0~5)
     * null 表示该层未被占用
     */
    private final Object[][] layerValues;

    /**
     * 各部件当前激活的优先级层
     * -1 表示该部件当前没有任何活跃请求
     */
    private final int[] activePriority;

    /**
     * 构造函数
     *
     * 初始化 9 部件 × 6 优先级的空层数组，所有部件初始状态无活跃请求。
     */
    public AnimationPriorityManager() {
        layerValues = new Object[PART_COUNT][PRIORITY_COUNT];
        activePriority = new int[PART_COUNT];
        // 初始化所有部件的激活优先级为 -1（无活跃请求）
        for (int i = 0; i < PART_COUNT; i++) {
            activePriority[i] = -1;
        }
    }

    /**
     * 请求设置指定部件在给定优先级层的动画值
     *
     * 将值写入对应层，并在该优先级 ≤ 当前激活优先级（更高或相同）时
     * 立即生效。如果当前已有更高优先级（数值更小）占用，则值会被
     * 记录但不立即生效，等高优先级释放后自动回退到此层。
     *
     * @param part     部件索引，使用 PART_* 常量
     * @param priority 优先级层，使用 PRIORITY_* 常量 (0~5)
     * @param value    要设置的动画值，不能为 null
     * @return true 如果该值立即成为部件的有效值（即当前最高优先级），false 表示被更高优先级遮挡
     */
    public synchronized boolean request(int part, int priority, Object value) {
        // 参数校验
        if (part < 0 || part >= PART_COUNT) {
            return false;
        }
        if (priority < 0 || priority >= PRIORITY_COUNT) {
            return false;
        }
        if (value == null) {
            return false;
        }

        // 将值写入对应的层
        layerValues[part][priority] = value;

        // 判断是否需要更新激活优先级
        // 如果当前无活跃层，或新请求的优先级更高（数值更小或相等），则更新
        if (activePriority[part] < 0 || priority <= activePriority[part]) {
            activePriority[part] = priority;
            return true;
        }

        // 值已记录，但被更高优先级遮挡，暂不生效
        return false;
    }

    /**
     * 释放指定部件在给定优先级层的锁
     *
     * 清除该层的值，如果释放的恰好是当前激活层，则自动回退
     * 到下一个最高优先级的活跃层。
     *
     * @param part     部件索引，使用 PART_* 常量
     * @param priority 优先级层，使用 PRIORITY_* 常量 (0~5)
     */
    public synchronized void release(int part, int priority) {
        // 参数校验
        if (part < 0 || part >= PART_COUNT) {
            return;
        }
        if (priority < 0 || priority >= PRIORITY_COUNT) {
            return;
        }

        // 清除该层的值
        layerValues[part][priority] = null;

        // 如果释放的是当前激活层，需要重新计算激活优先级
        if (activePriority[part] == priority) {
            recalculateActivePriority(part);
        }
    }

    /**
     * 释放所有部件在给定优先级层的锁
     *
     * 当某个状态结束时（如驾驶反应结束），一次性释放该优先级层
     * 在所有部件上的占用，每个被释放的部件都会自动回退。
     *
     * @param priority 优先级层，使用 PRIORITY_* 常量 (0~5)
     */
    public synchronized void releaseAll(int priority) {
        if (priority < 0 || priority >= PRIORITY_COUNT) {
            return;
        }
        for (int part = 0; part < PART_COUNT; part++) {
            if (layerValues[part][priority] != null) {
                layerValues[part][priority] = null;
                // 如果释放的是该部件的当前激活层，重新计算
                if (activePriority[part] == priority) {
                    recalculateActivePriority(part);
                }
            }
        }
    }

    /**
     * 获取指定部件当前的有效动画值
     *
     * 返回当前最高优先级活跃层的值。如果没有任何活跃层，返回 null。
     *
     * @param part 部件索引，使用 PART_* 常量
     * @return 当前有效值，如果无活跃请求则返回 null
     */
    public synchronized Object getEffectiveValue(int part) {
        if (part < 0 || part >= PART_COUNT) {
            return null;
        }
        int prio = activePriority[part];
        if (prio < 0) {
            return null;
        }
        return layerValues[part][prio];
    }

    /**
     * 获取指定部件当前的激活优先级
     *
     * @param part 部件索引，使用 PART_* 常量
     * @return 当前激活优先级层 (0~5)，如果无活跃请求返回 -1
     */
    public synchronized int getActivePriority(int part) {
        if (part < 0 || part >= PART_COUNT) {
            return -1;
        }
        return activePriority[part];
    }

    /**
     * 重新计算指定部件的激活优先级
     *
     * 从最高优先级（P0）到最低优先级（P5）遍历，找到第一个非空层
     * 作为新的激活层。如果所有层都为空，设置为 -1。
     *
     * @param part 部件索引
     */
    private void recalculateActivePriority(int part) {
        // 从最高优先级（数值最小）开始搜索
        for (int p = 0; p < PRIORITY_COUNT; p++) {
            if (layerValues[part][p] != null) {
                activePriority[part] = p;
                return;
            }
        }
        // 所有层都为空，标记为无活跃请求
        activePriority[part] = -1;
    }
}

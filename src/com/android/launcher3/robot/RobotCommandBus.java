package com.android.launcher3.robot;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

/**
 * 机器人命令总线（单例）
 *
 * 基于 LiveData 实现的发布-订阅命令总线。
 * 任意线程可通过 postCommand() 发送命令，
 * 观察者在主线程收到回调（LiveData 自动切线程）。
 *
 * 使用双重检查锁定（DCL）实现线程安全的懒加载单例。
 *
 * 使用示例：
 * - 发送命令：RobotCommandBus.getInstance().postCommand(new RobotCommand.ShowInfo(...))
 * - 监听命令：RobotCommandBus.getInstance().observe(this, cmd -> handleCommand(cmd))
 */
public class RobotCommandBus {

    /** 单例实例，volatile 保证多线程可见性和禁止指令重排序 */
    private static volatile RobotCommandBus sInstance;

    /** 可变 LiveData，承载命令事件 */
    private final MutableLiveData<RobotCommand> mCommandLiveData;

    /**
     * 私有构造函数（单例模式）
     */
    private RobotCommandBus() {
        mCommandLiveData = new MutableLiveData<>();
    }

    /**
     * 获取单例实例
     *
     * 使用双重检查锁定（DCL）保证：
     * 1. 线程安全（synchronized 块）
     * 2. 高性能（仅首次创建时同步）
     * 3. 正确性（volatile 禁止 JVM 指令重排序）
     *
     * @return 命令总线单例
     */
    public static RobotCommandBus getInstance() {
        if (sInstance == null) {
            synchronized (RobotCommandBus.class) {
                if (sInstance == null) {
                    sInstance = new RobotCommandBus();
                }
            }
        }
        return sInstance;
    }

    /**
     * 发送命令（线程安全）
     *
     * 使用 LiveData.postValue() 实现跨线程投递，
     * 命令最终在主线程被分发给所有观察者。
     *
     * 注意：如果短时间内多次调用，LiveData 只保证最后一个值被分发。
     * 如需保证每条命令都被处理，需使用自定义 Event 包装。
     *
     * @param command 要发送的命令
     */
    public void postCommand(RobotCommand command) {
        mCommandLiveData.postValue(command);
    }

    /**
     * 注册命令观察者（生命周期感知）
     *
     * 绑定到 LifecycleOwner 的生命周期，在 DESTROYED 状态自动移除观察者，
     * 避免内存泄漏。回调在主线程执行。
     *
     * @param owner    生命周期所有者（Activity / Fragment）
     * @param observer 命令观察者回调
     */
    public void observe(LifecycleOwner owner, Observer<RobotCommand> observer) {
        mCommandLiveData.observe(owner, observer);
    }

    /**
     * 注册永久命令观察者（无生命周期绑定）
     *
     * 适用于调用方不是 LifecycleOwner 的场景（如 Launcher Activity）。
     * 调用方必须在适当时机手动调用 removeObserver() 避免泄漏。
     *
     * @param observer 命令观察者回调
     */
    public void observeForever(Observer<RobotCommand> observer) {
        mCommandLiveData.observeForever(observer);
    }

    /**
     * 移除命令观察者
     *
     * 手动移除通过 observeForever() 注册的观察者，防止内存泄漏。
     *
     * @param observer 要移除的观察者
     */
    public void removeObserver(Observer<RobotCommand> observer) {
        mCommandLiveData.removeObserver(observer);
    }
}

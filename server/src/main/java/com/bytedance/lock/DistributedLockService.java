package com.bytedance.lock;

import com.bytedance.exception.BizException;
import com.bytedance.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用分布式锁服务
 * 基于 Redisson 实现，支持 tryLock + Supplier 模式
 */
@Component
@Slf4j
public class DistributedLockService {

    private final RedissonClient redissonClient;

    @Autowired
    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 尝试获取锁并执行业务逻辑
     * @param lockKey 锁的 key
     * @param waitTime 等待获取锁的最大时间（秒）
     * @param leaseTime 持锁最大时间（秒），防止死锁
     * @param supplier 业务逻辑
     * @return 业务逻辑返回值
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                throw new BizException(ErrorCode.CONCURRENT_OPERATION);
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.CONCURRENT_OPERATION);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 简化版：默认等待 3 秒，持锁 10 秒
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, 3, 10, supplier);
    }

    /**
     * 无返回值版本
     */
    public void executeWithLock(String lockKey, Runnable runnable) {
        executeWithLock(lockKey, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 非阻塞尝试获取锁并执行（用于定时任务）
     * waitTime=0：获取不到立即返回 false，不等待
     * 多实例部署时，只有一个实例能获取到锁执行定时任务
     *
     * @param lockKey   锁 key
     * @param leaseTime 持锁最大时间（秒）
     * @param runnable  业务逻辑
     * @return true=获取到锁并执行完成，false=未获取到锁（另一个实例在执行）
     */
    public boolean tryExecuteWithLock(String lockKey, long leaseTime, Runnable runnable) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, leaseTime, TimeUnit.SECONDS);
            if (!acquired) {
                return false;
            }
            runnable.run();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

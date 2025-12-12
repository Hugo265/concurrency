package com.example.demo.demos.concurrency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    /**
     * 方案一：悲观锁实现 (推荐用于高并发写)
     * 特点：串行执行，绝对安全，但慢。
     * 必须加事务，否则锁不住！
     */
    @Transactional
    public void addViewCountPessimistic(Long id) {
        // 1. 这一步会加上行锁 (For Update)，其他线程会卡在这里等待
        Post post = postRepository.findByIdWithPessimisticLock(id).orElseThrow(() -> new RuntimeException("帖子不存在"));

        // 2. 修改数据
        post.setViewCount(post.getViewCount() + 1);

        // 3. 保存 (事务提交时才释放锁)
        postRepository.save(post);
    }

    /**
     * 方案二：乐观锁实现 (演示用)
     * 特点：不加锁，并发高时会抛异常，需要自己写重试逻辑。
     */
    public void addViewCountOptimistic(Long id) {
        int maxRetries = 5; // 最大重试5次
        for (int i = 0; i < maxRetries; i++) {
            try {
                // 1. 普通查询
                Post post = postRepository.findById(id).orElseThrow(() -> new RuntimeException("帖子不存在"));

                // 2. 修改
                post.setViewCount(post.getViewCount() + 1);

                // 3. 保存 (JPA对比版本号，不对则抛异常)
                postRepository.save(post);
                return; // 成功则退出
            } catch (ObjectOptimisticLockingFailureException e) {
                // 捕获异常，进入下一次循环重试
                // System.out.println("版本冲突，正在重试... 线程:" + Thread.currentThread().getName());
            }
        }
        // 如果5次都失败，就放弃，或者记录日志
        // System.err.println("重试失败，丢失一次更新");
    }
}
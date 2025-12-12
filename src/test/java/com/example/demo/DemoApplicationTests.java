package com.example.demo;

import com.example.demo.demos.concurrency.PostRepository;
import com.example.demo.demos.concurrency.Post;
import com.example.demo.demos.concurrency.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@SpringBootTest
class PostServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;
    // 压测配置：200 线程，10万请求，分散到 1000 个热点数据上
    private static final int THREAD_COUNT = 200;
    private static final int TOTAL_REQUESTS = 100000;
    private static final int POST_COUNT = 10;

    @Test
    void testRandomAccess() throws InterruptedException {
        // =============================================================
        // Phase 1: 数据初始化 (修复了 ID 不匹配的 Bug)
        // =============================================================
        System.out.println("====== [Init] 正在清理旧数据并初始化 " + POST_COUNT + " 条新数据 ======");

        // 1. 清理旧数据
        postRepository.deleteAll();

        // 2. 构造数据对象
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < POST_COUNT; i++) {
            Post p = new Post();
            p.setTitle("Benchmark-Post-" + i);
            p.setViewCount(0);
            p.setVersion(0); // 乐观锁版本号初始为0
            posts.add(p);
        }

        // 3. 批量保存，并捕获回填了 ID 的实体列表
        List<Post> savedPosts = postRepository.saveAll(posts);

        // 4. 【关键】收集真实的 ID 列表
        // 数据库自增 ID 可能不是从 1 开始的，必须用真实 ID
        List<Long> realPostIds = savedPosts.stream()
                .map(Post::getId)
                .collect(Collectors.toList());

        System.out.println("====== [Init] 数据初始化完成 ======");
        System.out.println("ID 范围示例: " + realPostIds.get(0) + " ~ " + realPostIds.get(realPostIds.size() - 1));


        // =============================================================
        // Phase 2: 并发压测环境准备
        // =============================================================
        // 线程池：模拟 200 个并发用户
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        // 闭锁：用于阻塞主线程，直到 10万次请求全部执行完毕
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

        System.out.println("====== [Run] 压测开始: 目标 " + TOTAL_REQUESTS + " 次请求 ======");
        long start = System.currentTimeMillis();


        // =============================================================
        // Phase 3: 发起高并发随机访问
        // =============================================================
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executorService.execute(() -> {
                try {
                    // 1. 从真实 ID 列表中随机取一个 (模拟用户随机点击)
                    // ThreadLocalRandom 在多线程下性能优于 Random
                    int randomIndex = ThreadLocalRandom.current().nextInt(realPostIds.size());
                    Long targetId = realPostIds.get(randomIndex);

                    // 2. 执行业务逻辑 (切换注释测试不同锁)
                    // -------------------------------------------

                    // 方案 A: 乐观锁 (分散热点下性能极佳)
                    postService.addViewCountOptimistic(targetId);

                    // 方案 B: 悲观锁 (稳定但较慢)
                    // postService.addViewCountPessimistic(targetId);

                    // -------------------------------------------

                } catch (Exception e) {
                    // 建议打印简单的错误标记，防止控制台刷屏，同时确认是否有异常抛出
                    // System.err.print("E");
                } finally {
                    // 3. 任务完成 (无论成功失败)，计数器减 1
                    latch.countDown();
                }
            });
        }


        // =============================================================
        // Phase 4: 等待与结果验证
        // =============================================================
        // 主线程在此等待，直到计数器归零
        latch.await();
        long end = System.currentTimeMillis();

        // 关闭线程池
        executorService.shutdown();

        // 查询数据库汇总结果
        Long totalViews = postRepository.sumTotalViewCount();
        if (totalViews == null) totalViews = 0L;

        // 计算统计指标
        long duration = end - start;
        long lostData = TOTAL_REQUESTS - totalViews;
        double tps = (double) TOTAL_REQUESTS / (duration / 1000.0);
        double lossRate = (double) lostData / TOTAL_REQUESTS * 100;

        System.out.println("\n====== [Result] 压测报告 ======");
        System.out.println("并发线程数 : " + THREAD_COUNT);
        System.out.println("总耗时     : " + duration + " ms");
        System.out.println("系统吞吐量 : " + String.format("%.2f", tps) + " Req/sec");
        System.out.println("--------------------------------");
        System.out.println("预期总浏览量: " + TOTAL_REQUESTS);
        System.out.println("实际总浏览量: " + totalViews);
        System.out.println("数据丢失数  : " + lostData);
        System.out.println("数据丢失率  : " + String.format("%.2f%%", lossRate));
        System.out.println("================================");
    }

    @Test
    void testRedisPerformance() throws InterruptedException {
        // =============================================================
        // Phase 1: 数据初始化
        // =============================================================
        System.out.println("====== [Init] 初始化数据库与 Redis 数据 ======");

        // 1. 还是先准备数据库数据（为了拿到真实的 ID）
        postRepository.deleteAll();
        // 清理 Redis 旧缓存 (模糊删除 post:view:*)
        // 注意：生产环境慎用 keys *，这里仅供测试
        java.util.Set<String> keys = redisTemplate.keys("post:view:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 2. 创建 1000 个帖子
        List<Post> posts = new java.util.ArrayList<>();
        for (int i = 0; i < POST_COUNT; i++) {
            Post p = new Post();
            p.setTitle("Redis-Post-" + i);
            p.setViewCount(0);
            p.setVersion(0);
            posts.add(p);
        }
        List<Post> savedPosts = postRepository.saveAll(posts);

        // 3. 收集真实 ID
        List<Long> realPostIds = savedPosts.stream().map(Post::getId).collect(Collectors.toList());
        System.out.println("数据初始化完成，Redis 已清空。");


        // =============================================================
        // Phase 2: 压测准备
        // =============================================================
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

        System.out.println("====== [Run] Redis 压测开始: " + TOTAL_REQUESTS + " 次请求 ======");
        long start = System.currentTimeMillis();

        // =============================================================
        // Phase 3: 发起攻击 (目标：Redis)
        // =============================================================
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executorService.execute(() -> {
                try {
                    int randomIndex = ThreadLocalRandom.current().nextInt(realPostIds.size());
                    Long targetId = realPostIds.get(randomIndex);

                    // 【核心】调用 Redis 原子递增
                    postService.addViewCountRedis(targetId);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long end = System.currentTimeMillis();
        executorService.shutdown();

        // =============================================================
        // Phase 4: 结果验证 (从 Redis 统计)
        // =============================================================
        long duration = end - start;
        double qps = (double) TOTAL_REQUESTS / (duration / 1000.0);

        // 统计 Redis 里所有 key 的总数
        // 注意：这里用简单粗暴的方式遍历求和，仅限测试用
        long totalViewsInRedis = 0;
        for (Long id : realPostIds) {
            totalViewsInRedis += postService.getViewCountFromRedis(id);
        }

        System.out.println("\n====== [Result] Redis 压测报告 ======");
        System.out.println("并发线程数 : " + THREAD_COUNT);
        System.out.println("总耗时     : " + duration + " ms");
        System.out.println("系统 QPS   : " + String.format("%.2f", qps) + " (次/秒)");
        System.out.println("--------------------------------");
        System.out.println("预期总浏览量: " + TOTAL_REQUESTS);
        System.out.println("Redis总浏览量: " + totalViewsInRedis);
        System.out.println("数据丢失数  : " + (TOTAL_REQUESTS - totalViewsInRedis));
        System.out.println("================================");
    }
}
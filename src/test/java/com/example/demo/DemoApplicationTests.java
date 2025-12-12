package com.example.demo;

import com.example.demo.demos.concurrency.PostRepository;
import com.example.demo.demos.concurrency.Post;
import com.example.demo.demos.concurrency.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class PostServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private PostRepository postRepository;

    // 压测配置：100个线程，总共请求 10000 次
    // 如果你电脑性能好，可以改成 200 线程，10万次请求
    private static final int THREAD_COUNT = 100;
    private static final int TOTAL_REQUESTS = 10000;

    @Test
    void testdemo() throws InterruptedException {
        Long postId = 1L;

        // 1. 初始化数据：如果没有ID=1的帖子，创建一个
        Post post = postRepository.findById(postId).orElse(new Post());
        if (post.getId() == null) {
            post.setTitle("Java高并发测试");
            post.setVersion(0);
        }
        post.setViewCount(0); // 归零，方便统计
        postRepository.save(post);

        // 2. 准备线程池
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        // 闭锁：用于让主线程等待所有子线程跑完
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);

        System.out.println("====== 压测开始: 目标 " + TOTAL_REQUESTS + " 次点击 ======");
        long start = System.currentTimeMillis();

        // 3. 疯狂发送请求
        for (int i = 0; i < TOTAL_REQUESTS; i++) {
            executorService.execute(() -> {
                try {
                    // ==========================================
                    // TODO: 在这里切换你要测试的方法！
                    // ==========================================

                    // A. 测试悲观锁 (准确，但稍慢)
//                    postService.addViewCountPessimistic(postId);

                    // B. 测试乐观锁 (极快，但大量失败数据对不上)
                     postService.addViewCountOptimistic(postId);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown(); // 完成一个，计数器减1
                }
            });
        }

        // 4. 等待所有线程结束
        latch.await();
        long end = System.currentTimeMillis();
        executorService.shutdown();

        // 5. 验证结果
        Post result = postRepository.findById(postId).get();
        System.out.println("====== 压测结束 ======");
        System.out.println("耗时: " + (end - start) + "ms");
        System.out.println("理论浏览量: " + TOTAL_REQUESTS);
        System.out.println("实际浏览量: " + result.getViewCount());
        System.out.println("数据误差: " + (TOTAL_REQUESTS - result.getViewCount()));
    }
}
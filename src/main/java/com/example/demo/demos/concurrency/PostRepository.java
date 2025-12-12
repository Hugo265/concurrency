package com.example.demo.demos.concurrency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import javax.transaction.Transactional;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 悲观锁核心写法
     * LockModeType.PESSIMISTIC_WRITE 代表 SQL 里的 "FOR UPDATE"
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdWithPessimisticLock(@Param("id") Long id);

    /**
     * 新增：统计所有帖子的浏览量总和
     * 用于验证压测结果
     */
    @Query("select sum(p.viewCount) from Post p")
    Long sumTotalViewCount();

    // 这是一个灾难性的方法
    @Modifying
    @Transactional
    @Query("update Post p set p.viewCount = p.viewCount + 1 where p.postCode = :postCode")
    int updateViewCountByPostCode(@Param("postCode") String postCode);
}


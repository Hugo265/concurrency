package com.example.demo.demos.concurrency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    /**
     * 悲观锁核心写法
     * LockModeType.PESSIMISTIC_WRITE 代表 SQL 里的 "FOR UPDATE"
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdWithPessimisticLock(@Param("id") Long id);

}


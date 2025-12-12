package com.example.demo.demos.concurrency;

import lombok.Data;
import javax.persistence.*;

@Data
@Entity
@Table(name = "t_post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(name = "view_count")
    private Integer viewCount;

    /**
     * 乐观锁的核心！
     * JPA 会自动处理：UPDATE ... SET version = version+1 WHERE id = ? AND version = oldVersion
     */
    @Version
    private Integer version;
}
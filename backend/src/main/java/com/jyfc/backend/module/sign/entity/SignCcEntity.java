package com.jyfc.backend.module.sign.entity;

import jakarta.persistence.*;

/** 抄送。只用于「抄送我的」查询。 */
@Entity
@Table(name = "sign_cc")
public class SignCcEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public Long getTaskId() { return taskId; }
    public Long getUserId() { return userId; }
}

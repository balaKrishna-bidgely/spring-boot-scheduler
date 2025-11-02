package com.scheduler.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "templates")
public class Template {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 100)
    private String keyName;

    @Column(columnDefinition = "TEXT")
    private String content; // with placeholders like {{orderId}}

    // Getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}


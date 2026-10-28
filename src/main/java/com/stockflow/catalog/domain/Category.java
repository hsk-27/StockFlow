package com.stockflow.catalog.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
    }

    public Category(UUID id, String name, String slug, String description, Category parent) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.slug = slug.toLowerCase().trim();
        this.description = description;
        this.parent = parent;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getDescription() {
        return description;
    }

    public Category getParent() {
        return parent;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void update(String name, String description, Category parent) {
        this.name = name;
        this.description = description;
        this.parent = parent;
    }
}

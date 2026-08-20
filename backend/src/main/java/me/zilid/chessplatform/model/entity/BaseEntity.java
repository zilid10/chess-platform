package me.zilid.chessplatform.model.entity;


import io.github.robsonkades.uuidv7.UUIDv7;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.util.Objects;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = UUIDv7.randomUUID();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BaseEntity entity)) {
            return false;
        }
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

}

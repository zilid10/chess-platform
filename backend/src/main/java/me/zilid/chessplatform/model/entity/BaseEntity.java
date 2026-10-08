package me.zilid.chessplatform.model.entity;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@MappedSuperclass
public abstract class BaseEntity {
    private static final TimeBasedEpochGenerator uuidGenerator = Generators.timeBasedEpochGenerator();

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id = uuidGenerator.generate();

    // null until the entity is first saved, which is how Hibernate tells new entities apart
    @Version
    @Column(name = "version", nullable = false)
    private @Nullable Long version;

    @Override
    public boolean equals(@Nullable Object o) {
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

    public @Nullable Long getVersion() {
        return version;
    }
}

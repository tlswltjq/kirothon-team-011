package dev.kirothon.resume.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "anon_user")
public class AnonUser {
    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String nickname;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    /** 작업 12(복구 코드)용 자리. 지금은 항상 null. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "recovery_hash", length = 64, columnDefinition = "char(64)")
    private String recoveryHash;

    @Column(nullable = false)
    private boolean sample;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnonUser() {
    }

    public AnonUser(String nickname, String tokenHash) {
        this.id = UUID.randomUUID();
        this.nickname = nickname;
        this.tokenHash = tokenHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isSample() {
        return sample;
    }
}

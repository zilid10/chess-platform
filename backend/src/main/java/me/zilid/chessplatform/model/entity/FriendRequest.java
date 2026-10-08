package me.zilid.chessplatform.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "friend_request")
public class FriendRequest extends AuditedBaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", updatable = false, nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", updatable = false, nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status;

    protected FriendRequest() {}

    public FriendRequest(User sender, User recipient) {
        this.sender = sender;
        this.recipient = recipient;
        this.status = RequestStatus.PENDING;
    }

    public FriendRequest(User sender, User recipient, RequestStatus status) {
        this.sender = sender;
        this.recipient = recipient;
        this.status = status;
    }

    public User getSender() {
        return sender;
    }

    public User getRecipient() {
        return recipient;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public void setStatus(RequestStatus status) {
        this.status = status;
    }

    public enum RequestStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        CANCELLED
    }
}

package io.cresco.dashboard.models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table( name = "session" )
public class LoginSession {
    @Id
    private String id;

    @Column( name = "last_seen" )
    private Long lastSeen;

    @Column( name = "remember_me" )
    private Boolean remememberMe;

    @Column( name = "username" )
    private String username;

    public LoginSession() {
        this.id = java.util.UUID.randomUUID().toString();
        this.lastSeen = new Date().getTime();
        this.remememberMe = false;
    }

    public LoginSession(String username) {
        this();
        this.username = username;
    }

    public LoginSession(String username, Boolean remememberMe) {
        this(username);
        this.remememberMe = remememberMe;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public Long getLastSeen() {
        return lastSeen;
    }
    public Date getLastSeenAsDate() {
        return new Date(lastSeen);
    }
    public void setLastSeen(Long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Boolean getRemememberMe() {
        return remememberMe;
    }
    public void setRemememberMe(Boolean remememberMe) {
        this.remememberMe = remememberMe;
    }

    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
}

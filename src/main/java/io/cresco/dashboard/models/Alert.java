package io.cresco.dashboard.models;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table( name = "alerts")
public class Alert {
    @Id
    private String id;

    @Column( name = "created" )
    private Long created;

    @Column( name = "message" )
    private String message;

    public Alert() {
        this.id = java.util.UUID.randomUUID().toString();
        this.created = new Date().getTime();
    }

    public Alert(String message) {
        this();
        this.message = message;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public Long getCreated() {
        return created;
    }
    public Date getCreatedAtDate() {
        return new Date(created);
    }
    public void setCreated(Long created) {
        this.created = created;
    }

    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}

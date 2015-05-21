package org.neuinfo.resource.disambiguator.model;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

@Entity
@Table(name = "rd_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "login_id", length = 20, nullable = false, unique = true)
    private String loginId;

    @Column(length = 20)
    String password;

    @Column(length = 20)
    String role = "curator";

    @Column(length = 20)
    String email;

    @Column(name = "date_created")
    Calendar dateCreated = Calendar.getInstance();

    @OneToMany
    @JoinTable(name = "rd_user_registry",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "registry_id"))
    private Set<Registry> resources = new HashSet<Registry>(3);

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Calendar getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Calendar dateCreated) {
        this.dateCreated = dateCreated;
    }

    public long getId() {
        return id;
    }

    public Set<Registry> getResources() {
        return resources;
    }

    public void setResources(Set<Registry> resources) {
        this.resources = resources;
    }
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("User [id=");
        builder.append(id);
        builder.append(", ");
        if (loginId != null) {
            builder.append("loginId=");
            builder.append(loginId);
            builder.append(", ");
        }
        if (password != null) {
            builder.append("password=");
            builder.append(password);
            builder.append(", ");
        }
        if (role != null) {
            builder.append("role=");
            builder.append(role);
            builder.append(", ");
        }
        if (email != null) {
            builder.append("email=");
            builder.append(email);
            builder.append(", ");
        }
        if (dateCreated != null) {
            builder.append("dateCreated=");
            builder.append(dateCreated);
        }
        builder.append("]");
        return builder.toString();
    }


}

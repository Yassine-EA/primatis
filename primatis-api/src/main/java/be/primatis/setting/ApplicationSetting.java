package be.primatis.setting;

import be.primatis.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "application_setting")
public class ApplicationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "application_setting_seq")
    @SequenceGenerator(name = "application_setting_seq", sequenceName = "application_setting_seq", allocationSize = 1)
    @Column(name = "setting_id")
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 100, unique = true)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by_user_id")
    private AppUser updatedByUser;

    public ApplicationSetting() {
    }

    public Long getId() {
        return id;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AppUser getUpdatedByUser() {
        return updatedByUser;
    }

    public void setUpdatedByUser(AppUser updatedByUser) {
        this.updatedByUser = updatedByUser;
    }
}

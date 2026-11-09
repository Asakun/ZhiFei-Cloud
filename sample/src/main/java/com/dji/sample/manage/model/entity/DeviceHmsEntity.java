package com.dji.sample.manage.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author sean
 * @version 1.1
 * @date 2022/7/6
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "manage_device_hms")
@Entity
@Table(name = "manage_device_hms")
public class DeviceHmsEntity implements Serializable, Cloneable {

    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "hms_id" , nullable = false, length = 45, unique = true)
    @Comment("uuid")
    @TableField("hms_id")
    private String hmsId;

    @Column(name = "tid" , nullable = false, length = 45)
    @Comment("The tid when the device reports the hms message")
    @TableField("tid")
    private String tid;

    @Column(name = "bid" , nullable = false, length = 45)
    @Comment("The bid when the device reports the hms message")
    @TableField("bid")
    private String bid;

    @Column(name = "sn" , nullable = false, length = 45)
    @Comment("Which device reported the message")
    @TableField("sn")
    private String sn;

    @Column(name = "level" , nullable = false)
    @Comment("hms level. 0: notice; 1: caution; 2: warning")
    @TableField("level")
    private Integer level;

    @Column(name = "module" , nullable = false)
    @Comment("Which module's message. 0: flight task; 1:device manage; 2: media; 3: hms")
    @TableField("module")
    private Integer module;

    @Column(name = "hms_key" , nullable = false, length = 64)
    @Comment("The key of the hms message, according to which the message text is obtained")
    @TableField("hms_key")
    private String hmsKey;

    @Column(name = "message_zh" , nullable = false, length = 100)
    @Comment("Chinese message")
    @TableField("message_zh")
    private String messageZh;

    @Column(name = "message_en" , nullable = false, length = 300)
    @Comment("English message")
    @TableField("message_en")
    private String messageEn;

    @Column(name = "create_time" , nullable = false)
    @TableField("create_time")
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField("update_time")
    private Long updateTime;

    @Override
    public DeviceHmsEntity clone() {
        try {
            return (DeviceHmsEntity) super.clone();
        } catch (CloneNotSupportedException e) {
            return DeviceHmsEntity.builder()
                    .bid(this.getBid())
                    .tid(this.getTid())
                    .createTime(this.getCreateTime())
                    .updateTime(this.getUpdateTime())
                    .sn(this.getSn())
                    .build();
        }
    }
}

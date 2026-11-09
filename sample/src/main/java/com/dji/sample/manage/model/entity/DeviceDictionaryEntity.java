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
 *
 * @author sean.zhou
 * @date 2021/11/15
 * @version 0.1
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("manage_device_dictionary")
@Entity
@Table(name = "manage_device_dictionary")
public class DeviceDictionaryEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "domain" , nullable = false)
    @Comment("This parameter corresponds to the domain in the Product Type section of the document. 0: drone; 1: payload; 2: remote control; 3: dock;")
    @TableField(value = "domain")
    private Integer domain;

    @Column(name = "device_type" , nullable = false)
    @Comment("This parameter corresponds to the type in the Product Type section of the document")
    @TableField(value = "device_type")
    private Integer deviceType;

    @Column(name = "sub_type" , nullable = false)
    @Comment("This parameter corresponds to the sub_type in the Product Type section of the document")
    @TableField(value = "sub_type")
    private Integer subType;

    @Column(name = "device_name" , nullable = false, length = 32)
    @Comment("This parameter corresponds to the name in the Product Type section of the document")
    @TableField(value = "device_name")
    private String deviceName;

    @Column(name = "device_desc" , length = 100)
    @Comment("remark")
    @TableField(value = "device_desc")
    private String deviceDesc;

}
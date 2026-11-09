package com.dji.sample.manage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author sean
 * @version 1.2
 * @date 2022/9/8
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName("logs_file_index")
@Entity
@Table(name = "logs_file_index")
public class LogsFileIndexEntity implements Serializable {

    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "boot_index" , nullable = false)
    @Comment("The file index reported by the dock.")
    @TableField("boot_index")
    private Integer bootIndex;

    @Column(name = "file_id" , nullable = false, length = 64)
    @Comment("The file_id in the logs_file table.")
    @TableField("file_id")
    private String fileId;

    @Column(name = "start_time" , nullable = false)
    @Comment("The file start time reported by the dock.")
    @TableField("start_time")
    private Long startTime;

    @Column(name = "end_time" , nullable = false)
    @Comment("The file end time reported by the dock.")
    @TableField("end_time")
    private Long endTime;

    @Column(name = "size" , nullable = false)
    @Comment("The file size reported by the dock.")
    @TableField("size")
    private Long size;

    @Column(name = "device_sn" , nullable = false, length = 64)
    @Comment("The sn of the device.")
    @TableField("device_sn")
    private String deviceSn;

    @Column(name = "domain" , nullable = false)
    @Comment("This parameter corresponds to the domain in the device dictionary table.")
    @TableField("domain")
    private Integer domain;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}

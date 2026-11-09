package com.dji.sample.manage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("osd_dock_drone")
@Entity
@Table(name = "osd_dock_drone", indexes = {@Index(name = "idx_job_id", columnList = "job_id")})
public class OsdDockDroneEntity implements Serializable {

    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "job_id" , nullable = false, length = 45)
    @Comment("uuid")
    @TableField("job_id")
    private String jobId;

    @Column(name = "data" , nullable = false, length = 65536)
    @TableField("data")
    private String data;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;
}

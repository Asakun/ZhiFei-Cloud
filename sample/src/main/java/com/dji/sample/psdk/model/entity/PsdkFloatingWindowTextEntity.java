package com.dji.sample.psdk.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

@TableName("psdk_floating_window_text")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "psdk_floating_window_text", indexes = {
        @Index(name = "idx_job_id", columnList = "job_id"),
        @Index(name = "idx_psdk_index", columnList = "psdk_index")
})
public class PsdkFloatingWindowTextEntity implements Serializable {
    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "job_id" , nullable = false, length = 45)
    @Comment("uuid")
    @TableField("job_id")
    private String jobId;

    @Column(name = "latitude" , nullable = false)
    @TableField("latitude")
    private Float latitude;

    @Column(name = "longitude" , nullable = false)
    @TableField("longitude")
    private Float longitude;

    @Column(name = "height" , nullable = false)
    @TableField("height")
    private Float height;

    @Column(name = "psdk_index" , nullable = false)
    @TableField("psdk_index")
    private Integer psdkIndex;

    @Column(name = "psdk_name", length = 32, nullable = false)
    @TableField(value = "psdk_name")
    private String psdkName;

    @Column(name = "value" , nullable = false, length = 65536)
    @TableField("value")
    private String value;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

}

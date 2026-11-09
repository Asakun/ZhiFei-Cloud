package com.dji.sample.psdk.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

@TableName("psdk_widget")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "psdk_widget")
public class PsdkWidgetEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "payload_sn", length = 32, unique = true)
    @TableField(value = "payload_sn")
    private String payloadSn;

    @Column(name = "device_sn", length = 32, nullable = false)
    @TableField(value = "device_sn")
    private String deviceSn;

    @Column(name = "psdk_name", length = 32, nullable = false)
    @TableField(value = "psdk_name")
    private String psdkName;

    @Column(name = "psdk_version", length = 32)
    @TableField(value = "psdk_version")
    private String psdkVersion;

    @Column(name = "psdk_sn", length = 32)
    @TableField(value = "psdk_sn")
    private String psdkSn;

    @Column(name = "psdk_lib_version", length = 32)
    @TableField(value = "psdk_lib_version")
    private String psdkLibVersion;

    @Column(name = "psdk_index" , nullable = false)
    @TableField(value = "psdk_index")
    private Integer psdkIndex;

    @Column(name = "psdk_type")
    @TableField(value = "psdk_type")
    private Integer psdkType;

    @Column(name = "status" , nullable = false)
    @ColumnDefault("1")
    @Comment("1: in use; 0: not in use")
    @TableField(value = "status")
    private Integer status;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}

package com.dji.sample.manage.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

@TableName(value = "manage_workspace")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "manage_workspace")
public class WorkspaceEntity implements Serializable {
    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "workspace_id" , nullable = false, length = 64, unique = true)
    @Comment("uuid")
    @TableField(value = "workspace_id")
    private String workspaceId;

    @Column(name = "workspace_name" , nullable = false, length = 64)
    @Comment("The name of the workspace")
    @TableField(value = "workspace_name")
    private String workspaceName;

    @Column(name = "workspace_desc" , length = 100)
    @Comment("The description of the workspace")
    @TableField(value = "workspace_desc")
    private String workspaceDesc;

    @Column(name = "platform_name" , nullable = false, length = 64)
    @Comment("The platform name of the workspace")
    @TableField(value = "platform_name")
    private String platformName;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    @Column(name = "bind_code" , nullable = false, length = 32, unique = true)
    @Comment("The binding code for this workspace is required when the dock connects to a third-party cloud")
    @TableField(value = "bind_code")
    private String bindCode;

    public WorkspaceEntity(String workspaceId, String workspaceName, String workspaceDesc, String platformName, String bindCode) {
        this.platformName = platformName;
        this.workspaceDesc = workspaceDesc;
        this.workspaceName = workspaceName;
        this.workspaceId = workspaceId;
        this.bindCode = bindCode;
    }
}

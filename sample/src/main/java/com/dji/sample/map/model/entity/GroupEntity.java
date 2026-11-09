package com.dji.sample.map.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

/**
 * @author sean
 * @version 0.2
 * @date 2021/11/29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "map_group")
@Entity
@Table(name = "map_group")
public class GroupEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "group_id" , nullable = false, length = 64, unique = true)
    @Comment("uuid")
    @TableField("group_id")
    private String groupId;

    @Column(name = "group_name" , nullable = false, length = 64)
    @Comment("The name of the group.")
    @TableField("group_name")
    private String groupName;

    @Column(name = "group_type" , nullable = false)
    @Comment("The type of the group. 0: custome; 1: default; 2: app shared; see developer document for detail.")
    @TableField("group_type")
    private Integer groupType;

    @Column(name = "username", length = 64)
    @TableField("username")
    private String username;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @Comment("The workspace_id in the manage_workspace table.")
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "is_distributed", nullable = false)
    @ColumnDefault("1")
    @Comment("element group distributed status. Only data with value 1 is displayed on the pilot map. 1: true; 0: false.")
    @TableField("is_distributed")
    private Boolean isDistributed;

    @Column(name = "is_lock", nullable = false)
    @ColumnDefault("0")
    @Comment("Whether to lock. If locked, the elements under this element group cannot be deleted and modified. 1: locked; 0: unlock.")
    @TableField("is_lock")
    private Boolean isLock;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    public GroupEntity(String groupId, String groupName, Integer groupType, String workspaceId) {
        this.workspaceId = workspaceId;
        this.groupType = groupType;
        this.groupName = groupName;
        this.groupId = groupId;
    }
}

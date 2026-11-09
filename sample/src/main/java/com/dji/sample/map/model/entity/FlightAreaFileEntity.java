package com.dji.sample.map.model.entity;

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
 * @version 1.9
 * @date 2023/11/22
 */
@TableName("flight_area_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "flight_area_file")
public class FlightAreaFileEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "file_id" , nullable = false, length = 64, unique = true)
    @TableField("file_id")
    private String fileId;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "name" , nullable = false, length = 100)
    @TableField("name")
    private String name;

    @Column(name = "object_key" , nullable = false, length = 1000)
    @TableField("object_key")
    private String objectKey;

    @Column(name = "sign" , nullable = false, length = 64)
    @Comment("sha256")
    @TableField("sign")
    private String sign;

    @Column(name = "size" , nullable = false)
    @TableField("size")
    private Integer size;

    @Column(name = "latest" , nullable = false)
    @Comment("The latest version？")
    @TableField("latest")
    private Boolean latest;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}

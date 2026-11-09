package com.dji.sample.media.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.io.Serializable;

@TableName(value = "map_file")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "map_file")
public class MapFileEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "file_id" , nullable = false, length = 64, unique = true)
    @Comment("uuid")
    @TableField("file_id")
    private String fileId;

    @Column(name = "name" , nullable = false, length = 50)
    @TableField("name")
    private String name;

    @Column(name = "remark", length = 100)
    @TableField("remark")
    private String remark;

    @Column(name = "file_path" , nullable = false, length = 1000)
    @Comment("The path of the file")
    @TableField("file_path")
    private String filePath;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @Comment("The workspace to which the file belongs")
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "object_key" , nullable = false, length = 1000)
    @Comment("The key of the file in the bucket")
    @TableField("object_key")
    private String objectKey;

    @Column(name = "file_type" , nullable = false)
    @Comment("Map data file type. 0: image layer; 1: terrain layer; 2: 3d tileset")
    @TableField("file_type")
    private Integer fileType;

    @Column(name = "file_size")
    @Comment("file size byte")
    @TableField("file_size")
    private Long fileSize;

    @Column(name = "max_latitude")
    @Comment("The maximum latitude of the map data bounding box")
    @TableField("max_latitude")
    private Double maxLatitude;

    @Column(name = "min_latitude")
    @Comment("The minimum latitude of the map data bounding box")
    @TableField("min_latitude")
    private Double minLatitude;

    @Column(name = "max_longitude")
    @Comment("The maximum longitude of the map data bounding box")
    @TableField("max_longitude")
    private Double maxLongitude;

    @Column(name = "min_longitude")
    @Comment("The minimum longitude of the map data bounding box")
    @TableField("min_longitude")
    private Double minLongitude;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}


package com.dji.sample.media.model;

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
 * @date 2021/12/9
 */
@TableName(value = "media_file")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "media_file")
public class MediaFileEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "file_id" , nullable = false, length = 64, unique = true)
    @Comment("uuid")
    @TableField("file_id")
    private String fileId;

    @Column(name = "file_name" , nullable = false, length = 100)
    @Comment("The original name of the file")
    @TableField("file_name")
    private String fileName;

    @Column(name = "file_path" , nullable = false, length = 1000)
    @Comment("The path of the file")
    @TableField("file_path")
    private String filePath;

    @Column(name = "workspace_id" , nullable = false, length = 64)
    @Comment("The workspace to which the file belongs")
    @TableField("workspace_id")
    private String workspaceId;

    @Column(name = "fingerprint" , length = 64)
    @Comment("The fingerprint of the file. This property exists only for media files uploaded by Pilot")
    @TableField("fingerprint")
    private String fingerprint;

    @Column(name = "tinny_fingerprint" , length = 100)
    @Comment("The tiny fingerprint of the file. This property exists only for media files uploaded by Pilot")
    @TableField("tinny_fingerprint")
    private String tinnyFingerprint;

    @Column(name = "object_key" , nullable = false, length = 1000)
    @Comment("The key of the file in the bucket")
    @TableField("object_key")
    private String objectKey;

    @Column(name = "sub_file_type")
    @Comment("This property exists only for image files uploaded by Pilot. 0: normal picture; 1: panorama")
    @TableField("sub_file_type")
    private Integer subFileType;

    @Column(name = "is_original" , nullable = false)
    @Comment("Whether is the original image")
    @TableField("is_original")
    private Boolean isOriginal;

    @Column(name = "drone" , nullable = false, length = 32)
    @Comment("The sn of the drone which create the file")
    @ColumnDefault("undefined")
    @TableField("drone")
    private String drone;

    @Column(name = "payload" , nullable = false, length = 32)
    @Comment("The name of the drone payload which create the file")
    @ColumnDefault("undefined")
    @TableField("payload")
    private String payload;

    @Column(name = "job_id" , nullable = false, length = 64)
    @Comment("The job_id in the wayline_job table. Whether the file belongs to the dock task")
    @TableField("job_id")
    private String jobId;

    @Column(name = "create_time" , nullable = false)
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Long createTime;

    @Column(name = "update_time" , nullable = false)
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;
}


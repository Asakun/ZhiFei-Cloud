package com.dji.sample.wayline.model.entity;

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

@TableName("wayline_file_edit")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "wayline_file_edit")
public class WaylineFileEditEntity implements Serializable {
    private static final long serialVersionUID = -12L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Column(name = "wayline_id" , nullable = false, length = 45, unique = true)
    @Comment("uuid")
    @TableField("wayline_id")
    private String waylineId;

    @Column(name = "uav_route" , nullable = false, length = 65536)
    @TableField("uav_route")
    private String uavRoute;
}

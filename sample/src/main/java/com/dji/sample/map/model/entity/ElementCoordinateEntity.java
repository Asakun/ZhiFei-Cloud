package com.dji.sample.map.model.entity;

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

/**
 * @author sean
 * @version 0.2
 * @date 2021/11/29
 */
@TableName("map_element_coordinate")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "map_element_coordinate", indexes = {@Index(name = "idx_element_id", columnList = "element_id")})
public class ElementCoordinateEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Long id;

    @Column(name = "element_id" , nullable = false, length = 64)
    @Comment("uuid")
    @TableField("element_id")
    private String elementId;

    @Column(name = "longitude" , nullable = false)
    @TableField("longitude")
    private Double longitude;

    @Column(name = "latitude" , nullable = false)
    @TableField("latitude")
    private Double latitude;

    @Column(name = "altitude")
    @TableField("altitude")
    private Double altitude;

}

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
 * @version 1.9
 * @date 2023/11/21
 */
@TableName("flight_area_property")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "flight_area_property")
public class FlightAreaPropertyEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(type = IdType.AUTO)
    private Integer id;

    @Column(name = "element_id" , nullable = false, length = 64, unique = true)
    @TableField("element_id")
    private String elementId;

    @Column(name = "type" , nullable = false, length = 32)
    @Comment("dfence/nfz")
    @TableField("type")
    private String type;

    @Column(name = "enable", nullable = false)
    @TableField("enable")
    private Boolean enable;

    @Column(name = "sub_type" , length = 32)
    @Comment("options: Circle")
    @TableField("sub_type")
    private String subType;

    @Column(name = "radius", nullable = false)
    @ColumnDefault("0")
    @Comment("unit: cm")
    @TableField("radius")
    private Integer radius;

}

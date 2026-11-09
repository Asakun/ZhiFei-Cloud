package com.dji.sample.psdk.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import javax.persistence.*;

@TableName("psdk_widget_value_set")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "psdk_widget_value_set")
public class PsdkWidgetValueSetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @Column(name = "name", length = 32, nullable = false)
    @TableField(value = "name")
    private String name;

    @Column(name = "sn", length = 32)
    @TableField(value = "sn")
    private String sn;

    @Column(name = "psdk_index" , nullable = false)
    @TableField(value = "psdk_index")
    private Integer psdkIndex;

    @Column(name = "index" , nullable = false)
    @TableField(value = "`index`")
    private Integer index;

    @Column(name = "value" , nullable = false)
    @TableField(value = "`value`")
    private Integer value;

    @Column(name = "action", length = 32)
    @TableField(value = "action")
    private String action;

    @Column(name = "remark", length = 64)
    @TableField(value = "remark")
    private String remark;

    @Column(name = "visible", nullable = false)
    @ColumnDefault("1")
    @TableField(value = "visible")
    private Integer visible;
}

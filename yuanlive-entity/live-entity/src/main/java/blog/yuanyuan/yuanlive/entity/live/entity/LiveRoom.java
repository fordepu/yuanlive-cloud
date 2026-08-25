package blog.yuanyuan.yuanlive.entity.live.entity;


import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import me.ahoo.cosid.annotation.CosId;

@Data
public class LiveRoom {
    @Schema(description="房间主键ID")
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @Schema(description="主播ID (对应user库id)")
    private Long anchorId;
    @Schema(description = "主播名称")
    private String anchorName;
    @Schema(description="直播间标题")
    private String title;
    @Schema(description="直播间封面图URL")
    private String coverImg;
    @Schema(description="直播状态 0:未开播 1:直播中")
    private Integer roomStatus;
    @Schema(description = "是否允许接收礼物")
    private Boolean acceptingGifts;
    @Schema(description="当前在线人数")
    private Integer viewCount;
    @Schema(description="分类ID")
    private Integer categoryId;
    @Schema(description="最近一次开播时间")
    private Date lastStartTime;
    @Schema(description="创建时间")
    private Date createTime;
    @Schema(description="更新时间")
    private Date updateTime;
    @Schema(description = "直播间公告")
    private String notification;
}

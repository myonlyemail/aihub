package com.aihub.admin.mapper;

import com.aihub.admin.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT COUNT(*) FROM `user`")
    Long countTotalUsers();

    @Select("SELECT COUNT(*) FROM `user` WHERE DATE(create_time) = CURDATE()")
    Long countTodayNewUsers();

    @Select("SELECT COUNT(*) FROM `user` WHERE vip_level > 0")
    Long countVipUsers();

    @Select("SELECT COUNT(*) FROM `user` WHERE last_login_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)")
    Long countActiveUsers();

    @Select("SELECT COALESCE(SUM(amount), 0) FROM pay_order WHERE pay_status = 1 AND DATE(create_time) = CURDATE()")
    BigDecimal todayRevenue();

    @Select("SELECT COALESCE(SUM(amount), 0) FROM pay_order WHERE pay_status = 1")
    BigDecimal totalRevenue();

    @Select("SELECT COUNT(*) FROM ai_chat_record WHERE DATE(create_time) = CURDATE()")
    Long countTodayChat();

    @Select("SELECT COUNT(*) FROM ai_image_task WHERE DATE(create_time) = CURDATE()")
    Long countTodayImage();

    @Select("SELECT COUNT(*) FROM ai_video_task WHERE DATE(create_time) = CURDATE()")
    Long countTodayVideo();

    @Select("SELECT COALESCE(SUM(token_change), 0) FROM token_log WHERE business_type = 'chat' AND DATE(create_time) = CURDATE()")
    Long todayTokens();

    @Select("SELECT COALESCE(SUM(token_change), 0) FROM token_log WHERE business_type = 'chat'")
    Long totalTokens();
}

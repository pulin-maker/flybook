package com.bytedance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bytedance.entity.MqOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MqOutboxMapper extends BaseMapper<MqOutbox> {

    /**
     * CAS 更新状态：仅当当前状态为 expectedStatus 时才更新
     */
    @Update("UPDATE mq_outbox SET status = #{newStatus}, retry_count = retry_count + 1 " +
            "WHERE id = #{id} AND status = #{expectedStatus}")
    int casUpdateStatus(@Param("id") Long id,
                        @Param("expectedStatus") int expectedStatus,
                        @Param("newStatus") int newStatus);

    /**
     * 扫描 PENDING 状态且重试次数未超限的记录
     */
    @Select("SELECT * FROM mq_outbox WHERE status = 0 AND retry_count < #{maxRetry} " +
            "ORDER BY create_time ASC LIMIT #{batchSize}")
    List<MqOutbox> findPendingRecords(@Param("maxRetry") int maxRetry,
                                      @Param("batchSize") int batchSize);

    /**
     * 标记为已发送
     */
    @Update("UPDATE mq_outbox SET status = 1 WHERE id = #{id} AND status = 0")
    int markSent(@Param("id") Long id);
}

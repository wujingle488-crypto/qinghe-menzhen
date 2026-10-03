package com.commerce.cs.server.medical;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 早期每人只能有一张就诊卡，user_id 上有唯一索引。
 * Hibernate 的 update 不会删掉已有唯一索引，启动后再去掉，才能按姓名存多张。
 */
@Component
public class HealthProfileSchemaFix implements ApplicationRunner {
    private final JdbcTemplate jdbc;

    public HealthProfileSchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> names = jdbc.query("""
                    SELECT INDEX_NAME FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = 'cs_health_profile'
                      AND NON_UNIQUE = 0
                      AND COLUMN_NAME = 'user_id'
                      AND INDEX_NAME <> 'PRIMARY'
                    """, (rs, row) -> rs.getString(1));
            for (String name : names) {
                jdbc.execute("ALTER TABLE cs_health_profile DROP INDEX `" + name.replace("`", "") + "`");
            }
        } catch (Exception ignored) {
            // 表还没建出来时跳过
        }
    }
}

package com.cn.zym.note.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 显式注册 {@link ObjectMapper}，避免在部分上下文（例如测试或未触发 Jackson 自动配置时）
 * {@link com.cn.zym.note.service.AdminFacadeService}、{@link com.cn.zym.note.service.FileStorageService} 注入失败。
 */
@Configuration
public class JacksonMapperConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper m = new ObjectMapper();
        m.findAndRegisterModules();
        return m;
    }
}

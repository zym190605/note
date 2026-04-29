package com.cn.zym.note.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({JwtProps.class, NoteUploadProps.class, NoteSecurityProps.class, NoteExportProps.class})
public class ConfigBeans {

    @Bean
    PasswordEncoder passwordEncoder(NoteSecurityProps props) {
        int strength = props.passwordStrength() != null ? props.passwordStrength() : 12;
        return new BCryptPasswordEncoder(strength);
    }
}

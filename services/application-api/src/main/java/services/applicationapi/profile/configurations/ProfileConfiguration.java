package services.applicationapi.profile.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@EnableConfigurationProperties(ProfileProperties.class)
public class ProfileConfiguration {}

package com.mediflow.api.config;

import com.mediflow.api.entity.AppointmentStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Lets query parameters use the FHIR code ({@code ?status=booked}), the same as JSON. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, AppointmentStatus.class, AppointmentStatus::fromCode);
    }
}

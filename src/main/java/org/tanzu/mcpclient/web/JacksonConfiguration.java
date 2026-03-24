package org.tanzu.mcpclient.web;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Jackson configuration providing flexible timestamp handling.
 * Handles microsecond precision timestamps with or without timezone (assumes UTC).
 * Required for correct deserialization of A2A streaming responses.
 */
@Configuration
public class JacksonConfiguration {

    public static class FlexibleInstantDeserializer extends JsonDeserializer<Instant> {

        private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .optionalStart()
                .appendOffsetId()
                .optionalEnd()
                .toFormatter();

        @Override
        public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String timestamp = p.getText();

            if (timestamp == null || timestamp.isEmpty()) {
                return null;
            }

            try {
                return Instant.parse(timestamp);
            } catch (Exception e1) {
                try {
                    LocalDateTime ldt = LocalDateTime.parse(timestamp, FORMATTER);
                    return ldt.toInstant(ZoneOffset.UTC);
                } catch (Exception e2) {
                    try {
                        return Instant.parse(timestamp + "Z");
                    } catch (Exception e3) {
                        throw new IOException("Cannot parse timestamp: " + timestamp, e3);
                    }
                }
            }
        }
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.registerModule(new JavaTimeModule());

        SimpleModule customModule = new SimpleModule();
        customModule.addDeserializer(Instant.class, new FlexibleInstantDeserializer());
        mapper.registerModule(customModule);

        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        return mapper;
    }
}

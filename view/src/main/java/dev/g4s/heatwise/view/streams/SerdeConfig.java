package dev.g4s.heatwise.view.streams;


import com.fasterxml.jackson.databind.ObjectMapper;
import dev.g4s.heatwise.view.domain.Decision;
import dev.g4s.heatwise.view.domain.State;
import org.apache.kafka.common.serialization.Serde;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.kafka.support.serializer.JsonSerde;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.web.client.RestTemplate;

@Configuration
public class SerdeConfig {
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
         restTemplate.getMessageConverters().add(0, createMappingJacksonHttpMessageConverter());
         return restTemplate;
    }

    private MappingJackson2HttpMessageConverter createMappingJacksonHttpMessageConverter() {

        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper());
        return converter;
    }

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new Jdk8Module())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // ISO-8601 for Instant
                .build();
    }

    @Bean
    public Serde<State> stateSerde(ObjectMapper om) {
        JsonSerde<State> s = new JsonSerde<>(State.class, om);
        s.ignoreTypeHeaders();
        return s;
    }

    @Bean
    public Serde<Decision> decisionSerde(ObjectMapper om) {
        JsonSerde<Decision> s = new JsonSerde<>(Decision.class, om);
        s.ignoreTypeHeaders();
        return s;
    }
}


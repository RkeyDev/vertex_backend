package com.rkey.vertex_backend.core.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisClientConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Objects;

@Configuration
public class RedisConfig {

    @Value("${redis.host:localhost}")
    private String host;

    @Value("${redis.port:6379}")
    private int port;

    @Value("${redis.user:default}")
    private String user;

    @Value("${redis.password:}")
    private String password;

    private boolean isSslEnabled() {
        return password != null && !password.isBlank();
    }

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        String redisHost = Objects.requireNonNull(host, "redis.host must not be null");
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, port);
        config.setUsername(user);

        if (isSslEnabled()) {
            config.setPassword(password);
        }

        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(64);
        poolConfig.setMaxIdle(32);
        poolConfig.setMinIdle(8);

        Duration timeout = Objects.requireNonNull(Duration.ofMillis(2000));
        JedisClientConfiguration.JedisClientConfigurationBuilder builder = JedisClientConfiguration.builder()
                .connectTimeout(timeout)
                .readTimeout(timeout);

        if (isSslEnabled()) {
            builder.useSsl();
        }

        JedisClientConfiguration clientConfig = builder
                .usePooling()
                .poolConfig(poolConfig)
                .build();

        return new JedisConnectionFactory(config, clientConfig);
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(JedisConnectionFactory jedisConnectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(Objects.requireNonNull(jedisConnectionFactory));
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(JedisConnectionFactory jedisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(Objects.requireNonNull(jedisConnectionFactory));
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(JedisConnectionFactory jedisConnectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(Objects.requireNonNull(jedisConnectionFactory));
        return container;
    }
}
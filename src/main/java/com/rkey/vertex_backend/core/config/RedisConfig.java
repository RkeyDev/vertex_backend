package com.rkey.vertex_backend.core.config;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.providers.PooledConnectionProvider;

/**
 * Redis configuration for Vertex.
 * Uses a PooledConnectionProvider to bridge the gap between UnifiedJedis 
 * and the underlying connection pool.
 */
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

    @Bean
    public UnifiedJedis unifiedJedis() {
        // Connection Details
        HostAndPort address = new HostAndPort(host, port);

        // Client Credentials & Timeouts
        JedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .user(user)
                .password(password)
                .connectionTimeoutMillis(2000)
                .socketTimeoutMillis(2000)
                .build();

        // Pool Configuration (Handling high-concurrency canvas events)
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(64);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);

        // The Bridge: Create a Provider that UnifiedJedis accepts
        PooledConnectionProvider provider = new PooledConnectionProvider(address, clientConfig, poolConfig);

        // Return the UnifiedJedis instance using the provider
        return new UnifiedJedis(provider);
    }
}
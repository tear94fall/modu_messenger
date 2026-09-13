package com.example.memberservice.config;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * spring.data.redis.cluster.nodes 유무에 따라 Redisson 이 클러스터/단일 서버 모드로 갈라지는지 확인한다.
 * 운영(docker/local)은 클러스터, 임베디드 Redis 를 쓰는 테스트 프로필은 단일 서버여야 한다.
 */
class RedissonConfigTest {

    @Test
    void clusterNodesPresent_usesClusterServers() {
        RedisProperties props = new RedisProperties();
        props.setCluster(new RedisProperties.Cluster());
        props.getCluster().setNodes(List.of("redis-node-1:7001", "redis-node-2:7002"));

        Config config = RedissonConfig.buildConfig(props);

        assertThat(config.isClusterConfig()).isTrue();
        assertThat(config.useClusterServers().getNodeAddresses())
                .containsExactly("redis://redis-node-1:7001", "redis://redis-node-2:7002");
    }

    @Test
    void clusterNodesAbsent_usesSingleServer() {
        RedisProperties props = new RedisProperties();
        props.setHost("localhost");
        props.setPort(6379);

        Config config = RedissonConfig.buildConfig(props);

        assertThat(config.isClusterConfig()).isFalse();
        assertThat(config.useSingleServer().getAddress()).isEqualTo("redis://localhost:6379");
    }

    @Test
    void clusterNodesEmpty_usesSingleServer() {
        RedisProperties props = new RedisProperties();
        props.setHost("localhost");
        props.setPort(6379);
        props.setCluster(new RedisProperties.Cluster());
        props.getCluster().setNodes(List.of());

        Config config = RedissonConfig.buildConfig(props);

        assertThat(config.isClusterConfig()).isFalse();
    }
}

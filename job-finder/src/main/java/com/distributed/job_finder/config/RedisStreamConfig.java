package com.distributed.job_finder.config;

import com.distributed.job_finder.dtos.JobDto;
import com.distributed.job_finder.services.JobStreamConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;

@Slf4j
@Configuration
public class RedisStreamConfig {

    private final JobStreamConsumer jobStreamConsumer;

    private static final String STREAM_KEY = "job:ingestion:stream";
    private static final String CONSUMER_GROUP = "job-workers-group";

    @Autowired
    public RedisStreamConfig(JobStreamConsumer jobStreamConsumer) {
        this.jobStreamConsumer = jobStreamConsumer;
    }

    @Bean
    public Subscription jobStreamSubscription(RedisConnectionFactory redisConnectionFactory) {
        
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, ObjectRecord<String, JobDto>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .builder()
                        .pollTimeout(Duration.ofMillis(100)) 
                        .targetType(JobDto.class) 
                        .build();

        StreamMessageListenerContainer<String, ObjectRecord<String, JobDto>> container =
                StreamMessageListenerContainer.create(redisConnectionFactory, options);

        try {
            redisConnectionFactory.getConnection().streamCommands()
                    .xGroupCreate(STREAM_KEY.getBytes(), CONSUMER_GROUP, ReadOffset.from("0-0"), true);
        } catch (Exception e) {
            log.debug("Consumer group {} already exists", CONSUMER_GROUP);
        }

        Subscription subscription = container.receive(
                Consumer.from(CONSUMER_GROUP, "worker-1"),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                jobStreamConsumer
        );

        container.start();
        log.info("Successfully wired JobStreamConsumer to Redis stream: {}", STREAM_KEY);

        return subscription;
    }
}
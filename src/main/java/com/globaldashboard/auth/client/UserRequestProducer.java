package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserFindRequest;
import com.globaldashboard.auth.event.user.UserCreateRequest;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;

@KafkaClient
public interface UserRequestProducer {
    @Topic("persistence.users")
    void sendFindRequest(@KafkaKey String username, UserFindRequest request);

    @Topic("persistence.users")
    void sendCreateRequest(@KafkaKey String username, UserCreateRequest request);
}

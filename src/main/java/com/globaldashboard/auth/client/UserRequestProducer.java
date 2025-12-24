package com.globaldashboard.auth.client;

import com.globaldashboard.auth.event.user.UserFindRequest;
import io.micronaut.configuration.kafka.annotation.KafkaClient;
import io.micronaut.configuration.kafka.annotation.KafkaKey;
import io.micronaut.configuration.kafka.annotation.Topic;

@KafkaClient
public interface UserRequestProducer {
    @Topic("persistence.users")
    void sendFindRequest(@KafkaKey String username, UserFindRequest request);
}

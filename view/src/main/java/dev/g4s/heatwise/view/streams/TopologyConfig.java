package dev.g4s.heatwise.view.streams;


import dev.g4s.heatwise.view.domain.Decision;
import dev.g4s.heatwise.view.domain.DeviceView;
import dev.g4s.heatwise.view.domain.State;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueBytesStoreSupplier;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.util.Optional;

@Configuration
public class TopologyConfig {

    final private String stateTopic;
    final private String decisionTopic;
    final private String viewTopic;

    public static final String STATE_STORE_NAME    = "state-store";
    public static final String DECISION_STORE_NAME = "last-decision-store";
    public static final String VIEW_STORE_NAME     = "device-view-store";
    public TopologyConfig(
            @Value("${heatwise.topics.state}") String stateTopic,
            @Value("${heatwise.topics.decision}") String decisionTopic,
            @Value("${heatwise.topics.view}") String viewTopic

    ) {
        this.stateTopic = stateTopic;
        this.decisionTopic = decisionTopic;
        this.viewTopic = viewTopic;
    }

    @Bean
    public KStream<String, Decision> heatwiseTopology(
            StreamsBuilder builder,
            Serde<State> stateSerde,
            Serde<Decision> decisionSerde
    ) {
        KeyValueBytesStoreSupplier stateStoreSupplier =
                Stores.persistentKeyValueStore(STATE_STORE_NAME);

        KTable<String, State> stateTable = builder.table(
                stateTopic,
                Consumed.with(Serdes.String(), stateSerde),
                Materialized.<String, State>as(stateStoreSupplier)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(stateSerde)
        );

        KeyValueBytesStoreSupplier decisionStoreSupplier =
                Stores.persistentKeyValueStore(DECISION_STORE_NAME);

        KTable<String, Decision> lastDecisionTable = builder
                .stream(decisionTopic, Consumed.with(Serdes.String(), decisionSerde))
                .groupByKey(Grouped.with(Serdes.String(), decisionSerde))
                .reduce((oldVal, newVal) ->
                                (isAfter(newVal, oldVal) ? newVal : oldVal),
                        Materialized.<String, Decision>as(decisionStoreSupplier)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(decisionSerde)
                );

        KeyValueBytesStoreSupplier viewStoreSupplier =
                Stores.persistentKeyValueStore(VIEW_STORE_NAME);

        KTable<String, DeviceView> deviceView = stateTable.leftJoin(
                lastDecisionTable,
                (state, decision) -> {
                    if (state == null) return null;
                    return new DeviceView(
                            state.deviceId(),
                            Optional.ofNullable(decision).map(Decision::heatOn),
                            Optional.ofNullable(decision).map(Decision::reason),
                            Optional.ofNullable(decision).map(Decision::ts),
                            state.lastOn(),
                            state.lastChangeTs()
                    );
                },
                Materialized.<String, DeviceView>as(viewStoreSupplier)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(new org.springframework.kafka.support.serializer.JsonSerde<>(DeviceView.class))
        );


        deviceView.toStream().to(viewTopic, Produced.with(Serdes.String(), new JsonSerde<>(DeviceView.class)));

        return builder.stream(decisionTopic, Consumed.with(Serdes.String(), decisionSerde))
                .map(KeyValue::pair); // no-op
    }

    private static boolean isAfter(Decision newVal, Decision oldVal) {
            return switch (newVal) {
                case null -> false;
                case Decision nv when oldVal == null -> true;
                case Decision nv -> !nv.ts().isBefore(oldVal.ts()); //newVal >= oldVal
            };
        }
    }


package dev.g4s.heatwise.view.streams;

import dev.g4s.heatwise.view.domain.Decision;
import dev.g4s.heatwise.view.domain.DeviceView;
import dev.g4s.heatwise.view.domain.State;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class TopologySmokeTest {
    private final Clock clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);

    @Test
    void latestDecisionWinsAndJoinsState() {
        StreamsBuilder b = new StreamsBuilder();
        SerdeConfig serdeCfg = new SerdeConfig();
        TopologyConfig topo = new TopologyConfig("heatwise.state", "heatwise.decisions", "heatwise.view");

        topo.heatwiseTopology(
                b,
                serdeCfg.stateSerde(serdeCfg.objectMapper()),
                serdeCfg.decisionSerde(serdeCfg.objectMapper())
        );
        Topology builtTopology = b.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        // props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        // props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArray().getClass());

        try (TopologyTestDriver td = new TopologyTestDriver(builtTopology, props)) {
            var stateIn = td.createInputTopic(
                    "heatwise.state",
                    Serdes.String().serializer(),
                    serdeCfg.stateSerde(serdeCfg.objectMapper()).serializer()
            );
            var decisionIn = td.createInputTopic(
                    "heatwise.decisions",
                    Serdes.String().serializer(),
                    serdeCfg.decisionSerde(serdeCfg.objectMapper()).serializer()
            );

            stateIn.pipeInput("dev1", new State(null, clock.instant(),true));
            decisionIn.pipeInput("dev1", new Decision("dev1",true,"Price OK",clock.instant()));
            decisionIn.pipeInput("dev1", new Decision("dev1",false,"Price too high",clock.instant().plusMillis(1)));

            KeyValueStore<String, DeviceView> store = td.getKeyValueStore(TopologyConfig.VIEW_STORE_NAME);
            DeviceView v = store.get("dev1");
            assertThat(v.lastDecisionReason()).isEqualTo(Optional.of("Price too high"));
            assertThat(v.lastDecisionOn().get()).isFalse();
        }
    }

}

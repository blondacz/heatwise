package dev.g4s.heatwise.view.api;

import dev.g4s.heatwise.view.domain.DeviceView;
import dev.g4s.heatwise.view.streams.TopologyConfig;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.StreamSupport;

@RestController
@RequestMapping("/api/view")
public class ViewController {

    private final StreamsBuilderFactoryBean factory;

    public ViewController(StreamsBuilderFactoryBean factory) {
        this.factory = factory;
    }

    private ReadOnlyKeyValueStore<String, DeviceView> viewStore() {
        KafkaStreams streams = factory.getKafkaStreams();
        return streams.store(StoreQueryParameters.fromNameAndType(
                TopologyConfig.VIEW_STORE_NAME, QueryableStoreTypes.keyValueStore()));
    }

    @GetMapping("/device/{deviceId}")
    public DeviceView byDevice(@PathVariable String deviceId) {
        return viewStore().get(deviceId);
    }


    @GetMapping("/devices")
    public List<DeviceView> all(@RequestParam(defaultValue = "100") int limit) {
            return StreamSupport.stream(
                            Spliterators.spliteratorUnknownSize(viewStore().all(), Spliterator.ORDERED),
                            false)
                    .map(kv -> kv.value)
                    .limit(limit)
                    .toList();
        }

}

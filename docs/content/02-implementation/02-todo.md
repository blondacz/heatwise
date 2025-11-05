# TODO
1. Make all policy values configurable
2. Make all check intervals configurable
3. Remove deduplication or make it switchable
4. ~~Start storing in readable log on Kafka or RocksDB or actors so it can resume after restart with previous state~~ (Chose Kafka)
5. ~~Add endpoint to see latest n-decisions~~ (/health endpoint shows liveness latest liveness hecks that record state from adapters and main decision)
6. Add room sensor or sensors or thermostats
7. Start publishing metrics to prometheus
8. Support multiple immersion spirals
9. Support manul switch on or off as admin commands
10. Add laika to build the documentation
11. Support only reporting mode (how is it similar to dummy?)
12. Support calendar for pre-heats 
13. Support alerts and problem detection
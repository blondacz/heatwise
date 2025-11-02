docker run --rm -p 8080:8080 \
  -e OCTOPUS_PRODUCT_CODE="AGILE-24-10-01" \
  -e OCTOPUS_TARIFF_CODE="E-1R-AGILE-24-10-01-J" \
  -e RELAY_HOST="192.168.1.50" \
  -e MAX_PRICE_PER_KWH="6.0" \
  -e MORNING_READY="06:30" \
  -e DUMMY_RUN="true" \
  heatwise:local
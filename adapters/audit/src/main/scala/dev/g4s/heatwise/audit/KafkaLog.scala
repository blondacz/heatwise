package dev.g4s.heatwise.audit

import dev.g4s.heatwise.domain.{ControllerState, Decision, DecisionReason, HeatwiseKafkaConfig}
import io.circe.{Decoder, Encoder}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.kafka.ProducerSettings
import org.apache.pekko.kafka.scaladsl.SendProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import io.circe.syntax.*
import io.circe.generic.semiauto.*
import org.apache.kafka.clients.admin.*

import java.util
import scala.jdk.CollectionConverters.*
import org.apache.pekko.kafka.scaladsl.Consumer
import org.apache.pekko.kafka.{ConsumerSettings, Subscriptions}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import org.apache.pekko.stream.scaladsl.Sink

import java.time.Instant
import scala.concurrent.Future


class KafkaLog(cfg: HeatwiseKafkaConfig)(implicit sys: ActorSystem)  {
  private val settings =
    ProducerSettings(sys, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(cfg.bootstrap)
      .withProperty("acks", cfg.acks)

  private val producer = SendProducer(settings)

  given decisionReasonEncoder : Encoder[DecisionReason] = Encoder.encodeString.contramap[DecisionReason](_.toString)
  given decisionEncoder : Encoder[Decision] = deriveEncoder[Decision]
  given stateEncoder : Encoder[ControllerState] = deriveEncoder[ControllerState]
  given stateDecoder : Decoder[ControllerState] = deriveDecoder[ControllerState]

  def publishDecision(c: ControllerState, d: Decision): Unit = {
    val key = cfg.deviceId
    val value = d.asJson.noSpaces.getBytes("UTF-8")
    producer.send(new ProducerRecord(cfg.topics.decisions, key, value))
    val st = c.asJson.noSpaces.getBytes("UTF-8")
    producer.send(new ProducerRecord(cfg.topics.state, key, st))
  }


  def start(): Unit = {
    val props = new util.Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cfg.bootstrap)
    val admin = AdminClient.create(props)
    try {
      val existing = admin.listTopics().names().get()
      val toCreate = new util.ArrayList[NewTopic]()
      if (!existing.contains(cfg.topics.decisions)) toCreate.add(new NewTopic(cfg.topics.decisions, 1, 1.toShort))
      if (!existing.contains(cfg.topics.state)) {
        val t = new NewTopic(cfg.topics.state, 1, 1.toShort)
        t.configs(Map("cleanup.policy" -> "compact").asJava)
        toCreate.add(t)
      }
      if (!toCreate.isEmpty) admin.createTopics(toCreate).all().get()
    } finally admin.close()
  }

  

  def restoreState(): Future[Option[ControllerState]] = {
    val cs = ConsumerSettings(sys, new StringDeserializer, new ByteArrayDeserializer)
      .withBootstrapServers(cfg.bootstrap)
      .withGroupId(s"${cfg.deviceId}-restorer")
      .withProperty("auto.offset.reset", "earliest") // read the whole compacted log
      .withProperty("isolation.level", "read_committed")

    Consumer
      .plainSource(cs, Subscriptions.topics(cfg.topics.state))
      .filter(_.key() == cfg.deviceId)
      .map(_.value())
      .runWith(Sink.fold(Option.empty[ControllerState]) { (_, bytes) =>
        io.circe.parser.decode[ControllerState](new String(bytes, "UTF-8")).toOption
      })
  }
}
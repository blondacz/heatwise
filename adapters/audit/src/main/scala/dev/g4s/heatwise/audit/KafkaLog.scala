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
import org.apache.kafka.clients.consumer.ConsumerPartitionAssignor.Assignment
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.TopicPartition

import java.util
import scala.jdk.CollectionConverters.*
import org.apache.pekko.kafka.scaladsl.Consumer
import org.apache.pekko.kafka.{ConsumerSettings, Subscriptions}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import org.apache.pekko.stream.scaladsl.{Keep, Sink}

import java.time.Instant
import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.concurrent.duration.*


class KafkaLog(cfg: HeatwiseKafkaConfig)(implicit sys: ActorSystem)  {
  import sys.dispatcher
  private val settings =
    ProducerSettings(sys, new StringSerializer, new ByteArraySerializer)
      .withBootstrapServers(cfg.bootstrap)
      .withProperty("acks", cfg.acks)
      .withProperty("request.timeout.ms", "30000")
      .withProperty("session.timeout.ms", "30000")
      .withProperty("metadata.max.age.ms", "5000")

  private val producer = SendProducer(settings)

  given decisionReasonEncoder : Encoder[DecisionReason] = Encoder.encodeString.contramap[DecisionReason](_.toString)
  given decisionEncoder : Encoder[Decision] = deriveEncoder[Decision]
  given stateEncoder : Encoder[ControllerState] = deriveEncoder[ControllerState]
  given stateDecoder : Decoder[ControllerState] = deriveDecoder[ControllerState]

  def publishDecision(c: ControllerState, d: Decision): Future[Unit] = {
    val key = cfg.deviceId
    val value = d.asJson.noSpaces.getBytes("UTF-8")
    val decisionFuture = producer.send(new ProducerRecord(cfg.topics.decisions, key, value))
      .recover { case ex =>
        sys.log.error(ex, s"Failed to publish decision to ${cfg.topics.decisions}: $d")
      }
    val st = c.asJson.noSpaces.getBytes("UTF-8")
    val stateFuture = producer.send(new ProducerRecord(cfg.topics.state, key, st))
      .recover { case ex =>
        sys.log.error(ex, s"Failed to publish state to ${cfg.topics.state}: $c")
      }
    for {
      _ <- decisionFuture
      _ <- stateFuture
    } yield ()
  }


  def start(): Unit = {
    @tailrec
    def attemptTopicCreation(attempt: Int): Unit = {
      sys.log.info(s"Attempting to connect to Kafka at ${cfg.bootstrap} and create topics (attempt $attempt/10)")
      val props = new util.Properties()
      props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, cfg.bootstrap)
      props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000")
      props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "30000")
      val admin = AdminClient.create(props)

      Try {
        val existing = admin.listTopics().names().get()
        sys.log.info(s"Successfully connected to Kafka. Existing topics: ${existing.asScala.mkString(", ")}")

        val toCreate = new util.ArrayList[NewTopic]()
        if (!existing.contains(cfg.topics.decisions)) {
          sys.log.info(s"Creating topic: ${cfg.topics.decisions}")
          toCreate.add(new NewTopic(cfg.topics.decisions, 1, 1.toShort))
        }
        if (!existing.contains(cfg.topics.state)) {
          sys.log.info(s"Creating topic: ${cfg.topics.state}")
          val t = new NewTopic(cfg.topics.state, 1, 1.toShort)
          t.configs(Map("cleanup.policy" -> "compact").asJava)
          toCreate.add(t)
        }
        if (!toCreate.isEmpty) {
          admin.createTopics(toCreate).all().get()
          sys.log.info(s"Successfully created ${toCreate.size()} topic(s)")
        } else {
          sys.log.info("All required topics already exist")
        }
      } match {
        case Success(_) =>
          admin.close()
          sys.log.info("Kafka topic initialization complete")
        case Failure(ex) =>
          admin.close()
          if (attempt < 10) {
            val delay = math.min(attempt * 2, 10)
            sys.log.warning(s"Failed to initialize Kafka topics (attempt $attempt/10): ${ex.getMessage}. Retrying in ${delay}s...")
            Thread.sleep(delay * 1000)
            attemptTopicCreation(attempt + 1)
          } else {
            sys.log.error(ex, s"Failed to initialize Kafka topics after 10 attempts. Application may not function correctly.")
            throw ex
          }
      }
    }

    attemptTopicCreation(1)
  }


  
  
  
  def restoreState(): Future[Option[ControllerState]] = {
    sys.log.info(s"Attempting to restore state from topic ${cfg.topics.state}")

    Future {
      val props = new util.Properties()
      props.put("bootstrap.servers", cfg.bootstrap)
      props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
      props.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer")
      props.put("group.id", s"${cfg.deviceId}-restorer-${System.nanoTime()}")
      props.put("enable.auto.commit", "false")
      props.put("isolation.level", "read_committed")

      val consumer = new KafkaConsumer[String, Array[Byte]](props)

      try {
        val partitions = consumer.partitionsFor(cfg.topics.state).asScala.map { info =>
          new TopicPartition(info.topic(), info.partition())
        }.toList

        if (partitions.isEmpty) {
          sys.log.info("No partitions found for topic")
          None
        } else {
          consumer.assign(partitions.asJava)
          consumer.seekToBeginning(partitions.asJava)

          val endOffsets = consumer.endOffsets(partitions.asJava).asScala

          @tailrec
          def pollUntilEnd(lastState: Option[ControllerState]): Option[ControllerState] = {
            val records = consumer.poll(java.time.Duration.ofSeconds(5))

            if (records.isEmpty) {
              lastState
            } else {
              val updatedState = records.asScala
                .filter(_.key() == cfg.deviceId)
                .foldLeft(lastState) { (acc, record) =>
                  io.circe.parser.decode[ControllerState](new String(record.value(), "UTF-8")) match {
                    case Right(state) => Some(state)
                    case Left(e) =>
                      sys.log.error(s"Failed to decode state: ${e.getMessage}")
                      acc//TODO: or None?
                  }
                }

              val currentPositions = partitions.map(p => p -> consumer.position(p)).toMap
              val hasMore = currentPositions.exists { case (partition, position) =>
                position < endOffsets.getOrElse(partition, Long.box(0L))
              }

              if (hasMore) pollUntilEnd(updatedState) else updatedState
            }
          }

          val lastState = pollUntilEnd(None)

          lastState match {
            case Some(state) => sys.log.info(s"Successfully restored state: $state")
            case None => sys.log.info("No previous state found in Kafka")
          }

          lastState
        }
      } finally {
        consumer.close()
      }
    }.recoverWith {
      case ex =>
        sys.log.error(ex, s"Failed to restore state from Kafka topic ${cfg.topics.state}")
        Future.successful(None)
    }
  }
}
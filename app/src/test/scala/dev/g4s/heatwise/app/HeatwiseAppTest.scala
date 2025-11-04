package dev.g4s.heatwise.app

import dev.g4s.heatwise.domain.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.apache.pekko.testkit.TestKit
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import sttp.client4.Backend
import sttp.client4.pekkohttp.PekkoHttpBackend

import java.time.{Clock, Instant, ZoneOffset, ZonedDateTime}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*

class HeatwiseAppTest extends TestKit(ActorSystem("HeatwiseAppTest"))
  with AnyFreeSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ScalaFutures {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  implicit val defaultPatience: PatienceConfig = PatienceConfig(
    timeout = Span(15, Seconds),
    interval = Span(100, Millis)
  )
  
  given backend: Backend[Future] = PekkoHttpBackend.usingActorSystem(system)
   private val lowTemp = BigDecimal(10)
  
  "HeatwiseApp" - {
    "should make decisions based on price points" in {
      val fixedTime = Instant.parse("2024-01-01T10:00:00Z")
      given clock : Clock = Clock.fixed(fixedTime, ZoneOffset.UTC)
      val cfg = HeatwiseConfig("AGILE-24-10-01", "E-1R-AGILE-24-10-01-J", "192.168.1.50", BigDecimal(10), None, lowTemp, dummyRun = true, checkInterval = 1.second)

      val prices = List(
        PricePoint(ZonedDateTime.parse("2024-01-01T10:00:00Z"), ZonedDateTime.parse("2024-01-01T10:30:00Z"), BigDecimal(5), BigDecimal(5.25)),
        PricePoint(ZonedDateTime.parse("2024-01-01T10:30:00Z"), ZonedDateTime.parse("2024-01-01T11:00:00Z"), BigDecimal(15), BigDecimal(15.75)),
        PricePoint(ZonedDateTime.parse("2024-01-01T11:00:00Z"), ZonedDateTime.parse("2024-01-01T11:30:00Z"), BigDecimal(8), BigDecimal(8.40))
      )

      val stubPriceService = new StubPriceService(prices)
      val stubRelayService = new StubRelayService
      val stubAuditService = new StubAuditService
      val stubTemperatureService = new StubCylinderTemperatureService(Temperature(lowTemp))

      val policy = Policy(maxPricePerKWh = BigDecimal(10), morningPreheat = None,  delay = Delay(), desiredTemperature = Temperature(BigDecimal(50)))

      val app = new HeatwiseApp(stubPriceService, stubRelayService, stubAuditService, stubTemperatureService)

      val decisionsF = app.decisionStream(app.combinedSource(cfg), policy)
        .take(3)
        .runWith(Sink.seq)

      whenReady(decisionsF) { decisions =>
        decisions should have size 3
        decisions(0).heatOn shouldBe true
        decisions(0).reason shouldBe DecisionReason.PriceOk(BigDecimal(5), BigDecimal(10))

        decisions(1).heatOn shouldBe false
        decisions(1).reason shouldBe DecisionReason.PriceTooHigh(BigDecimal(15), BigDecimal(10))

        decisions(2).heatOn shouldBe false
      }
    }

    "should execute relay switches and log decisions" in {
      val fixedTime = Instant.parse("2024-01-01T10:00:00Z")
      given clock : Clock = Clock.fixed(fixedTime, ZoneOffset.UTC)
      val cfg = HeatwiseConfig("AGILE-24-10-01", "E-1R-AGILE-24-10-01-J", "192.168.1.50", BigDecimal(10), None, lowTemp, dummyRun = true, checkInterval = 1.second)

      val prices = List(
        PricePoint(ZonedDateTime.parse("2024-01-01T10:00:00Z"), ZonedDateTime.parse("2024-01-01T10:30:00Z"), BigDecimal(5), BigDecimal(5.25)),
        PricePoint(ZonedDateTime.parse("2024-01-01T10:30:00Z"), ZonedDateTime.parse("2024-01-01T11:00:00Z"), BigDecimal(15), BigDecimal(15.75))
      )

      val stubPriceService = new StubPriceService(prices)
      val stubRelayService = new StubRelayService
      val stubAuditService = new StubAuditService
      val stubTemperatureService = new StubCylinderTemperatureService(Temperature(lowTemp))

      val policy = Policy(maxPricePerKWh = BigDecimal(10), morningPreheat = None, desiredTemperature = Temperature(BigDecimal(50)), delay = Delay())

      val app = new HeatwiseApp(stubPriceService, stubRelayService, stubAuditService, stubTemperatureService)

      val combinedSrc = app.combinedSource(cfg)
      val executionF = app.executionStream(cfg,app.decisionStream(combinedSrc,policy))
        .take(2)
        .runWith(Sink.seq)

      whenReady(executionF) { _ =>
        stubRelayService.switches should have size 2
        stubRelayService.switches(0) shouldBe (("192.168.1.50", true, true))
        stubRelayService.switches(1) shouldBe (("192.168.1.50", false, true))

        stubAuditService.decisions should have size 2
        stubAuditService.decisions(0).heatOn shouldBe true
        stubAuditService.decisions(1).heatOn shouldBe false
      }
    }

    "should handle relay errors gracefully" in {
      val fixedTime = Instant.parse("2024-01-01T10:00:00Z")
      given clock: Clock = Clock.fixed(fixedTime, ZoneOffset.UTC)
      val cfg = HeatwiseConfig("AGILE-24-10-01", "E-1R-AGILE-24-10-01-J", "192.168.1.50", BigDecimal(10), None, lowTemp, dummyRun = true, checkInterval = 1.second)

      val prices = List(
        PricePoint(ZonedDateTime.parse("2024-01-01T10:00:00Z"), ZonedDateTime.parse("2024-01-01T10:30:00Z"), BigDecimal(5), BigDecimal(5.25))
      )

      val stubPriceService = new StubPriceService(prices)
      val failingRelayService = new FailingRelayService
      val stubAuditService = new StubAuditService
      val stubTemperatureService = new StubCylinderTemperatureService(Temperature(lowTemp))

      val policy = Policy(maxPricePerKWh = BigDecimal(10), morningPreheat = None, desiredTemperature = Temperature(BigDecimal(50)), delay = Delay())

      val app = new HeatwiseApp(stubPriceService, failingRelayService, stubAuditService, stubTemperatureService)

      val executionF =  app.executionStream(cfg,app.decisionStream(app.combinedSource(cfg),policy))
        .take(1)
        .runWith(Sink.seq)

      whenReady(executionF) { _ =>
        stubAuditService.decisions should have size 1
      }
    }

    "should not switch on when temperature is high enough" in {
      val fixedTime = Instant.parse("2024-01-01T10:00:00Z")
      given clock : Clock = Clock.fixed(fixedTime, ZoneOffset.UTC)
      val highTemp = BigDecimal(60)
      val cfg = HeatwiseConfig("AGILE-24-10-01", "E-1R-AGILE-24-10-01-J", "192.168.1.50", BigDecimal(10), None, highTemp, dummyRun = true, checkInterval = 1.second)

      val prices = List(
        PricePoint(ZonedDateTime.parse("2024-01-01T10:00:00Z"), ZonedDateTime.parse("2024-01-01T10:30:00Z"), BigDecimal(5), BigDecimal(5.25))
      )

      val stubPriceService = new StubPriceService(prices)
      val stubRelayService = new StubRelayService
      val stubAuditService = new StubAuditService
      val stubTemperatureService = new StubCylinderTemperatureService(Temperature(highTemp))

      val policy = Policy(maxPricePerKWh = BigDecimal(10), morningPreheat = None, desiredTemperature = Temperature(BigDecimal(50)), delay = Delay())

      val app = new HeatwiseApp(stubPriceService, stubRelayService, stubAuditService, stubTemperatureService)

      val decisionsF = app.decisionStream(app.combinedSource(cfg), policy)
        .take(1)
        .runWith(Sink.seq)

      whenReady(decisionsF) { decisions =>
        decisions should have size 1
        decisions(0).heatOn shouldBe false
        decisions(0).reason shouldBe DecisionReason.TemperatureOk(Temperature(highTemp), Temperature(BigDecimal(50)))
      }
    }
  }

  class StubPriceService(prices: List[PricePoint]) extends PriceService {
    private var currentIndex = 0

    def fetchCurrentPrice(cfg: HeatwiseConfig, now: ZonedDateTime)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[PricePoint] = {
      val price = if (currentIndex < prices.size) prices(currentIndex) else prices.last
      currentIndex += 1
      Future.successful(price)
    }
  }

  class StubRelayService extends RelayService {
    val switches: mutable.ListBuffer[(String, Boolean, Boolean)] = mutable.ListBuffer.empty

    def switchRelay(host: String, on: Boolean, dummyRun: Boolean)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[Either[Throwable, Boolean]] = {
      switches += ((host, on, dummyRun))
      Future.successful(Right(on))
    }
  }

  class FailingRelayService extends RelayService {
    def switchRelay(host: String, on: Boolean, dummyRun: Boolean)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Future[Either[Throwable, Boolean]] = {
      Future.successful(Left(new RuntimeException("Relay connection failed")))
    }
  }

  class StubAuditService extends AuditService {
    val decisions: mutable.ListBuffer[Decision] = mutable.ListBuffer.empty

    def logDecision(decision: Decision): Unit = {
      decisions += decision
    }
  }

  class StubCylinderTemperatureService(temperature: Temperature) extends CylinderTemperatureService {
    def fetchCurrentTemperature(cfg: HeatwiseConfig)(using system: ActorSystem, backend: Backend[Future], executionContext: ExecutionContext): Source[Temperature, org.apache.pekko.actor.Cancellable] = {
      org.apache.pekko.stream.scaladsl.Source.tick(0.seconds, cfg.checkInterval, temperature)
    }
  }
}

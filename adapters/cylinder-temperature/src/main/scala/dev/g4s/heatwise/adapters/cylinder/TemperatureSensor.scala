package dev.g4s.heatwise.adapters.cylinder

import org.apache.pekko.actor.Cancellable
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Random, Success, Try, Using}
import dev.g4s.heatwise.domain.{Temperature, TemperatureSensorConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class TemperatureSensor(temperatureSensorConfig: TemperatureSensorConfig) {
  private val file = temperatureSensorConfig.path.toFile
  private val TRex = ".*t=(\\d{5})$".r
  
  def temperature: Source[Temperature, Cancellable] = {
    Source.tick(0.seconds, temperatureSensorConfig.checkInterval, ()).mapAsync(1) { _ =>
      Future {
        if (file.exists()) {
            Using(scala.io.Source.fromFile(file, "UTF-8")) { f =>
              f.getLines().toList match {
                case first :: second :: Nil if !first.endsWith("YES") => Failure(new IllegalStateException(s"Temperature not syncing. Line 1 is: [$first]"))
                case first :: TRex(t) :: Nil => Success(Temperature(BigDecimal(t) / 1000))
                case first :: second :: Nil => Failure(new IllegalStateException(s"Temperature has wrong format. Line 2 is: [$second]"))
                case l => Failure(new IllegalStateException(s"Temperature has wrong file format. Lines are: ${l.mkString(",")}"))
              }
            } 
          } else {
            Failure( new IllegalStateException(s"File not found: ${file.getAbsolutePath}"))
          }
        }.flatMap {
        case Failure(exception) => Future.failed(exception)
        case Success(Failure(value)) => Future.failed(value)
        case Success(Success(value)) => Future.successful(value)
      }
      }
    }
  }


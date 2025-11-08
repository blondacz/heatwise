package dev.g4s.heatwise.adapters.cylinder
import java.net.URI
import java.nio.file.{FileSystems, Files, Path, Paths}
import scala.util.Using
import java.io.IOException

object ResourceUtils {
  
  def getPathFromResource(resourceName: String): Path = {
    val url = getClass.getResource(s"/$resourceName")

    if (url == null) {
      throw new IllegalArgumentException(s"Resource not found: $resourceName")
    }

    val uri = url.toURI

    if (uri.getScheme.equals("jar")) {
      try {
        FileSystems.newFileSystem(uri, java.util.Collections.emptyMap[String, AnyRef]())
      } catch {
        case _: Exception =>
      }
      Paths.get(uri)
    } else {
      Paths.get(uri)
    }
  }
}


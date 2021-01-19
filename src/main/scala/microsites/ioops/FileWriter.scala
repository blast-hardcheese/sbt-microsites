/*
 * Copyright 2016-2020 47 Degrees Open Source <https://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package microsites.ioops

import java.io.File
import java.net.URL
import java.nio.file.{FileSystems, Files, Path, Paths}
import java.nio.file.Files.{copy => fcopy}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.{util => ju}

import cats.implicits._
import microsites.Exceptions._
import syntax._

class FileWriter {

  def writeContentToFile(content: String, output: String): IOResult[Unit] = {

    def writeFile: Either[Throwable, Unit] =
      Either.catchNonFatal(IOUtils.write(IOUtils.file(output), content))

    (for {
      result <- createFile(output)
      _      <- writeFile if result
    } yield ()).leftMap(e => IOException(s"Error writing to file $output", Some(e)))

  }

  def createFile(file: File): IOResult[Boolean] = {

    def parentDirExists(f: File): Boolean = f.getParentFile.exists() || f.getParentFile.mkdirs()

    Either
      .catchNonFatal(file.exists() || (parentDirExists(file) && file.createNewFile()))
      .leftMap(e => IOException(s"Error creating file", Some(e)))
  }

  def createFile(output: String): IOResult[Boolean] = createFile(output.toFile)

  def createDir(output: File): IOResult[Boolean] =
    Either
      .catchNonFatal(output.exists() || output.mkdir())
      .leftMap(e => IOException(s"Error creating directory $output", Some(e)))

  def createDir(output: String): IOResult[Boolean] =
    createDir(output.fixPath.ensureFinalSlash.toFile)

  def copy(input: String, output: String): IOResult[Path] = {

    def safeCopy: Either[Throwable, Path] =
      Either
        .catchNonFatal(fcopy(input.toPath, output.toPath, REPLACE_EXISTING))

    (for {
      result <- createFile(output)
      path   <- safeCopy if result
    } yield path)
      .leftMap(e => IOException(s"Error copying file from $input to $output", Some(e)))
  }

  def copyFilesRecursively(sourcePath: String, outputPath: String): IOResult[List[Path]] = {

    def copySingleFile(f: File): IOResult[Path] = {
      val filePath = f.getAbsolutePath.replaceAllLiterally(sourcePath, "")
      copy(f.getAbsolutePath, s"${outputPath.ensureFinalSlash}$filePath")
    }

    def copyMultipleFiles(files: List[File]): IOResult[List[Path]] =
      files.traverse(copySingleFile)

    println(sourcePath)
    for {
      files <- FileReader.fetchFilesRecursivelyFromPath(sourcePath)
      _ = println(s"files: $files")
      paths <- copyMultipleFiles(files)
      _ = println(s"paths: $paths")
    } yield paths
  }

  /** Copies files from an arbitrary FileSystem (dir, JAR, ZIP) defined at the listed URL */
  def copyResourcesTo(
      url: URL,
      outputPath: String,
      filter: String = ""
  ) = {
    val emptyMap = new ju.HashMap[String, Any]
    Either
      .catchNonFatal(FileSystems.newFileSystem(url.toURI, emptyMap))
      .flatMap { fs =>
        Either
          .catchNonFatal {
            val filesInPath = fs.getPath(filter).iterator

            filesInPath.forEachRemaining { path =>
              Files.copy(path, Paths.get(outputPath), REPLACE_EXISTING)
            }
          }
          .leftMap { e => fs.close(); e }
          .map(_ => fs.close())
      }
      .leftMap(e =>
        IOException(s"Error copying resources from $url to directory $outputPath", Some(e))
      )
  }
}

object FileWriter extends FileWriter

package com.rubrik.sanescala.component

import com.rubrik.SaneScala
import java.net.URLClassLoader
import org.scalatest.Matchers
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.internal.util.Position
import scala.tools.nsc.Global
import scala.tools.nsc.Settings
import scala.tools.nsc.io.VirtualDirectory
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent
import scala.tools.nsc.reporters.Reporter
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.util.ClassPath

/**
 * @param line 1 based index
 * @param col 1 based index
 */
case class Caret(line: Int, col: Int)

object Caret {
  def fromPosition(position: Position): Caret = {
    Caret(position.line, position.column)
  }
}

object TestUtil extends Matchers {
  def extractCarets(annotatedCode: String): Set[Caret] = {
    annotatedCode
      .stripMargin
      .split("\n")
      .zipWithIndex
      .filter { case (line, _) => line.contains("^") }
      .zipWithIndex
      .map {
        case ((line, lineNo), caretCount) => (line, lineNo - caretCount)
      }
      // The index from zipWithIndex works fine as line number
      // because the indices start from 0 while line numbers start from 1
      // On the other hand, we need to offset the column number by 1
      .flatMap {
        case (line, lineNo) =>
          val columnNumbers: Seq[Int] =
            (0 until line.length).filter(line.startsWith("^", _)).map(_ + 1)
          columnNumbers.map(Caret(lineNo, _))
      }
      .toSet
  }

  def extractSourceCode(annotatedCode: String): String = {
    annotatedCode
      .stripMargin
      .split("\n")
      .filterNot(_.contains("^"))
      .mkString("\n")
  }

  val compilerSettings: Settings = {
    val settings = new Settings

    val classPathEntries: Array[String] =
      this.getClass.getClassLoader
        .asInstanceOf[URLClassLoader]
        .getURLs
        .map(_.getPath)

    // When the tests are run through SBT, "scala-library.jar" in not
    // in the classpath, but conveniently, it is easy to guess where
    // it is based on where "scala-compiler.jar" is.
    val scalaLibraryJarPath: Option[String] =
      classPathEntries
        .find(_.endsWith("scala-compiler.jar"))
        .map(_.replaceAll("scala-compiler.jar", "scala-library.jar"))

    settings.classpath.value =
      ClassPath.join(classPathEntries ++ scalaLibraryJarPath: _*)

    // Save class files to a virtual directory in memory
    settings.outputDirs.setSingleOutput(new VirtualDirectory("dummy", None))

    settings
  }

  def getCompiler(
    componentFactory: Global => SaneScalaComponent,
    reporter: Reporter
  ): Global = {
    new Global(compilerSettings, reporter) {
      override def loadRoughPluginsList: List[Plugin] = List(plugin)
      val global: Global = this
      val plugin: Plugin =
        new SaneScala(global) {
          override val components: List[PluginComponent] =
            List(componentFactory(global))
        }
    }
  }

  def testWith(
    componentFactory: Global => SaneScalaComponent
  )(
    annotatedCode: String
  ): Unit = {
    val sourceCode = extractSourceCode(annotatedCode)
    val carets = extractCarets(annotatedCode)
    val sources = List(new BatchSourceFile("dummy.scala", sourceCode))

    val reporter = new StoreReporter
    val compiler = getCompiler(componentFactory, reporter)
    new compiler.Run().compileSources(sources)

    reporter.infos.map(_.pos).map(Caret.fromPosition) shouldBe carets
  }
}

package com.rubrik.sanescala.component

import com.rubrik.CompilerPhase
import scala.tools.nsc.Global
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.PluginComponent

abstract class SaneScalaComponent(
  override val global: Global,
  val runAfter: CompilerPhase
) extends PluginComponent {
  import global._  // scalastyle:ignore

  def description: String
  def visit(tree: Tree): Unit

  override final def newPhase(prev: Phase): StdPhase = new StdPhase(prev) {
    override def name: String = phaseName
    def apply(unit: CompilationUnit): Unit = unit.body.foreach(visit)
  }

  override final val runsAfter = List(runAfter.entryName)
  override final val phaseName = this.getClass.getSimpleName
}

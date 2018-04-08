package com.rubrik.sanescala.component

import com.rubrik.CompilerPhase
import scala.tools.nsc.Global

/**
 * When two objects of different types are compared, unlike
 * scala's default (of showing warnings), we want to show an error.
 *
 * Also, we want to be stricter than the scala compiler.
 * Look at this code, for example:
 *
 * <code>
 *   val a = "rubrik"
 *   val b = 528
 *   a == b                            // Scala compiler throws warning
 *   List(1, 2).filter(_ == "rubrik")  // Scala compiler doesn't even
 *                                     // throw a warning!
 * </code>
 *
 * In both of the examples above, we desire an error to be thrown
 * instead. While the first one could be converted into an error by
 * simply turning warnings into fatal (for which we aren't ready yet),
 * there doesn't seem to be an easy way of catching the second case.
 *
 * [[TypeSafeEquality()]] ensures that we catch these cases and show
 * appropriate errors.
 */
final class TypeSafeEquality(
  override val global: Global
) extends SaneScalaComponent(
  global = global,
  runAfter = CompilerPhase.Typer
) {
  import global._  // scalastyle:ignore

  override val description: String =
    "Catch instances of objects of unrelated types being compared"

  override def visit(tree: Tree): Unit = {

    def showError(a: Tree, b: Tree): Unit = {
      reporter.error(
        tree.pos,
        s"""Comparing objects of different types:
           |\t$a : ${a.tpe.widen}
           |\t$b : ${b.tpe.widen}
         """.stripMargin
      )
    }

    tree match {
      case q"$a == $b" if shouldShowError(a.tpe, b.tpe) => showError(a, b)
      case q"$a != $b" if shouldShowError(a.tpe, b.tpe) => showError(a, b)
      case q"$a.equals($b)" if shouldShowError(a.tpe, b.tpe) => showError(a, b)
      case _ =>
    }
  }

  private def shouldShowError(tpe1: Type, tpe2: Type): Boolean = {
    !related(tpe1, tpe2) && !(isNull(tpe1) || isNull(tpe2))
  }


  // We define two types to be related if and only if one
  // is a direct descendant of the other.
  private def related(tpe1: Type, tpe2: Type): Boolean = {
    tpe1.widen <:< tpe2.widen || tpe2.widen <:< tpe1.widen
  }

  // We whitelist comparison with null
  // so that the following is valid:
  //
  // require(_id != null, "id must not be null")
  private def isNull(tpe: Type): Boolean = {
    tpe.widen <:< typeOf[Null]
  }
}

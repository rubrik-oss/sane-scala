package com.rubrik.sanescala.component

import com.rubrik.CompilerPhase
import scala.reflect.api.Trees
import scala.tools.nsc.Global

/**
 * Consider the following two cases. Clearly the second traceback is
 * more helpful.
 *
 *   val myMap = Map(1 -> 2)
 *
 *   myMap.get(99).get
 *     java.util.NoSuchElementException: None.get
 *     at scala.None$.get(Option.scala:322)
 *     at scala.None$.get(Option.scala:320)
 *     ... 32 elided
 *
 *   myMap(99)
 *     java.util.NoSuchElementException: key not found: 99
 *     at scala.collection.MapLike$class.default(MapLike.scala:228)
 *     at scala.collection.AbstractMap.default(Map.scala:59)
 *     at scala.collection.MapLike$class.apply(MapLike.scala:141)
 *     at scala.collection.AbstractMap.apply(Map.scala:59)
 *     ... 32 elided
 *
 *
 * For better logs and stack-traces, we want to detect code that is
 * written in the fashion of example (1) and throw a compile time error
 * suggesting that it should be re-written in the fashion of example (2)
 *
 * [[MapGetGet]] does exactly that.
 */
final class MapGetGet(
  override val global: Global
) extends SaneScalaComponent(
  global = global,
  runAfter = CompilerPhase.Typer
) {
  import global._  // scalastyle:ignore

  override val description: String = "Catch instances of Map.get(...).get"

  override def visit(tree: Tree): Unit = {
    tree match {
      case q"$map.get($key).get" if isMapType(map.tpe) =>
        reporter.error(
          map.pos,
          s"""
             |Incorrect usage found  : $map.get($key).get
             |Recommended correction : $map($key)
           """.stripMargin
        )
      case _ =>
    }
  }

  private def isMapType(tpe: Type): Boolean = {
    (tpe <:< typeOf[Map[_, _]]) ||
      (tpe <:< typeOf[collection.Map[_, _]]) ||
      (tpe <:< typeOf[collection.mutable.Map[_, _]])
  }
}

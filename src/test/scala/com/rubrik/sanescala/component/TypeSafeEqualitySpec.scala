package com.rubrik.sanescala.component

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class TypeSafeEqualitySpec extends FlatSpec with Matchers {

  behavior of "TypeSafeEquality"

  it should "catch instances of unrelated types being compared" in {
    TestUtil.testWith(new TypeSafeEquality(_)) {
      """
        |class Animal
        |class Dodo extends Animal
        |class Toad extends Animal
        |
        |object foo {
        |
        |  1 == "rubrik"
        |    ^
        |  List(1, 2, 3).filter(_ == "rubrik")
        |                         ^
        |  List(1, 2, 3).filter(_ != "rubrik")
        |                         ^
        |  List(1, 2, 3).filter("rubrik" != _)
        |                                ^
        |  List(1, 2, 3).filter("rubrik" == _)
        |                                ^
        |  List(1, 2, 3).filter(n => "rubrik".equals(n))
        |                                           ^
        |  List(1, 2, 3).filter(_.equals("rubrik"))
        |                               ^
        |  new Animal == new Dodo
        |  new Animal == new Toad
        |  new Dodo == new Toad
        |           ^
        |
        |  val ToadClass = classOf[Toad]
        |  val DodoClass = classOf[Dodo]
        |
        |  def getClass(): Class[_] = ???
        |
        |  // Instead of running after "typer", if this check ran
        |  // after "patmat", then the following code would throw error,
        |  // which is not what we want.
        |  getClass() match {
        |    case ToadClass => println("toad")
        |    case DodoClass => println("dodo")
        |    case _ => println("something else")
        |  }
        |}
      """
    }
  }
}

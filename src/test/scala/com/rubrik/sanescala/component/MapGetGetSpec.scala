package com.rubrik.sanescala.component

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class MapGetGetSpec extends FlatSpec with Matchers {

  behavior of "MapGetGet"

  it should "catch instances of Map.get(key).get" in {
    TestUtil.testWith(new MapGetGet(_)) {
      """
        |object foo {
        |  val m = Map(1 -> 2)
        |  m.get(3).get
        |  ^
        |}
      """
    }
  }
}

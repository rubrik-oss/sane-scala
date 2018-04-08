package com.rubrik.sanescala.component

import org.scalatest.FlatSpec
import org.scalatest.Matchers

class MapGetGetSpec extends FlatSpec with Matchers {

  behavior of "MapGetGet"

  it should "catch instances of Map.get(key).get" in {
    TestUtil.testWith(new MapGetGet(_)) {
      """
        |import scala.collection.{Map => CollectionMap}
        |import scala.collection.mutable.{Map => MutableMap}
        |import scala.collection.immutable.{Map => ImmutableMap}
        |
        |object foo {
        |  val map = Map(1 -> 2)
        |  val iMap = ImmutableMap(1 -> 2)
        |  val mMap = MutableMap(1 -> 2)
        |  val cMap = CollectionMap(1 -> 2)
        |
        |  map.get(3).get
        |  ^
        |  iMap.get(3).get
        |  ^
        |  mMap.get(3).get
        |  ^
        |  cMap.get(3).get
        |  ^
        |  map(3) + iMap(3) + mMap(3) + cMap(3)
        |}
      """
    }
  }
}

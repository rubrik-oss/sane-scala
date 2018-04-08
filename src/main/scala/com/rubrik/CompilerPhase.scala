package com.rubrik

import enumeratum.Enum
import enumeratum.EnumEntry

sealed abstract class CompilerPhase(
  override val entryName: String
) extends EnumEntry

object CompilerPhase extends Enum[CompilerPhase] {
  val values = findValues

  case object Parser extends CompilerPhase("parser")
  case object Namer extends CompilerPhase("namer")
  case object PackageObjects extends CompilerPhase("packageobjects")
  case object Typer extends CompilerPhase("typer")
  case object PatMat extends CompilerPhase("patmat")
  case object SuperAccessors extends CompilerPhase("superaccessors")
  case object ExtMethods extends CompilerPhase("extmethods")
  case object Pickler extends CompilerPhase("pickler")
  case object Refchecks extends CompilerPhase("refchecks")
  case object Uncurry extends CompilerPhase("uncurry")
  case object Tailcalls extends CompilerPhase("tailcalls")
  case object Specialize extends CompilerPhase("specialize")
  case object ExplicitOuter extends CompilerPhase("explicitouter")
  case object Erasure extends CompilerPhase("erasure")
  case object PostErasure extends CompilerPhase("posterasure")
  case object LazyVals extends CompilerPhase("lazyvals")
  case object LambdaLift extends CompilerPhase("lambdalift")
  case object Constructors extends CompilerPhase("constructors")
  case object Flatten extends CompilerPhase("flatten")
  case object Mixin extends CompilerPhase("mixin")
  case object Cleanup extends CompilerPhase("cleanup")
  case object Delambdafy extends CompilerPhase("delambdafy")
  case object Icode extends CompilerPhase("icode")
  case object Jvm extends CompilerPhase("jvm")
  case object Terminal extends CompilerPhase("terminal")
}

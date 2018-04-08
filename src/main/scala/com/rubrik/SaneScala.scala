package com.rubrik

import com.rubrik.sanescala.component.MapGetGet
import java.util
import scala.collection.SeqLike
import scala.tools.nsc.Global
import scala.tools.nsc.Phase
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.plugins.PluginComponent

class SaneScala(override val global: Global) extends Plugin {
  import global._  // scalastyle:ignore

  override val name = "rubrikScalaStaticChecker"
  override val description = "Static checker for scala codebase"
  override val components: List[PluginComponent] =
    List(
      CustomSyntaxEnforcer,
      new MapGetGet(global)
    )

  private object CustomSyntaxEnforcer extends PluginComponent {
    override val runsAfter = List("refchecks")
    override val phaseName = SaneScala.this.name
    override def newPhase(_prev: Phase): CheckerPhase = {
      new CheckerPhase(_prev)
    }

    override val description = "Ensure various code guidelines are followed."

    override val global: SaneScala.this.global.type =
      SaneScala.this.global

    class CheckerPhase(prev: Phase) extends StdPhase(prev) {
      override def name: String = SaneScala.this.name

      def apply(unit: CompilationUnit): Unit = {
        for (tree <- unit.body) {
          showErrorForAtomicPersistToNodeTable(tree)
          showErrorForUnrelatedTypeComparison(tree)
          showErrorForUnrelatedContains(tree)
          showErrorForInefficientSetUnion(tree)
          showErrorForBangBang(tree)
          showErrorForLongToInt(tree)
        }
      }

      /**
       * We want to disallow atomic persists to the node table, as it is
       * a single partition table with frequent persists across nodes. This
       * causes timeouts (see CDM-79615)
       */
      private def showErrorForAtomicPersistToNodeTable(tree: Tree): Unit = {
        tree match {
          case q"$obj.persist($metadataStore, $conf)" =>
            if (
              obj
                .tpe
                .widen
                .toString
                .contains("com.scaledata.cluster.metadata.Node")
            ) {
              global
                .reporter
                .error(
                  tree.pos,
                  "Atomic persists to the Node table are disallowed. Use " +
                    "persistUnsafe instead."
                )
            }
          case _ =>
        }
      }

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
       * [[showErrorForUnrelatedTypeComparison]] ensures that we catch these
       * cases and show appropriate errors.
       */
      private def showErrorForUnrelatedTypeComparison(tree: Tree): Unit = {

        def relatedTypes(a: Tree, b: Tree): Boolean = {
          a.tpe.widen <:< b.tpe.widen || b.tpe.widen <:< a.tpe.widen
        }

        // We whitelist comparisons of classes
        // so that the following is valid:
        //
        // val VirtualMachineClass = classOf[VirtualMachine]
        // val MssqlClass = classOf[Mssql]
        // wrapper.wrappedClass match {
        //   case VirtualMachineClass => ...
        //   case MssqlClass => ...
        // }
        def isClassType(a: Tree): Boolean = {
          a.tpe.widen <:< typeOf[Class[_]]
        }

        // We whitelist comparison with null
        // so that the following is valid:
        //
        // require(_id != null, "id must not be null")
        def isNull(a: Tree): Boolean = {
          a.tpe.widen <:< typeOf[Null]
        }

        def shouldShowError(a: Tree, b: Tree): Boolean = {
          !relatedTypes(a, b) &&
            !(isClassType(a) && isClassType(b)) &&
            !(isNull(a) || isNull(b))
        }

        def showError(a: Tree, b: Tree): Unit = {
          global.reporter
            .error(
              tree.pos,
              s"Comparing objects of different types:" +
                s"\n\t$a : ${a.tpe.widen}" +
                s"\n\t$b : ${b.tpe.widen}")
        }

        tree match {
          case q"$a == $b" if shouldShowError(a, b) => showError(a, b)
          case q"$a != $b" if shouldShowError(a, b) => showError(a, b)
          case _ =>
        }
      }

      /**
       * <code>
       *   val a: Set[Int] = ...
       *   val b: Set[Int] = ...
       *   val union1: Set[Int] = a ++ b // very inefficient, really bad
       *                                 // performance for large b
       *   val union2: Set[Int] = a | b  // efficient set union,
       *                                 // should be used anywhere instead
       *                                 // of ++
       * </code>
       *
       * [[showErrorForInefficientSetUnion]] ensures that we catch the
       * inefficient code like the one shown in example above and show an
       * error telling the author to use `|` instead of `++`.
       */
      private def showErrorForInefficientSetUnion(tree: Tree): Unit = {
        def showErrorIfSets(possibleSet1: Tree, possibleSet2: Tree): Unit = {
          val setType = typeOf[Set[_]]
          val type1 = possibleSet1.tpe.widen
          val type2 = possibleSet2.tpe.widen
          if (type1 <:< setType && type2 <:< setType) {
            global
              .reporter
              .error(
                tree.pos,
                s"Use | for set-union, not ++. ++ is inefficient.")
          }
        }

        tree match {
          case q"$set1 ++ $set2" => showErrorIfSets(set1, set2)
          case q"$set1.++[$t1, $t2]($set2)" => showErrorIfSets(set1, set2)
          case _ =>
        }
      }

      /**
       * For calls to x.contains(y) where the types of the elements of x and
       * y are unrelated, we want to report an error. Here x may be for
       * example a SeqLike.
       *
       * [[showErrorForUnrelatedContains]] ensures that we catch these
       * cases and show appropriate errors.
       */
      private def showErrorForUnrelatedContains(tree: Tree): Unit = {

        def relatedTypes(a: Type, b: Type): Boolean = {
          a.widen <:< b.widen || b.widen <:< a.widen
        }

        def asContainerType(containerType: Type, container: Tree): Type = {
          container.tpe.baseType(containerType.typeSymbol)
        }

        def shouldShowError(
          containerType: Type,
          container: Tree,
          element: Tree
        ): Boolean = {
          val containerElementType =
            asContainerType(containerType, container).typeArgs.head
          !relatedTypes(containerElementType, element.tpe)
        }

        def showError(
          containerType: Type,
          container: Tree,
          element: Tree
        ): Unit = {
          val containerBaseType = asContainerType(containerType, container)
          global.reporter
            .error(
              tree.pos,
              s"Types unrelated in contains call:" +
                s"\n\t$container : ${container.tpe.widen} " +
                s"(base class $containerBaseType)" +
                s"\n\t$element : ${element.tpe.widen}")
        }

        def checkContains(
          containerType: Type,
          container: Tree,
          element: Tree
        ): Unit = {
          if (shouldShowError(containerType, container, element)) {
            showError(containerType, container, element)
          }
        }

        val seqLikeType = typeOf[SeqLike[_, _]]
        val optionType = typeOf[Option[_]]
        val javaCollectionType = typeOf[util.Collection[_]]

        def handleContains(container: Tree, element: Tree): Unit = {
          container match {
            case _ if container.tpe.widen <:< seqLikeType =>
              checkContains(seqLikeType, container, element)
            case _ if container.tpe.widen <:< optionType =>
              checkContains(optionType, container, element)
            case _ if container.tpe.widen <:< javaCollectionType =>
              checkContains(javaCollectionType, container, element)
            // String, SetLike, and MapLike are type-checked so we don't need
            // to worry about them
            case _ =>
          }
        }

        tree match {
          case q"$container.contains[$_]($element)" =>
            handleContains(container, element)
          case q"$container.contains($element)" =>
            handleContains(container, element)
          case _ =>
        }
      }

      /**
       * <code> Seq("/program/that/returns/non-zero/exit/code").!! </code>
       * The code above throws error that looks like like the following.
       *
       * java.lang.RuntimeException: Nonzero exit value: 1
       *   scala.sys.package$.error(package.scala:27)
       *   scala.sys.process.ProcessBuilder...
       *
       * Note how it is not possible to figure out the failing command
       * from the trace. This problem worsens when commands are not obvious
       * from the source code. Like, <code> Seq(cmd1, cmd2).!! </code>.
       *
       * [[showErrorForBangBang]] ensures that we catch the usage of `!!`
       * and show an error telling the author to use the `getOutput` function.
       */
      private def showErrorForBangBang(tree: Tree): Unit = {
        tree match {
          case q"$cmd.!!" =>
            global
              .reporter
              .error(
                tree.pos,
                "Use com.scaledata.util.executeCmdWithOutput instead.")
          case _ =>
        }
      }

      /**
       * Scala silently ignores overflow while downcasting
       * a [[Long]] to an [[Int]]. That is never desirable, as
       * we never expect a negative [[Int]] from a positive
       * [[Long]]. This diff adds a toInt function that
       * is equivalent to Long.toInt but throws an exception
       * in case the value is out of bounds.
       *
       * [[showErrorForLongToInt]] detects cases where `.toInt`
       * is called on a [[Long]] and throws a compile time error.
       */
      private def showErrorForLongToInt(tree: Tree): Unit = {
        tree match {
          case q"$long.toInt" =>
            if (long.tpe <:< typeOf[Long]) {
              global.reporter.error(
                tree.pos,
                s"\nPossible overflow : $long.toInt" +
                  s"\nRecommended       : com.scaledata.util.toInt($long)")
            }
          case _ =>
        }
      }
    }
  }
}

package com.runtimeverification.rvmonitor.logicpluginshells.dl

import edu.cmu.cs.ls.keymaerax.Configuration
import edu.cmu.cs.ls.keymaerax.bellerophon._
import edu.cmu.cs.ls.keymaerax.btactics.ModelPlex.createMonitorSpecificationConjecture
import edu.cmu.cs.ls.keymaerax.btactics.{ModelPlex, SimplifierV3, _}
import edu.cmu.cs.ls.keymaerax.btactics.TactixLibrary._
import edu.cmu.cs.ls.keymaerax.btactics.SimplifierV3._
import edu.cmu.cs.ls.keymaerax.core.{BaseVariable, _}
import edu.cmu.cs.ls.keymaerax.parser.StringConverter._
import edu.cmu.cs.ls.keymaerax.parser._
import edu.cmu.cs.ls.keymaerax.core._
import edu.cmu.cs.ls.keymaerax.codegen._
import edu.cmu.cs.ls.keymaerax.launcher.DefaultConfiguration


/**
  * Connects to ModelPlex for dL monitoring C code synthesis
  */
object ModelPlexConnector {
  def init():Unit = {
    BelleInterpreter.setInterpreter(ExhaustiveSequentialInterpreter())
    PrettyPrinter.setPrinter(KeYmaeraXPrettyPrinter.pp)

    val generator = new ConfigurableGenerator[Formula]()
    KeYmaeraXParser.setAnnotationListener((p: Program, inv: Formula) =>
      generator.products += (p->(generator.products.getOrElse(p, Nil) :+ inv)))
    TactixLibrary.invGenerator = generator
  }

  def getMathematica():MathematicaToolProvider = {
    val mathLinkTcp = System.getProperty(Configuration.Keys.MATH_LINK_TCPIP, "true") // JVM parameter -DMATH_LINK_TCPIP=[true,false]
    Configuration.set(Configuration.Keys.MATH_LINK_TCPIP, mathLinkTcp, saveToFile = false)
    Configuration.set(Configuration.Keys.QE_TOOL, "mathematica", saveToFile=false)
    val provider = new MathematicaToolProvider(DefaultConfiguration.currentMathematicaConfig)
    ToolProvider.setProvider(provider)
    return provider
  }
  def shutdown():Unit = {
    ToolProvider.shutdown()
    ToolProvider.setProvider(new NoneToolProvider())
    TactixLibrary.invGenerator = FixedGenerator(Nil)
  }

  // PV (Program Variables) and End are KeYmaeraX keywords for declaring variables
  abstract class State
  case class BeforePVSeen()    extends State
  case class PVSeenEndUnSeen() extends State
  case class EndAfterPVSeen()  extends State

  def getVariablesFromModelKyx(modelKyxLines:List[String]):List[String] = {
    val variableLineRegex = "R\\s+([A-Za-z0-9]+)\\s*\\.".r

    def getVariableFromLine(str: String):Option[List[String]] =
      str match {
        case variableLineRegex(v) => Some(List(v))
        case _ => None
      }

    modelKyxLines.foldLeft(Tuple2(BeforePVSeen().asInstanceOf[State], List[String]()))({
      (stateTup, line) =>
        val trimmedLine = line.trim()
        stateTup._1 match {
          case BeforePVSeen()    => if(trimmedLine.startsWith("ProgramVariables")) {
                                      Tuple2(PVSeenEndUnSeen(), stateTup._2)
                                    } else {
                                      stateTup
                                    }
          case PVSeenEndUnSeen() => if(trimmedLine.startsWith("R")) {
                                      Tuple2(PVSeenEndUnSeen()
                                              ,getVariableFromLine(trimmedLine).getOrElse(List()) ::: stateTup._2)
                                    } else {
                                      stateTup
                                    }
          case EndAfterPVSeen()  => stateTup
        }
    })._2
  }

  /**
    * The LLVM instrumentation fails due to this function.
    * Since it's unused during monitoring, it's removed from the synthesized monitoring code.
    */

  def sanitizeC(unsanitizedInput:String):String = {
    unsanitizedInput.split("(?<=\\n)").foldLeft(Tuple2(false, ""))(
      {(tup2, line) => if(tup2._1 && line.contains("}")) {
                          Tuple2(false, tup2._2)
                       } else if(!tup2._1 && line.contains("Run controller")) {
                          Tuple2(true, tup2._2)
                       } else if(!tup2._1) {
                          Tuple2(false, tup2._2 + line)
                       } else {
                          tup2
                       }
      })._2
  }

  def synthesizeCModelMonitor(keymaeraxModel:String):String = {
     init()
     val model = KeYmaeraXArchiveParser.parseAsProblemOrFormula(keymaeraxModel)
     val Imply(_, Box(prg, _)) = model
     val stateVars = getVariablesFromModelKyx(keymaeraxModel.split("\n")
                      .toList.filter(!_.equals("")))
                      .map(_.asVariable.asInstanceOf[BaseVariable])
     val (modelplexInput, assumptions) = createMonitorSpecificationConjecture(model, stateVars:_*)
     val simplifier = SimplifierV3.simpTac(taxs=composeIndex(groundEqualityIndex,defaultTaxs))
     val provider = getMathematica()
     val tactic = ModelPlex.modelMonitorByChase(1) & ModelPlex.optimizationOneWithSearch(Some(provider.tool), assumptions)(1) & simplifier(1)
     val result = proveBy(modelplexInput, tactic)
     val inputs = CGenerator.getInputs(prg)
     CPrettyPrinter.printer = new CExpressionPlainPrettyPrinter()
     val code = (new CMonitorGenerator())(result.subgoals.head.succ.head, stateVars.toSet, inputs, "Monitor")
     shutdown()
     sanitizeC(code._1)
  }

}

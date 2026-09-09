import mill._
import mill.modules.Util
import mill.scalalib._
import mill.scalalib.TestModule.ScalaTest
import $ivy.`com.lihaoyi::mill-contrib-bloop:`

import mill.bsp._

private val scalaVer = "2.13.14"
private val chiselVer = "6.7.0"

/**
 * Compile Rocket Chip and DiffTest directly from their pinned submodules.
 *
 * Both projects normally own a top-level Mill build. Importing those builds
 * as nested `$file` modules changes Mill's namespace and makes their own
 * `$file` imports resolve incorrectly. Keeping the small source-module graph
 * here avoids patches in either submodule and leaves every gitlink clean and
 * reproducible.
 */
trait Chisel67Module extends ScalaModule {
  override def scalaVersion = scalaVer
  override def ivyDeps = Agg(ivy"org.chipsalliance::chisel:$chiselVer")
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:$chiselVer"
  )
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-Ymacro-annotations",
    "-deprecation",
    "-feature",
    "-P:chiselplugin:genBundleElements"
  )
}

object rocketCde extends ScalaModule {
  override def millSourcePath = os.pwd / "rocket-chip" / "dependencies" / "cde" / "cde"
  override def scalaVersion = scalaVer
}

object rocketMacros extends ScalaModule {
  override def millSourcePath = os.pwd / "rocket-chip" / "macros"
  override def scalaVersion = scalaVer
  override def ivyDeps = Agg(ivy"org.scala-lang:scala-reflect:$scalaVer")
}

object rocketHardfloat extends Chisel67Module {
  override def millSourcePath = os.pwd / "rocket-chip" / "dependencies" / "hardfloat" / "hardfloat"
}

object rocketDiplomacy extends Chisel67Module {
  override def millSourcePath = os.pwd / "rocket-chip" / "dependencies" / "diplomacy" / "diplomacy"
  override def moduleDeps = Seq(rocketCde)
  override def ivyDeps = super.ivyDeps() ++ Agg(ivy"com.lihaoyi::sourcecode:0.3.1")
  override def scalacOptions = super.scalacOptions() ++ Seq("-Wunused")
}

object rocketChipDep extends Chisel67Module {
  override def millSourcePath = os.pwd / "rocket-chip"
  override def resources = T.sources {
    millSourcePath / "src" / "main" / "resources"
  }
  override def moduleDeps = Seq(rocketMacros, rocketHardfloat, rocketDiplomacy)
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"com.lihaoyi::mainargs:0.5.0",
    ivy"org.json4s::json4s-jackson:4.0.5"
  )
}

object difftestDep extends Chisel67Module {
  override def millSourcePath = os.pwd / "difftest"
  override def sources = T.sources {
    millSourcePath / "src" / "main" / "scala"
  }
  override def scalacOptions = super.scalacOptions() ++ Seq("-Ymacro-annotations")
}

object boom extends Chisel67Module {
  override def millSourcePath = os.pwd
  override def sources = T.sources {
    millSourcePath / "src" / "main" / "scala" / "v4"
  }
  override def moduleDeps = Seq(difftestDep, rocketChipDep)
  override def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"ch.epfl.scala::bloop-config:2.0.3"
  )
  override def scalacOptions = super.scalacOptions() ++ Seq("-Xcheckinit")
}

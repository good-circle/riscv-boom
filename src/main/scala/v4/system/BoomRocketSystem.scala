package boom.v4.system

import chisel3._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.devices.tilelink.{CanHavePeripheryCLINT, CanHavePeripheryPLIC}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.util.DontTouch
import boom.v4.common._

trait HasBoomTiles { this: BaseSubsystem with InstantiatesHierarchicalElements =>
  val boomTiles = totalTiles.values.collect { case b: BoomTile => b }
  def coreMonitorBundles = boomTiles.map(_.module.core.coreMonitorBundle).toList
}

/** A Rocket-Chip subsystem carrying the existing BOOM v4 tile. */
class BoomRocketSubsystem(implicit p: Parameters) extends BaseSubsystem
    with InstantiatesHierarchicalElements
    with HasTileNotificationSinks
    with HasTileInputConstants
    with CanHavePeripheryCLINT
    with CanHavePeripheryPLIC
    with HasPeripheryDebug
    with HasHierarchicalElementsRootContext
    with HasHierarchicalElements
    with HasBoomTiles {
  override lazy val module = new BoomRocketSubsystemModuleImp(this)
}

class BoomRocketSubsystemModuleImp[+L <: BoomRocketSubsystem](_outer: L)
    extends BaseSubsystemModuleImp(_outer)
    with HasHierarchicalElementsRootContextModuleImp {
  override lazy val outer = _outer
}

/** Simulation-facing BOOM system with legacy CLINT/PLIC and AXI ports. */
class BoomRocketSystem(implicit p: Parameters) extends BoomRocketSubsystem
    with CanHaveMasterAXI4MemPort
    with CanHaveMasterAXI4MMIOPort {
  override lazy val module = new BoomRocketSystemModuleImp(this)
}

class BoomRocketSystemModuleImp[+L <: BoomRocketSystem](_outer: L)
    extends BoomRocketSubsystemModuleImp(_outer)
    with HasRTCModuleImp
    with DontTouch

package boom.v4.system

import chisel3._
import chisel3.util.{Cat, log2Ceil}
import difftest.common.DifftestMem
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.lazymodule.{LazyModule, LazyModuleImp, SimpleLazyModule}

import freechips.rocketchip.amba.AMBACorrupt
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy.{AddressSet, RegionType, TransferSizes}
import freechips.rocketchip.resources.DiplomaticSRAM
import freechips.rocketchip.subsystem.{CanHaveMasterAXI4MemPort, ExtMem}
import freechips.rocketchip.util._

/** AXI4 memory backed by DiffTest's DPI memory helper.
  *
  * The helper exposes the same backing storage to the emulator image loader,
  * which is the essential difference from Rocket's synthesizable AXI4RAM.
  */
class DifftestAXI4RAM(
    address: AddressSet,
    cacheable: Boolean = true,
    executable: Boolean = true,
    beatBytes: Int = 8,
    errors: Seq[AddressSet] = Nil,
    wcorrupt: Boolean = true)(implicit p: Parameters)
    extends DiplomaticSRAM(address, beatBytes, Some("difftest-memory")) {

  val node = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    slaves = Seq(AXI4SlaveParameters(
      address = List(address) ++ errors,
      resources = resources,
      regionType = if (cacheable) RegionType.UNCACHED else RegionType.IDEMPOTENT,
      executable = executable,
      supportsRead = TransferSizes(1, beatBytes),
      supportsWrite = TransferSizes(1, beatBytes),
      interleavedId = Some(0))),
    beatBytes = beatBytes,
    requestKeys = if (wcorrupt) Seq(AMBACorrupt) else Seq.empty,
    minLatency = 1)))

  private val outer = this
  lazy val module = new Impl

  class Impl extends LazyModuleImp(this) {
    val (in, edgeIn) = node.in.head
    val laneDataBits = 8
    val mem = DifftestMem(
      size = BigInt(1) << mask.count(identity),
      lanes = beatBytes,
      bits = laneDataBits,
      singlePort = false)
    val address = outer.address

    val corrupt = Option.when(edgeIn.bundle.requestFields.contains(AMBACorrupt))(
      SyncReadMem(BigInt(1) << mask.count(identity), UInt(2.W)))

    val rAddr = Cat((mask zip (in.ar.bits.addr >> log2Ceil(beatBytes)).asBools)
      .filter(_._1).map(_._2).reverse)
    val wAddr = Cat((mask zip (in.aw.bits.addr >> log2Ceil(beatBytes)).asBools)
      .filter(_._1).map(_._2).reverse)
    val rSelected = address.contains(in.ar.bits.addr)
    val wSelected = address.contains(in.aw.bits.addr)

    val writeOutstanding = RegInit(false.B)
    val writeId = Reg(UInt())
    val writeEcho = Reg(BundleMap(in.params.echoFields))
    val writeSelected = Reg(Bool())

    when(in.b.fire) { writeOutstanding := false.B }
    when(in.aw.fire) {
      writeOutstanding := true.B
      writeId := in.aw.bits.id
      writeSelected := wSelected
      writeEcho :<= in.aw.bits.echo
    }

    val writeData = VecInit.tabulate(beatBytes)(i => in.w.bits.data(8 * (i + 1) - 1, 8 * i))
    when(in.aw.fire && wSelected) {
      mem.write(wAddr, writeData, in.w.bits.strb.asBools)
      corrupt.foreach(_.write(wAddr, in.w.bits.user(AMBACorrupt).asUInt))
    }

    in.b.valid := writeOutstanding
    in.aw.ready := in.w.valid && (in.b.ready || !writeOutstanding)
    in.w.ready := in.aw.valid && (in.b.ready || !writeOutstanding)
    in.b.bits.id := writeId
    in.b.bits.resp := Mux(writeSelected, AXI4Parameters.RESP_OKAY, AXI4Parameters.RESP_DECERR)
    in.b.bits.echo :<= writeEcho

    val readOutstanding = RegInit(false.B)
    val readId = Reg(UInt())
    val readEcho = Reg(BundleMap(in.params.echoFields))
    val readSelected = Reg(Bool())

    when(in.r.fire) { readOutstanding := false.B }
    when(in.ar.fire) {
      readOutstanding := true.B
      readId := in.ar.bits.id
      readSelected := rSelected
      readEcho :<= in.ar.bits.echo
    }

    val readEnable = in.ar.fire
    val readData = mem.readAndHold(rAddr, readEnable)
    val readCorrupt = corrupt.map(_.readAndHold(rAddr, readEnable)(0)).getOrElse(false.B)

    in.r.valid := readOutstanding
    in.ar.ready := in.r.ready || !readOutstanding
    in.r.bits.id := readId
    in.r.bits.resp := Mux(readSelected,
      Mux(readCorrupt, AXI4Parameters.RESP_SLVERR, AXI4Parameters.RESP_OKAY),
      AXI4Parameters.RESP_DECERR)
    in.r.bits.data := Cat(readData.reverse)
    in.r.bits.echo :<= readEcho
    in.r.bits.last := true.B
  }
}

/** Harness-side adapter for every configured external memory channel. */
class SimDifftestAXIMem(edge: AXI4EdgeParameters, size: BigInt, base: BigInt)(implicit p: Parameters)
    extends SimpleLazyModule {
  val node = AXI4MasterNode(List(edge.master))
  val xbarX = AXI4Xbar()
  AddressSet.misaligned(base, size).foreach { address =>
    val ram = LazyModule(new DifftestAXI4RAM(
      address = address,
      beatBytes = edge.bundle.dataBits / 8,
      wcorrupt = edge.slave.requestKeys.contains(AMBACorrupt)))
    ram.node := AXI4Buffer() := AXI4Fragmenter() := xbarX
  }
  xbarX := node
  val io_axi4 = org.chipsalliance.diplomacy.lazymodule.InModuleBody { node.makeIOs() }
}

object SimDifftestAXIMem {
  def connectMem(dut: CanHaveMasterAXI4MemPort)(implicit p: Parameters): Seq[SimDifftestAXIMem] = {
    dut.mem_axi4.zip(dut.memAXI4Node.in).map { case (io, (_, edge)) =>
      val params = p(ExtMem).get.master
      val mem = LazyModule(new SimDifftestAXIMem(edge, params.size, params.base))
      Module(mem.module).suggestName("mem")
      mem.io_axi4.head <> io
      mem
    }.toSeq
  }
}

package boom.v4.system

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.subsystem.{CanHaveMasterAXI4MMIOPort, ExtBus}
import freechips.rocketchip.util.BundleMap

/** Minimal ns16550-compatible AXI UART used by OpenSBI/Linux console output. */
class SimAXIUart(edge: AXI4EdgeParameters, base: BigInt) extends Module {
  val io = IO(Flipped(new AXI4Bundle(edge.bundle)))
  val out = IO(Output(Valid(UInt(8.W))))

  val beatBytes = edge.bundle.dataBits / 8
  val laneBits = log2Ceil(beatBytes)
  val dlab = RegInit(false.B)
  val dll = RegInit(0.U(8.W))
  val dlm = RegInit(0.U(8.W))
  val ier = RegInit(0.U(8.W))

  val writeOutstanding = RegInit(false.B)
  val writeId = Reg(UInt())
  val writeEcho = Reg(BundleMap(io.params.echoFields))
  val writeFire = io.aw.fire
  val writeOffset = (io.aw.bits.addr - base.U)(7, 0)
  val writeLane = if (beatBytes == 1) 0.U else io.aw.bits.addr(laneBits - 1, 0)
  val writeBytes = io.w.bits.data.asTypeOf(Vec(beatBytes, UInt(8.W)))
  val writeByte = writeBytes(writeLane)
  val writeStrobes = VecInit(io.w.bits.strb.asBools)
  val writeEnabled = writeStrobes(writeLane)

  out.valid := writeFire && writeEnabled && writeOffset === 0.U && !dlab
  out.bits := writeByte

  when(writeFire && writeEnabled) {
    switch(writeOffset) {
      is(0.U) { when(dlab) { dll := writeByte } }
      is(1.U) { when(dlab) { dlm := writeByte }.otherwise { ier := writeByte } }
      is(3.U) { dlab := writeByte(7) }
    }
  }

  when(io.b.fire) { writeOutstanding := false.B }
  when(writeFire) {
    writeOutstanding := true.B
    writeId := io.aw.bits.id
    writeEcho :<= io.aw.bits.echo
  }
  io.aw.ready := io.w.valid && (io.b.ready || !writeOutstanding)
  io.w.ready := io.aw.valid && (io.b.ready || !writeOutstanding)
  io.b.valid := writeOutstanding
  io.b.bits := DontCare
  io.b.bits.id := writeId
  io.b.bits.resp := AXI4Parameters.RESP_OKAY
  io.b.bits.echo :<= writeEcho

  val readOutstanding = RegInit(false.B)
  val readId = Reg(UInt())
  val readEcho = Reg(BundleMap(io.params.echoFields))
  val readData = Reg(UInt(edge.bundle.dataBits.W))
  val readOffset = (io.ar.bits.addr - base.U)(7, 0)
  val readLane = if (beatBytes == 1) 0.U else io.ar.bits.addr(laneBits - 1, 0)
  val readByte = WireDefault(0.U(8.W))
  switch(readOffset) {
    is(0.U) { readByte := Mux(dlab, dll, 0.U) }
    is(1.U) { readByte := Mux(dlab, dlm, ier) }
    is(3.U) { readByte := dlab << 7 }
    // THR empty and transmitter empty: firmware may always make progress.
    is(5.U) { readByte := "h60".U }
  }
  val laneShift = readLane << 3

  when(io.r.fire) { readOutstanding := false.B }
  when(io.ar.fire) {
    readOutstanding := true.B
    readId := io.ar.bits.id
    readEcho :<= io.ar.bits.echo
    readData := readByte << laneShift
  }
  io.ar.ready := io.r.ready || !readOutstanding
  io.r.valid := readOutstanding
  io.r.bits := DontCare
  io.r.bits.id := readId
  io.r.bits.data := readData
  io.r.bits.resp := AXI4Parameters.RESP_OKAY
  io.r.bits.echo :<= readEcho
  io.r.bits.last := true.B
}

object SimAXIUart {
  def connectMMIO(dut: CanHaveMasterAXI4MMIOPort)(implicit p: Parameters): Seq[Valid[UInt]] = {
    dut.mmio_axi4.zip(dut.mmioAXI4Node.in).map { case (port, (_, edge)) =>
      val uart = Module(new SimAXIUart(edge, p(ExtBus).get.base)).suggestName("uart")
      uart.io <> port
      uart.out
    }.toSeq
  }
}

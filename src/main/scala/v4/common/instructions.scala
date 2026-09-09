package boom.v4.common

import chisel3.util.BitPat

/** Non-standard instructions supported by BOOM simulation workloads. */
object BoomCustomInstructions {
  // NEMU simulation termination instruction. Architectural a0 carries the
  // exit status.
  def NEMU_TRAP = BitPat("b?????????????????000?????1101011")
}

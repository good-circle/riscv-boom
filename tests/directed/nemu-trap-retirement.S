// Build each TRAP_CODE/PAD variant separately; NEMU_TRAP ends the run.
#ifndef TRAP_CODE
#define TRAP_CODE 0
#endif
#ifndef PAD
#define PAD 0
#endif
.option norvc
.section .text
.globl _start
_start:
  li a0, 0x55
  .rept PAD
  addi t0, zero, 1
  .endr
  li a0, TRAP_CODE
.globl exit_trap
exit_trap:
  .word 0x0005006b
  // These younger writes must never appear in the exit snapshot.
  li a0, 0x7f
  li a1, 0x123
1:
  j 1b

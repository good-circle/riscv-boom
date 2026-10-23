![](docs/figures/evolution.png)

The Berkeley Out-of-Order RISC-V Processor [![CircleCI](https://circleci.com/gh/riscv-boom/riscv-boom.svg?style=svg)](https://circleci.com/gh/riscv-boom/riscv-boom)
====================================================================================================================================================================

The Berkeley Out-of-Order Machine (BOOM) is a synthesizable and parameterizable open source RV64GC RISC-V core written in the [Chisel](https://chisel.eecs.berkeley.edu/) hardware construction language.
Created at the University of California,
Berkeley in the [Berkeley Architecture Research](https://bar.eecs.berkeley.edu/) group, its focus is to create a high performance, synthesizable, and parameterizable core for architecture research.
The current version of the BOOM microarchitecture ([SonicBOOM, or BOOMv3](https://carrv.github.io/2020/papers/CARRV2020_paper_15_Zhao.pdf)) is performance competitive with commercial high-performance out-of-order cores, achieving 6.2 CoreMarks/MHz.

![](docs/figures/uarch.png)


Feature | BOOM
--- | ---
ISA | RISC-V (RV64GCB)
Synthesizable |√
FPGA |√
Parameterized |√
IEEE 754 Floating Point |√
Atomics |√
Caches |√
Virtual Memory |√
Boots Linux |√
Runs SPEC |√
CoreMark/MHz |6.2


## IMPORTANT: Using BOOM
This repository is **NOT A SELF-RUNNING** repository. To instantiate a BOOM core, please use the
[Chipyard](https://github.com/ucb-bar/chipyard) SoC generator.

The current hash of Chipyard that works with this repository is located in the `CHIPYARD.hash`
file in the top level directory of this repository. This file is mainly used for CI purposes, since
Chipyard should follow the correct version of rocket-chip. For most users, you should be able to
clone Chipyard separately and follow the default Chipyard instructions (without having to use the `.hash` file).

While BOOM is primarily ASIC-optimized, it is also usable on FPGAs.
Chipyard provides infrastructure and documentation for deploying BOOM on AWS F1 FPGAs through FireSim.

## Documentation and Information

Please check out the BOOM website @ https://boom-core.org for the most up-to-date information.
It contains links to the mailing lists, documentation, design spec., publications and more!

If you use BOOMv3 in your published work, please cite BOOM as

```
@article{zhaosonicboom,
  title={SonicBOOM: The 3rd Generation Berkeley Out-of-Order Machine},
  author={Zhao, Jerry and Korpan, Ben and Gonzalez, Abraham and Asanovic, Krste},
  booktitle={Fourth Workshop on Computer Architecture Research with RISC-V},
  year={2020},
  month={May}
}
```

**Website:** (www.boom-core.org)

**Mailing List** (https://groups.google.com/forum/#!forum/riscv-boom)

## Disclaimer!

BOOM is a work-in-progress and remains in active development.

## Contributing

Please see [CONTRIB\_AND\_STYLE.md](/CONTRIB_AND_STYLE.md)

## Standalone DiffTest simulation on `dev-difftest`

The `dev-difftest` branch adds a standalone BOOM v4 Verilator harness alongside
the Chipyard flow described above. It supports single-hart `SmallBoomConfig`,
`MediumBoomConfig` (the default), and `LargeBoomConfig`, with F/D floating point,
Zba/Zbb/Zbs, Sv39, and eight PMP regions at four-byte granularity. The harness
provides 256 MiB DRAM at `0x80000000`, CLINT at `0x02000000`, PLIC at
`0x0c000000`, and an ns16550 UART at `0x10000000`.

Install JDK 17, Mill 0.11.1, Verilator 5, a C/C++ toolchain, Make, and Python 3.
Linux payload builds additionally require the workload-builder dependencies,
including `dtc`. Initialize the pinned Rocket-Chip and DiffTest sources before
building:

```sh
git clone --branch dev-difftest https://github.com/poemonsense/riscv-boom.git
cd riscv-boom
make init
make emu CONFIG=SmallBoomConfig -j"$(nproc)"
```

Use `make verilog CONFIG=SmallBoomConfig` to generate RTL only. Outputs are
isolated by configuration under `build/boom-<CONFIG>/build/`; the emulator is
`build/boom-<CONFIG>/build/emu`. `BUILD_DIR` can select another output directory.

For the reference model, use an OpenXiangShan NEMU checkout containing
`configs/riscv64-boom-ref_defconfig`. Assuming NEMU and workload-builder are
siblings of this repository, build the shared reference from the BOOM config:

```sh
export NEMU_HOME="$(realpath ../NEMU)"
make -C "$NEMU_HOME" riscv64-boom-ref_defconfig
make -C "$NEMU_HOME" -j"$(nproc)"
```

This configuration enables SoftFloat F/D, floating-point CSR comparison, eight
active PMP entries, Sv39, and the harness memory map. Use it instead of a
NutShell reference: both the floating-point register layout and ISA/PMP
configuration must match the DUT. DiffTest compares integer and floating-point
registers, FCSR, privileged CSR state, retirement, traps, and LR/SC events.
Device accesses and implementation-dependent performance counters are skipped.

To build and run the Linux hello payload, the workload-builder checkout must
contain `dts/boom.dts.in`:

```sh
make -C ../workload-builder linux/hello DEFAULT_DTB=boom -j"$(nproc)"
build/boom-SmallBoomConfig/build/emu \
  -i ../workload-builder/build/linux-workloads/hello/fw_payload.bin \
  --diff "$NEMU_HOME/build/riscv64-nemu-interpreter-so" --ram-size=256MB
```

Repeat `make emu` and the run command with `MediumBoomConfig` or `LargeBoomConfig`
to select another core. A completed hello run prints `Hello, world!` and
`HIT GOOD TRAP`; a cycle-limited run alone does not establish Linux completion.
The simulation exit instruction is `.word 0x0005006b`, with the exit code in
`a0`.

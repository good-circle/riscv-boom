# BOOM directed retirement regression

Build the current preview RTL and emulator first:

```sh
make emu CONFIG=MediumBoomMem2GConfig
```

Build each exit-code/alignment case as a separate raw image. `PAD=0..3`
changes the trap's position among consecutive uncompressed instructions;
actual ROB-slot coverage must be checked from RTL traces.

```sh
mkdir -p build/directed
for code in 0 1 0x100 0x101 0x100000000; do
  for pad in 0 1 2 3; do
    name="build/directed/trap-${code}-${pad}"
    riscv64-linux-gnu-gcc -nostdlib -nostartfiles -static \
      -march=rv64im -mabi=lp64 -Wl,--build-id=none,-Ttext=0x80000000 \
      -DTRAP_CODE="$code" -DPAD="$pad" \
      tests/directed/nemu-trap-retirement.S -o "$name.elf"
    riscv64-linux-gnu-objcopy -O binary "$name.elf" "$name.bin"
  done
done
```

Run each image using the matched BOOM REF and `--ram-size=2GB`:

```sh
build/boom-MediumBoomMem2GConfig/build/emu \
  -i build/directed/trap-0-0.bin --ram-size=2GB \
  --diff "$NEMU_HOME/build/riscv64-nemu-interpreter-so"
```

The zero-code cases must report GOOD TRAP. Nonzero cases must report a
failing exit with the exact 64-bit code; `0x100` and `0x101` are exits in
this test, not profiling markers. Compare trap PC against `exit_trap` in
the ELF and check that the exit snapshot has `a0=TRAP_CODE`, never `0x55`
or `0x7f`. Use the trace to verify that the trap is the last architectural
commit in its cycle and that its younger `a0`/`a1` writes did not retire.
A fixed instruction limit or assembler success alone does not pass this test.

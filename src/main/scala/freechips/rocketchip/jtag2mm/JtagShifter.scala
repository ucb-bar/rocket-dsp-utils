// See ./LICENSE for license details.

package freechips.rocketchip.jtag2mm

import chisel3._
import chisel3.reflect.DataMirror
import chisel3.internal.firrtl.KnownWidth
import chisel3.util._

// This code was taken from https://github.com/ucb-art/chisel-jtag/blob/master/src/main/scala/jtag/jtagShifter.scala and adjusted to our design needs

/** Base JTAG shifter IO, viewed from input to shift register chain.
  * Can be chained together.
  */
class ShifterIO extends Bundle {
  val shift = Bool() // advance the scan chain on clock high
  val data =
    Bool() // as input: bit to be captured into shifter MSB on next rising edge; as output: value of shifter LSB
  val capture = Bool() // high in the CaptureIR/DR state when this chain is selected
  val update = Bool() // high in the UpdateIR/DR state when this chain is selected

  /** Sets a output shifter IO's control signals from a input shifter IO's control signals.
    */
  def chainControlFrom(in: ShifterIO): Unit = {
    shift := in.shift
    capture := in.capture
    update := in.update
  }
}

trait ChainIO extends Bundle {
  val chainIn = Input(new ShifterIO)
  val chainOut = Output(new ShifterIO)
}

class Capture[+T <: Data](private val gen: T) extends Bundle {
  val bits = Input(gen) // data to capture, should be always valid
  val capture = Output(Bool()) // will be high in capture state (single cycle), captured on following rising edge
}

object Capture {
  def apply[T <: Data](gen: T): Capture[T] = new Capture(gen)
}

/** Trait that all JTAG chains (data and instruction registers) must extend, providing basic chain
  * IO.
  */
trait Chain extends Module {
  val io: ChainIO
}

/** One-element shift register, data register for bypass mode.
  *
  * Implements Clause 10.
  */
class JtagBypassChain extends Chain {
  class ModIO extends ChainIO
  val io = IO(new ModIO)
  io.chainOut.chainControlFrom(io.chainIn)

  val reg = Reg(Bool()) // 10.1.1a single shift register stage

  io.chainOut.data := reg

  when(io.chainIn.capture) {
    reg := false.B // 10.1.1b capture logic 0 on TCK rising
  }.elsewhen(io.chainIn.shift) {
    reg := io.chainIn.data
  }
  assert(
    !(io.chainIn.capture && io.chainIn.update)
      && !(io.chainIn.capture && io.chainIn.shift)
      && !(io.chainIn.update && io.chainIn.shift)
  )
}

object JtagBypassChain {
  def apply() = new JtagBypassChain
}

/** Simple shift register with parallel capture only, for read-only data registers.
  *
  * Number of stages is the number of bits in gen, which must have a known width.
  *
  * Useful notes:
  * 7.2.1c shifter shifts on TCK rising edge
  * 4.3.2a TDI captured on TCK rising edge, 6.1.2.1b assumed changes on TCK falling edge
  */
class CaptureChain[+T <: Data](gen: T) extends Chain {
  class ModIO extends ChainIO {
    val capture = Capture(gen)
  }
  val io = IO(new ModIO)
  io.chainOut.chainControlFrom(io.chainIn)

  val n = DataMirror.widthOf(gen) match {
    case KnownWidth(x) => x
    case _             => require(false, s"can't generate chain for unknown width data type $gen"); -1 // TODO: remove -1 type hack
  }

  val regs = (0 until n).map(x => Reg(Bool()))

  io.chainOut.data := regs(0)

  when(io.chainIn.capture) {
    (0 until n).map(x => regs(x) := io.capture.bits.asUInt(x))
    io.capture.capture := true.B
  }.elsewhen(io.chainIn.shift) {
    regs(n - 1) := io.chainIn.data
    (0 until n - 1).map(x => regs(x) := regs(x + 1))
    io.capture.capture := false.B
  }.otherwise {
    io.capture.capture := false.B
  }
  assert(
    !(io.chainIn.capture && io.chainIn.update)
      && !(io.chainIn.capture && io.chainIn.shift)
      && !(io.chainIn.update && io.chainIn.shift)
  )
}

object CaptureChain {
  def apply[T <: Data](gen: T) = new CaptureChain(gen)
}

/** Simple shift register with parallel capture and update. Useful for general instruction and data
  * scan registers.
  *
  * Number of stages is the max number of bits in genCapture and genUpdate, both of which must have
  * known widths. If there is a width mismatch, the unused most significant bits will be zero.
  *
  * Useful notes:
  * 7.2.1c shifter shifts on TCK rising edge
  * 4.3.2a TDI captured on TCK rising edge, 6.1.2.1b assumed changes on TCK falling edge
  */
class CaptureUpdateChain[+T <: Data, +V <: Data](genCapture: T, genUpdate: V) extends Chain {
  class ModIO extends ChainIO {
    val capture = Capture(genCapture)
    val update = Valid(genUpdate) // valid high when in update state (single cycle), contents may change any time after
  }
  val io = IO(new ModIO)
  io.chainOut.chainControlFrom(io.chainIn)

  val captureWidth = DataMirror.widthOf(genCapture) match {
    case KnownWidth(x) => x
    case _ =>
      require(false, s"can't generate chain for unknown width data type $genCapture"); -1 // TODO: remove -1 type hack
  }
  val updateWidth = DataMirror.widthOf(genUpdate) match {
    case KnownWidth(x) => x
    case _ =>
      require(false, s"can't generate chain for unknown width data type $genUpdate"); -1 // TODO: remove -1 type hack
  }
  val n = math.max(captureWidth, updateWidth)

  val regs = (0 until n).map(x => Reg(Bool()))

  io.chainOut.data := regs(0)

  if (updateWidth > 0) {
    val updateBits = Cat(regs.reverse)(updateWidth - 1, 0)
    io.update.bits := updateBits.asTypeOf(io.update.bits)
  } else {
    io.update.bits := 0.U
  }

  val captureBits = io.capture.bits.asUInt

  when(io.chainIn.capture) {
    (0 until math.min(n, captureWidth)).map(x => regs(x) := captureBits(x))
    (captureWidth until n).map(x => regs(x) := 0.U)
    io.capture.capture := true.B
    io.update.valid := false.B
  }.elsewhen(io.chainIn.update) {
    io.capture.capture := false.B
    io.update.valid := true.B
  }.elsewhen(io.chainIn.shift) {
    regs(n - 1) := io.chainIn.data
    (0 until n - 1).map(x => regs(x) := regs(x + 1))
    io.capture.capture := false.B
    io.update.valid := false.B
  }.otherwise {
    io.capture.capture := false.B
    io.update.valid := false.B
  }
  assert(
    !(io.chainIn.capture && io.chainIn.update)
      && !(io.chainIn.capture && io.chainIn.shift)
      && !(io.chainIn.update && io.chainIn.shift)
  )
}

object CaptureUpdateChain {

  /** Capture-update chain with matching capture and update types.
    */
  def apply[T <: Data](gen:                   T) = new CaptureUpdateChain(gen, gen)
  def apply[T <: Data, V <: Data](genCapture: T, genUpdate: V) =
    new CaptureUpdateChain(genCapture, genUpdate)
}

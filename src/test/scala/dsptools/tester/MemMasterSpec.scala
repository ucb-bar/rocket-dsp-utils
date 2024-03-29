package dsptools.tester

import chisel3._
import chiseltest.{ChiselScalatestTester, VerilatorBackendAnnotation}
import chiseltest.iotesters.PeekPokeTester
import dspblocks.MemMasterModel
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait RegmapExample extends HasRegMap {
  val r0 = RegInit(0.U(64.W))
  val r1 = RegInit(1.U(64.W))

  regmap(
    0x00 -> Seq(RegField(64, r0)),
    0x08 -> Seq(RegField(64, r1)),
    0x10 -> Seq(RegField(64, r0)),
    0x18 -> Seq(RegField(64, r1))
  )
}

class TLRegmapExample
    extends TLRegisterRouter(0, "example", Seq("dsptools", "example"), beatBytes = 8, interrupts = 1)(
      new TLRegBundle(null, _)
    )(new TLRegModule(null, _, _) with RegmapExample)(Parameters.empty) {
  //TODO: CHIPYARD check usage of echoFields, requestFields, responseFields
  def standaloneParams = {
    TLBundleParameters(
      addressBits = 64,
      dataBits = 64,
      sourceBits = 1,
      sinkBits = 1,
      sizeBits = 6,
      echoFields = Seq.empty,
      requestFields = Seq.empty,
      responseFields = Seq.empty,
      hasBCE = false
    )
  }

  val ioMemNode = BundleBridgeSource(() => TLBundle(standaloneParams))
  node :=
    BundleBridgeToTL(TLClientPortParameters(Seq(TLClientParameters("bundleBridgeToTL")))) :=
    ioMemNode
  val ioMem = InModuleBody { ioMemNode.makeIO() }

  val ioIntNode = BundleBridgeSink[Vec[Bool]]()
  ioIntNode :=
    IntToBundleBridge(IntSinkPortParameters(Seq(IntSinkParameters()))) :=
    intnode
  val ioInt = InModuleBody {
    import chisel3.experimental.IO
    val io = IO(Output(ioIntNode.bundle.cloneType))
    io.suggestName("int")
    io := ioIntNode.bundle
    io
  }
}

class AXI4RegmapExample
    extends AXI4RegisterRouter(0, beatBytes = 8, interrupts = 1)(new AXI4RegBundle(null, _))(
      new AXI4RegModule(null, _, _) with RegmapExample
    )(Parameters.empty) {
  def standaloneParams = {
    //TODO: CHIPYARD, check values for echoFields, requestFields, and responseFields
    AXI4BundleParameters(
      addrBits = 64,
      dataBits = 64,
      idBits = 1
//      userBits = 0,
//      wcorrupt = false
    )
  }

  val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))
  node :=
    BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
    ioMemNode
  val ioMem = InModuleBody { ioMemNode.makeIO() }

  val ioIntNode = BundleBridgeSink[Vec[Bool]]()
  ioIntNode :=
    IntToBundleBridge(IntSinkPortParameters(Seq(IntSinkParameters()))) :=
    intnode
  val ioInt = InModuleBody {
    import chisel3.experimental.IO
    val io = IO(Output(ioIntNode.bundle.cloneType))
    io.suggestName("int")
    io := ioIntNode.bundle
    io
  }
}

class APBRegmapExample
    extends APBRegisterRouter(0, beatBytes = 8, interrupts = 1)(new APBRegBundle(null, _))(
      new APBRegModule(null, _, _) with RegmapExample
    )(Parameters.empty) {
  def standaloneParams = APBBundleParameters(addrBits = 64, dataBits = 64)

  val ioMemNode = BundleBridgeSource(() => APBBundle(standaloneParams))
  node :=
    BundleBridgeToAPB(APBMasterPortParameters(Seq(APBMasterParameters("bundleBridgeToAPB")))) :=
    ioMemNode
  val ioMem = InModuleBody { ioMemNode.makeIO() }

  val ioIntNode = BundleBridgeSink[Vec[Bool]]()
  ioIntNode :=
    IntToBundleBridge(IntSinkPortParameters(Seq(IntSinkParameters()))) :=
    intnode
  val ioInt = InModuleBody {
    import chisel3.experimental.IO
    val io = IO(Output(ioIntNode.bundle.cloneType))
    io.suggestName("int")
    io := ioIntNode.bundle
    io
  }
}

class MemMasterSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  abstract class RegmapExampleTester[M <: Module](c: M) extends PeekPokeTester(c) with MemMasterModel {
    memReadWord(0x00) should be(0)
    memReadWord(0x08) should be(1)
    memReadWord(0x10) should be(0)
    memReadWord(0x18) should be(1)
    memWriteWord(0, 10)
    memWriteWord(8, 5)
    memReadWord(0x00) should be(10)
    memReadWord(0x08) should be(5)
    memReadWord(0x10) should be(10)
    memReadWord(0x18) should be(5)
  }

  class TLRegmapExampleTester(c: TLRegmapExample) extends RegmapExampleTester(c.module) with TLMasterModel {
    def memTL = c.ioMem
  }

  class AXI4RegmapExampleTester(c: AXI4RegmapExample) extends RegmapExampleTester(c.module) with AXI4MasterModel {
    def memAXI = c.ioMem
  }

  class APBRegmapExampleTester(c: APBRegmapExample) extends RegmapExampleTester(c.module) with APBMasterModel {
    def memAPB = c.ioMem
  }

//  behavior of "MemMaster Tester"
//
//  it should "work with TileLink" in {
//    lazy val dut = LazyModule(new TLRegmapExample)
//    // use verilog b/c of verilog blackboxes in TileLink things
//      test(dut.module)
//        .withAnnotations(Seq(VerilatorBackendAnnotation))
//        .runPeekPoke(_ => new TLRegmapExampleTester(dut))
//  }
//
//  it should "work with AXI-4" in {
//    lazy val dut = LazyModule(new AXI4RegmapExample)
//    test(dut.module).runPeekPoke(_ => new AXI4RegmapExampleTester(dut))
//  }
//
//  it should "work with APB" in {
//    lazy val dut = LazyModule(new APBRegmapExample)
//    test(dut.module).runPeekPoke(_ => new APBRegmapExampleTester(dut))
//  }
}

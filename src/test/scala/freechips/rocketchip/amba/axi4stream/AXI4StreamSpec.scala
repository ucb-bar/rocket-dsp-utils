package freechips.rocketchip.amba.axi4stream

import breeze.stats.distributions._
import Rand.FixedSeed._
import chisel3._
import chiseltest.{ChiselScalatestTester, VerilatorBackendAnnotation}
import chiseltest.iotesters.PeekPokeTester
import dspblocks.MemMasterModel
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

trait TMModuleImp {
  val sinkBundle: AXI4StreamBundle
  val edge:       AXI4StreamEdgeParameters
  val out:        AXI4StreamBundle
}

class TestModule(
  val inP: AXI4StreamBundleParameters,
  outP:    AXI4StreamSlaveParameters,
  func:    (AXI4StreamMasterNode, Parameters) => AXI4StreamNodeHandle
) extends Module {
  implicit val p: Parameters = Parameters.empty
  import DoubleToBigIntRand._
  val transactions = AXI4StreamTransaction.defaultSeq(100).map(_.randData(Uniform(0, 65535)))

  val lazyMod = LazyModule(new LazyModule() {
    val fuzzer = AXI4StreamFuzzer.bundleParams(transactions, inP)
    val outNode = AXI4StreamSlaveNode(outP)
    outNode := func(fuzzer, p)

    lazy val module = new LazyModuleImp(this) with TMModuleImp {
      override val (sinkBundle, edge) = outNode.in.head

      override val out = IO(AXI4StreamBundle(sinkBundle.params))

      out <> sinkBundle
    }
  })

  val mod = Module(lazyMod.module)

  val io = IO(new Bundle {
    val out = new AXI4StreamBundle(mod.edge.bundle)
  })

  io.out <> mod.out
}

class TestModuleTester(
  c: TestModule,
  expectTranslator: Seq[AXI4StreamTransaction] => Seq[AXI4StreamTransactionExpect] = {
    _.map(t => AXI4StreamTransactionExpect(data = Some(t.data)))
  }
) extends PeekPokeTester(c)
    with AXI4StreamSlaveModel[TestModule] {
  bindSlave(c.io.out).addExpects(
    expectTranslator(c.transactions)
  )
  stepToCompletion()
}

abstract class StreamMuxTester[D, U, EO, EI, B <: Data](c: StreamMux[D, U, EO, EI, B] with MuxInOuts[D, U, EO, EI, B])
    extends PeekPokeTester(c.module)
    with AXI4StreamModel[LazyModuleImp with SMModuleImp]
    with MemMasterModel {

  val inMasters = c.ins.map(in => bindMaster(in.getWrappedValue))
  val outSlaves = c.outIOs.map(out => bindSlave(out.getWrappedValue))
  c.ins.map(i => resetMaster(i))
  c.outIOs.map(o => resetMaster(o))

  for ((in, inIdx) <- inMasters.zipWithIndex) {
    in.addTransactions(Seq.fill(outSlaves.length)(AXI4StreamTransaction(data = inIdx)))
  }
  for ((out, outIdx) <- outSlaves.zipWithIndex) {}

  for (offset <- 0 until c.module.ins.length) {
    // set the input->output mapping
    for (outIdx <- 0 until c.module.outs.length) {
      memWriteWord(c.beatBytes * outIdx, (offset + outIdx) % c.module.ins.length)
    }
    for ((out, outIdx) <- outSlaves.zipWithIndex) {
      out.addExpects(Seq(AXI4StreamTransactionExpect(data = Some((offset + outIdx) % inMasters.length))))
    }
    step(20)
  }
  stepToCompletion(silentFail = true)
}

trait MuxInOuts[D, U, EO, EI, B <: Data] {
  self: StreamMux[D, U, EO, EI, B] =>
  def nIn:  Int
  def nOut: Int

  val ins: Seq[ModuleValue[AXI4StreamBundle]] = for (i <- 0 until nIn) yield {
    implicit val valName = ValName(s"inIOs_$i")
    val in = BundleBridgeSource[AXI4StreamBundle](() => AXI4StreamBundle(AXI4StreamBundleParameters(n = 8)))
    streamNode :=
      BundleBridgeToAXI4Stream(AXI4StreamMasterPortParameters(AXI4StreamMasterParameters())) :=
      in
    InModuleBody { in.makeIO() }
  }
  val outIOs: Seq[ModuleValue[AXI4StreamBundle]] = for (o <- 0 until nOut) yield {
    implicit val valName = ValName(s"outIOs_$o")
    val out = BundleBridgeSink[AXI4StreamBundle]()
    out :=
      AXI4StreamToBundleBridge(AXI4StreamSlavePortParameters(AXI4StreamSlaveParameters())) :=
      streamNode
    InModuleBody { out.makeIO() }
  }
}
trait AXI4MuxInOuts
    extends AXI4StreamMux
    with MuxInOuts[
      AXI4MasterPortParameters,
      AXI4SlavePortParameters,
      AXI4EdgeParameters,
      AXI4EdgeParameters,
      AXI4Bundle
    ] {
  val ioMem = mem.map { m =>
    {
      val ioMemNode =
        BundleBridgeSource(() => AXI4Bundle(AXI4BundleParameters(addrBits = 32, dataBits = beatBytes * 8, idBits = 1)))

      m :=
        BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
        ioMemNode

      val ioMem = InModuleBody { ioMemNode.makeIO() }
      ioMem
    }
  }
}

trait TLMuxInOuts
    extends TLStreamMux
    with MuxInOuts[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle] {
  val ioMem = mem.map { m =>
    {
      val ioMemNode = BundleBridgeSource(() =>
        TLBundle(
          TLBundleParameters(
            addressBits = 32,
            dataBits = beatBytes * 8,
            sourceBits = 1,
            sinkBits = 1,
            sizeBits = 1,
            //TODO: CHIPYARD: figure this out
            echoFields = Seq.empty,
            requestFields = Seq.empty,
            responseFields = Seq.empty,
            hasBCE = false
          )
        )
      )

      m :=
        BundleBridgeToTL(TLClientPortParameters(Seq(TLClientParameters("bundleBridgeToAXI4")))) :=
        ioMemNode

      val ioMem = InModuleBody { ioMemNode.makeIO() }
      ioMem
    }
  }
}

class AXI4StreamMuxTester(c: AXI4StreamMux with AXI4MuxInOuts) extends StreamMuxTester(c) with AXI4MasterModel {
  override def memAXI: AXI4Bundle = c.ioMem.get.getWrappedValue
}

class TLStreamMuxTester(c: TLStreamMux with TLMuxInOuts) extends StreamMuxTester(c) with TLMasterModel {
  override def memTL: TLBundle = c.registerNode.in.head._1
}

class AXI4StreamSpec extends AnyFlatSpec with ChiselScalatestTester with Matchers {

  def axi4(nInputs: Int, nOutputs: Int): Unit = {
    val lm = LazyModule(new AXI4StreamMux(AddressSet(0x0, 0xff), beatBytes = 4) with AXI4MuxInOuts {
      def nIn = nInputs

      def nOut = nOutputs
    })
    test(lm.module).runPeekPoke(_ => new AXI4StreamMuxTester(lm))
  }

  def tl(nInputs: Int, nOutputs: Int): Unit = {
    val lm = LazyModule(new TLStreamMux(AddressSet(0x0, 0xff), beatBytes = 4) with TLMuxInOuts {
      def nIn = nInputs

      def nOut = nOutputs
    })
    test(lm.module)
      .withAnnotations(Seq(VerilatorBackendAnnotation))
      .runPeekPoke(_ => new TLStreamMuxTester(lm))
  }

//  behavior of "AXI4 Stream Nodes"
//  it should "work with fuzzer and identity" in {
//    val inP  = AXI4StreamBundleParameters(n = 2)
//    val outP = AXI4StreamSlaveParameters()
//    def func(in: AXI4StreamMasterNode, p: Parameters) = {
//      implicit val pp = p
//      val identity = AXI4StreamIdentityNode()
//      identity := in
//
//    }
//
//    test(new TestModule(inP, outP, func)).runPeekPoke(new TestModuleTester(_))
//  }
//
//  it should "work with adapter that shrinks data" in {
//    val inP  = AXI4StreamBundleParameters(n = 2)
//    val outP = AXI4StreamSlaveParameters()
//    def func(in: AXI4StreamMasterNode, p: Parameters) = {
//      implicit val pp = p
//      val adapter = AXI4StreamWidthAdapter(
//        p => AXI4StreamAdapterNode.widthAdapter(p, _ => 1),
//        s => s
//      )
//      adapter := in
//    }
//
//    test(new TestModule(inP, outP, func)).runPeekPoke {
//      c => new TestModuleTester(c, expectTranslator = _.map(t => AXI4StreamTransactionExpect(data = Some(t.data & 0xFF))))
//    }
//  }
//
//  it should "work with one-to-two adapter" in {
//    val inP  = AXI4StreamBundleParameters(n = 2)
//    val outP = AXI4StreamSlaveParameters()
//    def func(in: AXI4StreamMasterNode, x: Parameters) = {
//      implicit val p = Parameters.empty
//      val adapter = AXI4StreamWidthAdapter.oneToN(2)
//      adapter := in
//    }
//    def expectTranslator(ts: Seq[AXI4StreamTransaction]): Seq[AXI4StreamTransactionExpect] = {
//      ts.flatMap(t => Seq((t.data >> 0) & 0xFF, (t.data >> 8) & 0xFF))
//        .map(t => AXI4StreamTransactionExpect(data = Some(t)))
//    }
//    test(new TestModule(inP, outP, func)).runPeekPoke {
//      c => new TestModuleTester(c, expectTranslator = expectTranslator)
//    }
//  }
//
//  it should "work with two-to-one adapter" in {
//    val inP  = AXI4StreamBundleParameters(n = 2)
//    val outP = AXI4StreamSlaveParameters()
//    def func(in: AXI4StreamMasterNode, x: Parameters) = {
//      implicit val p = Parameters.empty
//      val adapter = AXI4StreamWidthAdapter.nToOne(2)
//      adapter := in
//    }
//    def expectTranslator(ts: Seq[AXI4StreamTransaction]): Seq[AXI4StreamTransactionExpect] = {
//      ts.map(_.data & 0xFFFF).grouped(2).map({ case l :: r :: Nil =>
//        AXI4StreamTransactionExpect(data = Some((r << 16) | l))
//      }).toSeq
//    }
//    test(new TestModule(inP, outP, func)).runPeekPoke {
//      c => new TestModuleTester(c, expectTranslator = expectTranslator)
//    }
//  }
//
//  for (i <- 2 until 10) {
//    it should s"work with one-to-$i to $i-to-one adapters back to back" in {
//      val inP  = AXI4StreamBundleParameters(n = 2)
//      val outP = AXI4StreamSlaveParameters()
//      def func(in: AXI4StreamMasterNode, x: Parameters) = {
//        implicit val p = Parameters.empty
//        AXI4StreamWidthAdapter.oneToN(i) := AXI4StreamBuffer() := AXI4StreamWidthAdapter.nToOne(i) := in
//      }
//      def expectTranslator(ts: Seq[AXI4StreamTransaction]): Seq[AXI4StreamTransactionExpect] = {
//        ts.map(t => AXI4StreamTransactionExpect(data = Some(t.data & 0xFFFF)))
//      }
//      test(new TestModule(inP, outP, func)).runPeekPoke {
//        c => new TestModuleTester(c, expectTranslator = expectTranslator)
//      }
//    }
//  }
//
//  behavior of "AXI4-Stream Mux"
//
//  for (ins <- 1 to 5) {
//    for (outs <- 1 to 5) {
//      it should s"work with $ins masters and $outs slaves" in {
//        axi4(ins, outs)
//      }
//    }
//  }
}

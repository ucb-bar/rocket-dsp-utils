package dspblocks

import chisel3._
import chisel3.util.{log2Ceil, Queue}
import freechips.rocketchip.amba.ahb._
import freechips.rocketchip.amba.apb._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream.AXI4StreamIdentityNode
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.interrupts._

trait DspQueue[D, U, EO, EI, B <: Data] extends DspBlock[D, U, EO, EI, B] {
  this: RegisterRouter with HasInterruptSources =>

  /**
    * Depth of queue
    */
  val depth: Int

  def nInterrupts = 2

  require(depth > 0)

  override val streamNode: AXI4StreamIdentityNode = AXI4StreamIdentityNode()

  override lazy val module = new LazyModuleImp(this) {
    val (streamIn, streamEdgeIn) = streamNode.in.head
    val (streamOut, streamEdgeOut) = streamNode.out.head

    val queuedStream = Queue(streamIn, entries = depth)
    streamOut <> queuedStream

    val queueEntries = RegInit(UInt(log2Ceil(depth + 1).W), 0.U)
    queueEntries := queueEntries + streamIn.fire - streamOut.fire

    val queueThreshold = WireInit(UInt(64.W), depth.U)
    val queueFilling = queueEntries >= queueThreshold
    val queueFull = queueEntries >= depth.U

    interrupts := VecInit(queueFilling, queueFull)

    regmap(
      0 ->
        Seq(
          RegField(
            64,
            queueThreshold,
            RegFieldDesc("queueThreshold", "Threshold for number of elements to throw interrupt")
          )
        )
    )
  }
}

class TLDspQueue(val depth: Int, val baseAddr: BigInt = 0, devname: String = "vqueue", concurrency: Int = 1)(implicit
  p:                        Parameters
) extends RegisterRouter(RegisterRouterParams(devname, Nil, baseAddr, beatBytes = 8, concurrency = concurrency))(p)
    with HasTLControlRegMap
    with HasInterruptSources
    with DspQueue[TLClientPortParameters, TLManagerPortParameters, TLEdgeOut, TLEdgeIn, TLBundle]
    with TLDspBlock {
  val mem = Some(controlNode)
}

class AXI4DspQueue(val depth: Int, val baseAddr: BigInt = 0, concurrency: Int = 4)(implicit p: Parameters)
    extends RegisterRouter(RegisterRouterParams("vqueue", Nil, baseAddr, beatBytes = 8, concurrency = concurrency))(p)
    with HasAXI4ControlRegMap
    with HasInterruptSources
    with DspQueue[AXI4MasterPortParameters, AXI4SlavePortParameters, AXI4EdgeParameters, AXI4EdgeParameters, AXI4Bundle]
    with AXI4DspBlock {
  val mem = Some(controlNode)
}

class AHBDspQueue(val depth: Int, val baseAddr: BigInt = 0, concurrency: Int = 4)(implicit p: Parameters)
    extends RegisterRouter(RegisterRouterParams("vqueue", Nil, baseAddr, beatBytes = 8, concurrency = concurrency))(p)
    with HasAHBControlRegMap
    with HasInterruptSources
    with DspQueue[AHBMasterPortParameters, AHBSlavePortParameters, AHBEdgeParameters, AHBEdgeParameters, AHBSlaveBundle]
    with AHBSlaveDspBlock {
  val mem = Some(controlNode)
}

class APBDspQueue(val depth: Int, val baseAddr: BigInt = 0, concurrency: Int = 4)(implicit p: Parameters)
    extends RegisterRouter(RegisterRouterParams("vqueue", Nil, baseAddr, beatBytes = 8, concurrency = concurrency))
    with HasAPBControlRegMap
    with HasInterruptSources
    with DspQueue[APBMasterPortParameters, APBSlavePortParameters, APBEdgeParameters, APBEdgeParameters, APBBundle]
    with APBDspBlock {
  val mem = Some(controlNode)
}

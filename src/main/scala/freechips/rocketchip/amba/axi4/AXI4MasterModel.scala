package freechips.rocketchip.amba.axi4

import chisel3._
import chisel3.util.IrrevocableIO
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util.BundleMap

object AXI4MasterModel {
  case class AWChannel(
                        id: BigInt                = 0,
                        addr: BigInt              = 0,
                        len: BigInt               = 0,
                        size: BigInt              = 0,
                        burst: BigInt             = 0,
                        lock: BigInt              = 0,
                        cache: BigInt             = 0,
                        prot: BigInt              = 0,
                        qos: BigInt               = 0,
                        region: BigInt            = 0,
                        user: Map[String, BigInt] = Map()
                      )
  case class ARChannel(
                        id: BigInt                = 0,
                        addr: BigInt              = 0,
                        len: BigInt               = 0,
                        size: BigInt              = 0,
                        burst: BigInt             = 0,
                        lock: BigInt              = 0,
                        cache: BigInt             = 0,
                        prot: BigInt              = 0,
                        qos: BigInt               = 0,
                        region: BigInt            = 0,
                        user: Map[String, BigInt] = Map()
                      )
  case class WChannel(
                       data: BigInt = 0,
                       strb: BigInt = 0,
                       last: BigInt = 0
                     )
  case class RChannel(
                       id: BigInt                = 0,
                       data: BigInt              = 0,
                       resp: BigInt              = 0,
                       last: BigInt              = 0,
                       user: Map[String, BigInt] = Map()
                     )
  case class BChannel(
                       id: BigInt                = 0,
                       resp: BigInt              = 0,
                       user: Map[String, BigInt] = Map()
                     )

  val BRESP_OKAY   = BigInt(0)
  val BRESP_EXOKAY = BigInt(1)
  val BRESP_SLVERR = BigInt(2)
  val BRESP_DECERR = BigInt(3)

  val RRESP_OKAY   = BigInt(0)
  val RRESP_EXOKAY = BigInt(1)
  val RRESP_SLVERR = BigInt(2)
  val RRESP_DECERR = BigInt(3)
}


package freechips.rocketchip.amba.axi4stream

import breeze.stats.distributions._
import chisel3.MultiIOModule

import scala.language.implicitConversions

object DoubleToBigIntRand {
  implicit def apply(r: Rand[Double]): Rand[BigInt] = new Rand[BigInt] {
    def draw(): BigInt = { BigDecimal(r.draw()).toBigInt() }
  }
}

case class AXI4StreamTransaction
(
  data: BigInt = 0,
  last: Boolean = false,
  strb: BigInt = -1,
  keep: BigInt = -1,
  user: BigInt = 0,
  id:   BigInt = 0,
  dest: BigInt = 0
) {
  def randData(dataDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(data = dataDist.draw())
  }
  def randLast(lastDist: Rand[Boolean] = Rand.always(false)): AXI4StreamTransaction = {
    copy(last = lastDist.draw())
  }
  def randStrb(strbDist: Rand[BigInt] = Rand.always(-1)): AXI4StreamTransaction = {
    copy(strb = strbDist.draw())
  }
  def randKeep(keepDist: Rand[BigInt] = Rand.always(-1)): AXI4StreamTransaction = {
    copy(keep = keepDist.draw())
  }
  def randUser(userDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(user = userDist.draw())
  }
  def randId(idDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(id = idDist.draw())
  }
  def randDest(destDist: Rand[BigInt] = Rand.always(0)): AXI4StreamTransaction = {
    copy(dest = destDist.draw())
  }
}

object AXI4StreamTransaction {
  def rand(
           dataDist : Rand[BigInt]  = Rand.always(0),
           lastDist:  Rand[Boolean] = Rand.always(false),
           strbDist:  Rand[BigInt]  = Rand.always(-1),
           keepDist:  Rand[BigInt]  = Rand.always(-1),
           userDist:  Rand[BigInt]  = Rand.always(0),
           idDist:    Rand[BigInt]  = Rand.always(0),
           destDist:  Rand[BigInt]  = Rand.always(0)
           ): AXI4StreamTransaction = {
    AXI4StreamTransaction(
      data = dataDist.draw(),
      last = lastDist.draw(),
      strb = strbDist.draw(),
      keep = keepDist.draw(),
      user = userDist.draw(),
      id   = idDist.draw(),
      dest = destDist.draw()
    )
  }

  def randSeq(
             n: Int,
             dataDist : Rand[BigInt]  = Rand.always(0),
             lastDist:  Rand[Boolean] = Rand.always(false),
             strbDist:  Rand[BigInt]  = Rand.always(-1),
             keepDist:  Rand[BigInt]  = Rand.always(-1),
             userDist:  Rand[BigInt]  = Rand.always(0),
             idDist:    Rand[BigInt]  = Rand.always(0),
             destDist:  Rand[BigInt]  = Rand.always(0)
             ): Seq[AXI4StreamTransaction] = {
    Seq.fill(n) { AXI4StreamTransaction.rand(
      dataDist = dataDist,
      lastDist = lastDist,
      strbDist = strbDist,
      keepDist = keepDist,
      userDist = userDist,
      idDist = idDist,
      destDist = destDist
    )}
  }

  def defaultSeq(n: Int): Seq[AXI4StreamTransaction] = Seq.fill(n)(AXI4StreamTransaction())
  def linearSeq(n: Int): Seq[AXI4StreamTransaction]  = Seq.tabulate(n)(AXI4StreamTransaction(_))
}

case class AXI4StreamTransactionExpect
(
  data: Option[BigInt]  = None,
  last: Option[Boolean] = None,
  strb: Option[BigInt]  = None,
  keep: Option[BigInt]  = None,
  user: Option[BigInt]  = None,
  id:   Option[BigInt]  = None,
  dest: Option[BigInt]  = None
)

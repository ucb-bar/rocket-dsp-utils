// SPDX-License-Identifier: Apache-2.0

package freechips.rocketchip.tilelink

import chisel3.util.log2Ceil

object TLMasterModel {
  case class AChannel(
    opcode:  BigInt = 0, // PutFullData
    param:   BigInt = 0, // toT
    size:    BigInt = 3,
    source:  BigInt = 0,
    address: BigInt = 0,
    mask:    BigInt = 0xff,
    data:    BigInt = 0)

  case class BChannel(
    opcode:  BigInt = 0,
    param:   BigInt = 0,
    size:    BigInt = 0,
    source:  BigInt = 0,
    address: BigInt = 0,
    mask:    BigInt = 0,
    data:    BigInt = 0)

  case class CChannel(
    opcode:  BigInt = 0,
    param:   BigInt = 0,
    size:    BigInt = 0,
    source:  BigInt = 0,
    address: BigInt = 0,
    data:    BigInt = 0,
    corrupt:   Boolean = false)

  case class DChannel(
    opcode:  BigInt = 0,
    param:   BigInt = 0,
    size:    BigInt = 0,
    source:  BigInt = 0,
    sink:    BigInt = 0,
    data:    BigInt = 0,
    corrupt:   Boolean = false)

  case class EChannel(
    sink: BigInt = 0)
}


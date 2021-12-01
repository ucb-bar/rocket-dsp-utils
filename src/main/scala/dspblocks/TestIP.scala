package dspblocks

import breeze.math.Complex
import chisel3._
import chisel3.experimental.FixedPoint
import chisel3.internal.firrtl.KnownBinaryPoint
import dsptools._
import dsptools.numbers._


trait MemTester {
  def resetMem(): Unit
  def readAddr(addr: BigInt): BigInt
  def writeAddr(addr: BigInt, value: BigInt): Unit
  def writeAddr(addr: Int, value: Int): Unit = writeAddr(BigInt(addr), BigInt(value))
}
package npc

import chisel3._
import chisel3.util._

class ysyx_26030092_PCReg extends Module{
    val pc      = IO(Output(UInt(32.W)))
    val next_pc = IO(Input(UInt(32.W)))
    val adv     = IO(Input(Bool()))
    val jump    = IO(Input(Bool()))

    val u = Module(new ysyx_26030092_PCBlackBox)
    u.io.clock   := clock
    u.io.reset   := reset
    u.io.pc_next := next_pc
    u.io.pc_we   := jump
    u.io.pc_4    := adv && !jump
    pc := u.io.pc
}

class ysyx_26030092_PCCompare extends Module{
    val hit      = IO(Output(Bool()))
    val flush    = IO(Output(Bool()))

    val pc        = IO(Input(UInt(32.W)))
    val pc_reg    = IO(Input(UInt(32.W)))
    val pc_valid  = IO(Input(Bool()))
    val lsu_trap  = IO(Input(Bool()))

    val pc_hit = !pc_valid || pc === pc_reg

    hit   := pc_hit
    flush := !pc_hit || lsu_trap
}

package npc

import chisel3._
import chisel3.util._

class branchBundle extends Bundle{
    val rs1       = UInt(32.W)
    val rs2       = UInt(32.W)
    val csr_rdata = UInt(32.W)
    val branch_op = BranchOp()
    val jump_op   = JumpOp()
    val sys_op    = SysOp()
    val is_excp   = Bool()
}

class ysyx_26030092_BranchUnit extends Module{
    val jump     = IO(Output(Bool()))
    val adv      = IO(Output(Bool()))
    val redirect = IO(Output(Bool()))
    val next_pc  = IO(Output(UInt(32.W)))
    val dnpc     = IO(Output(UInt(32.W)))

    val pc        = IO(Input(UInt(32.W)))
    val alu       = IO(Input(UInt(32.W)))
    val mtvec     = IO(Input(UInt(32.W)))
    val lsu_trap  = IO(Input(Bool()))
    val fire      = IO(Input(Bool()))
    val idu_in    = IO(Input(new branchBundle))

    val blt  = idu_in.rs1.asSInt < idu_in.rs2.asSInt
    val bltu = idu_in.rs1 < idu_in.rs2
    val branch_cond = MuxLookup(idu_in.branch_op, false.B)(Seq(
        BranchOp.beq  -> (idu_in.rs1 === idu_in.rs2),
        BranchOp.bne  -> (idu_in.rs1 =/= idu_in.rs2),
        BranchOp.blt  -> blt,
        BranchOp.bge  -> !blt,
        BranchOp.bltu -> bltu,
        BranchOp.bgeu -> !bltu))

    val is_jump = idu_in.jump_op === JumpOp.jump
    val is_mret = idu_in.sys_op === SysOp.mret
    val taken   = branch_cond || is_jump

    redirect := taken || is_mret || idu_in.is_excp || lsu_trap
    jump     := lsu_trap || (redirect && fire)
    adv      := fire
    next_pc  := Mux(lsu_trap, mtvec, MuxCase(0.U, Seq(
        taken   -> alu,
        is_mret -> idu_in.csr_rdata,
        idu_in.is_excp -> mtvec)))
    dnpc     := Mux(redirect, next_pc, pc + 4.U)
}

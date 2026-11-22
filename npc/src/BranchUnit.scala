package npc

import chisel3._
import chisel3.util._

class ysyx_26030092_BranchUnit extends Module{
    val flush      = IO(Output(Bool()))
    val redirectPC = IO(Output(UInt(32.W)))
    val dnpc       = IO(Output(UInt(32.W)))

    val pc        = IO(Input(UInt(32.W)))
    val alu       = IO(Input(UInt(32.W)))
    val rs1       = IO(Input(UInt(32.W)))
    val rs2       = IO(Input(UInt(32.W)))
    val csr_rdata = IO(Input(UInt(32.W)))
    val mtvec     = IO(Input(UInt(32.W)))

    val branch_op = IO(Input(BranchOp()))
    val jump_op   = IO(Input(JumpOp()))
    val sys_op    = IO(Input(SysOp()))
    val is_excp   = IO(Input(Bool()))

    val valid    = IO(Input(Bool()))
    val fire     = IO(Input(Bool()))
    val lsu_trap = IO(Input(Bool()))

    // 面积优化：bge/bgeu 用 blt/bltu 取反，共用比较器
    val blt  = rs1.asSInt < rs2.asSInt
    val bltu = rs1 < rs2
    val branch_cond = MuxLookup(branch_op, false.B)(Seq(
        BranchOp.beq  -> (rs1 === rs2),
        BranchOp.bne  -> (rs1 =/= rs2),
        BranchOp.blt  -> blt,
        BranchOp.bge  -> !blt,
        BranchOp.bltu -> bltu,
        BranchOp.bgeu -> !bltu))

    val is_branch = branch_op =/= BranchOp.none
    val is_jump   = jump_op === JumpOp.jump
    val is_mret   = sys_op === SysOp.mret
    val taken     = branch_cond || is_jump

    val shadow_pc = Module(new ysyx_26030092_PCBlackBox)
    val pc_hit    = shadow_pc.io.pc === pc
    val jump      = lsu_trap || ((taken || is_mret || is_excp) && fire && pc_hit)
    val next_pc   = Mux(lsu_trap, mtvec, MuxCase(0.U, Seq(
        taken   -> alu,
        is_mret -> csr_rdata,
        is_excp -> mtvec)))

    shadow_pc.io.clock   := clock
    shadow_pc.io.reset   := reset
    shadow_pc.io.pc_4    := fire && pc_hit && !lsu_trap
    shadow_pc.io.pc_we   := jump
    shadow_pc.io.pc_next := next_pc


    flush      := (valid && !pc_hit) || lsu_trap
    redirectPC := Mux(lsu_trap, mtvec, shadow_pc.io.pc)

    val is_redirect = taken || is_mret || is_excp || lsu_trap
    dnpc := Mux(is_redirect, next_pc, pc + 4.U)
}

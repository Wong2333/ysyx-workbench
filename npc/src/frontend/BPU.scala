package npc

import chisel3._
import chisel3.util._

class BPUTrainBundle extends Bundle{
    val valid  = Bool()
    val taken  = Bool()
    val pc     = UInt(32.W)
    val target = UInt(32.W)
    val link   = Bool()
    val jalr   = Bool()
    val ret    = Bool()
}

class BPUPredictBundle extends Bundle{
    val valid   = Bool()
    val take    = Bool()
    val next_pc = UInt(32.W)
}

class BTBEntry extends Bundle{
    val tag     = UInt(8.W)
    val target  = UInt(10.W)
    val counter = UInt(2.W)
    val valid   = Bool()
    val ret     = Bool()
}

class ysyx_26030092_BPU extends Module{
    val train = IO(Input(new BPUTrainBundle))
    val pc    = IO(Input(UInt(32.W)))
    val out   = IO(Output(new BPUPredictBundle))

    val NWAYS     = 2
    val RAS_DEPTH = 2

    val btb = Reg(Vec(NWAYS, new BTBEntry))
    val rr  = RegInit(false.B)

    val ras    = Reg(Vec(RAS_DEPTH, UInt(30.W)))
    val ras_sp = RegInit(0.U(log2Ceil(RAS_DEPTH + 1).W))

    val tag      = pc(9,2)
    val hit      = (0 until NWAYS).map(i => btb(i).valid && btb(i).tag === tag)
    val hit_any  = hit.reduce(_ || _)
    val hit_ret  = Mux1H(hit, (0 until NWAYS).map(i => btb(i).ret))
    val hit_take = Mux1H(hit, (0 until NWAYS).map(i => btb(i).counter(1)))
    val ras_ok   = ras_sp =/= 0.U

    out.valid   := hit_any
    out.take    := hit_take || hit_ret
    out.next_pc := Mux(hit_ret && ras_ok,
                       Cat(ras(ras_sp - 1.U), 0.U(2.W)),
                       Cat(pc(31,12), Mux1H(hit, (0 until NWAYS).map(i => btb(i).target)), 0.U(2.W)))

    val ttag     = train.pc(9,2)
    val thit     = (0 until NWAYS).map(i => btb(i).valid && btb(i).tag === ttag)
    val thit_any = thit.reduce(_ || _) && train.valid

    val learn   = train.valid && (!train.jalr || train.ret)
    val victim  = Mux(!btb(0).valid, 0.U, Mux(!btb(1).valid, 1.U, rr))

    val wr_en   = learn && (train.taken || thit_any)
    val wr_way  = Mux(thit_any, PriorityEncoder(thit), victim)

    when(wr_en){
        val e = btb(wr_way)
        e.valid := true.B
        e.tag   := ttag
        e.ret   := train.ret
        when(train.taken){
            e.target := train.target(11,2)
            when(e.counter === 3.U){ e.counter := 3.U }
            .otherwise             { e.counter := e.counter + 1.U }
        }.otherwise{
            when(e.counter === 0.U){ e.counter := 0.U }
            .otherwise             { e.counter := e.counter - 1.U }
        }
    }

    when(wr_en && !thit_any){ rr := !rr }

    val push     = train.valid && train.link && !train.ret
    val pop      = train.valid && train.ret
    val ret_addr = train.pc + 4.U
    val push_idx = Mux(ras_sp === RAS_DEPTH.U, (RAS_DEPTH - 1).U, ras_sp)

    when(push){
        ras(push_idx) := ret_addr(31,2)
        when(ras_sp =/= RAS_DEPTH.U){ ras_sp := ras_sp + 1.U }
    }
    when(pop && ras_ok){ ras_sp := ras_sp - 1.U }

    when(reset.asBool){
        btb.foreach(_.valid := false.B)
    }
}

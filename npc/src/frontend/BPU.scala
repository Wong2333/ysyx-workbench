package npc

import chisel3._
import chisel3.util._

class BPUTrainBundle extends Bundle{
    val valid  = Bool()
    val taken  = Bool()
    val pc     = UInt(32.W)
    val target = UInt(32.W)
}

class BPUPredictBundle extends Bundle{
    val valid   = Bool()
    val take    = Bool()
    val next_pc = UInt(32.W)
}

class BTBEntry extends Bundle{
    val tag     = UInt(6.W)
    val target  = UInt(10.W)
    val counter = UInt(2.W)
    val valid   = Bool()
}

class ysyx_26030092_BPU extends Module{
    val train = IO(Input(new BPUTrainBundle))
    val pc    = IO(Input(UInt(32.W)))
    val out   = IO(Output(new BPUPredictBundle))

    val NWAYS = 2
    val NSETS = 2

    val btb = Reg(Vec(NSETS * NWAYS, new BTBEntry))
    val rr  = RegInit(false.B)

    val idx = pc(3)
    val tag = pc(9,4)

    val e0 = btb(Cat(idx, 0.U(1.W)))
    val e1 = btb(Cat(idx, 1.U(1.W)))
    val hit0 = e0.valid && e0.tag === tag
    val hit1 = e1.valid && e1.tag === tag

    out.valid   := hit0 || hit1
    out.take    := Mux(hit0, e0.counter(1), Mux(hit1, e1.counter(1), false.B))
    out.next_pc := Cat(pc(31,12), Mux(hit0, e0.target, Mux(hit1, e1.target, 0.U(10.W))), 0.U(2.W))

    val tidx = train.pc(3)
    val ttag = train.pc(9,4)

    val t0 = btb(Cat(tidx, 0.U(1.W)))
    val t1 = btb(Cat(tidx, 1.U(1.W)))
    val thit0 = t0.valid && t0.tag === ttag
    val thit1 = t1.valid && t1.tag === ttag
    val thit_any = (thit0 || thit1) && train.valid

    val victim  = Mux(!t0.valid, 0.U, Mux(!t1.valid, 1.U, rr))
    val wr_way  = Mux(thit_any, Mux(thit0, 0.U, 1.U), victim)

    val wr_en  = train.valid && (train.taken || thit_any)

    when(wr_en){
        val e = btb(Cat(tidx, wr_way))
        e.valid := true.B
        e.tag   := ttag
        when(train.taken){
            e.target := train.target(11,2)
            when(e.counter === 3.U){ e.counter := 3.U }
            .otherwise             { e.counter := e.counter + 1.U }
        }.otherwise{
            when(e.counter === 0.U){ e.counter := 0.U }
            .otherwise             { e.counter := e.counter - 1.U }
        }
        when(!thit_any){ e.counter := 2.U }
    }

    when(wr_en && !thit_any){ rr := !rr }

    when(reset.asBool){
        btb.foreach(_.valid := false.B)
    }
}

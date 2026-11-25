package npc

import chisel3._
import chisel3.util._

class BPUTrainBundle extends Bundle{
    val valid  = Bool()
    val taken  = Bool()
    val pc     = UInt(32.W)
    val target = UInt(32.W)
    val jal    = Bool()
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
}

class ysyx_26030092_BPU extends Module{
    val train = IO(Input(new BPUTrainBundle))
    val pc    = IO(Input(UInt(32.W)))
    val out   = IO(Output(new BPUPredictBundle))

    val NWAYS = 3
    val btb = Reg(Vec(NWAYS, new BTBEntry))
    val rr  = RegInit(0.U(log2Ceil(NWAYS).W))

    val tag = pc(9,2)
    val hit = (0 until NWAYS).map(i => btb(i).valid && btb(i).tag === tag)

    out.valid   := hit.reduce(_ || _)
    out.take    := Mux1H(hit, (0 until NWAYS).map(i => btb(i).counter(1)))
    out.next_pc := Cat(pc(31,12), Mux1H(hit, (0 until NWAYS).map(i => btb(i).target)), 0.U(2.W))

    val ttag = train.pc(9,2)
    val thit = (0 until NWAYS).map(i => btb(i).valid && btb(i).tag === ttag)
    val thit_any = thit.reduce(_ || _) && train.valid

    val victim = MuxCase(rr, (0 until NWAYS).map(i => (!btb(i).valid) -> i.U))

    val wr_en  = train.valid && (train.taken || thit_any)
    val wr_way = Mux(thit_any, PriorityEncoder(thit), victim)

    when(wr_en){
        val e = btb(wr_way)
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
    }

    when(wr_en && !thit_any){
        rr := Mux(rr === (NWAYS - 1).U, 0.U, rr + 1.U)
    }

    when(reset.asBool){
        btb.foreach(_.valid := false.B)
    }
}

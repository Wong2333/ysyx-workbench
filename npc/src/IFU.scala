package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._
import npc.icache.ysyx_26030092_icacheMainPipe_reg

class instEntry extends Bundle{
    val inst = UInt(32.W)
    val pc = UInt(32.W)
    val excp_code = UInt(32.W)
    val is_excp = Bool()
}

class ysyx_26030092_IFU extends Module{

    val out = IO(Decoupled(new instEntry))

    val RedirectPC = IO(Input(UInt(32.W)))
    val flush_icache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    val AXI4_port = IO(new AXI4Bundle)

    val PC_reg = Module(new ysyx_26030092_PCReg)

    val inst_queue = ysyxPrefix { Module(new Queue(new instEntry, 1, hasFlush = true)) }
    inst_queue.io.flush.get := flush
    
    val icache_main_pipe = Module(new ysyx_26030092_icacheMainPipe_reg)
    icache_main_pipe.AXI4_port <> AXI4_port
    icache_main_pipe.icache_flush := flush_icache
    icache_main_pipe.req.valid := true.B
    icache_main_pipe.req.addr := PC_reg.pc
    icache_main_pipe.flush := flush

    inst_queue.io.enq <> icache_main_pipe.resp

    out <> inst_queue.io.deq

    val icahce_main_pipe_fire = icache_main_pipe.req.ready & icache_main_pipe.req.valid
    PC_reg.adv := icahce_main_pipe_fire
    PC_reg.jump := false.B
    PC_reg.next_pc := 0.U

    when(flush){                       // flush 优先级最高，放最后覆盖
        icache_main_pipe.req.valid := false.B
        PC_reg.jump := true.B
        PC_reg.next_pc := RedirectPC
        out.valid := false.B
    }

    PerfCounter("IFU_get_inst_cycles") := ~flush
    PerfCounter("IFU_wait_resp")    := icache_main_pipe.req.ready && ~icache_main_pipe.resp.valid && ~flush
    PerfCounter("IFU_fetch")        := out.fire
}
